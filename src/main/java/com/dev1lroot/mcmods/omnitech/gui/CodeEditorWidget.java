package com.dev1lroot.mcmods.omnitech.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone scrollable code-editor widget.
 * Call render() from extractBackground (absolute coords).
 * Forward keyPressed / charTyped / mouseClicked / mouseScrolled from the Screen.
 */
public class CodeEditorWidget {

    public static final int MAX_PROGRAM_LEN = 1_048_576;

    private static final int GUTTER_W    = 26;
    private static final int SCROLLBAR_W = 6;
    private static final int LINE_H      = 9;
    private static final int PADDING     = 2;

    private final List<String> lines = new ArrayList<>();

    private int cursorLine = 0;
    private int cursorCol  = 0;
    private int selAnchorLine = -1;
    private int selAnchorCol  = 0;

    private int scrollLine = 0;

    private boolean readOnly      = false;
    private int     highlightLine = -1;

    private long cursorBlinkEpoch = 0;

    public CodeEditorWidget() {
        lines.add("");
    }

    // ── Content ───────────────────────────────────────────────────────────────

    public void setText(String text) {
        if (text == null) text = "";
        lines.clear();
        String[] split = text.split("\n", -1);
        for (String s : split) lines.add(s);
        if (lines.isEmpty()) lines.add("");
        cursorLine = 0;
        cursorCol  = 0;
        selAnchorLine = -1;
        scrollLine = 0;
    }

    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    public void setReadOnly(boolean v) { this.readOnly = v; }

    /** 0-based line to highlight with a green tint (for debug execution pointer). */
    public void setHighlightLine(int line) { this.highlightLine = line; }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Renders the editor inside the given absolute screen rect (x,y,w,h).
     * Must be called from extractBackground (no pose translation active).
     */
    public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
        int vis = visibleLines(h);
        clampScroll(vis);

        // Outer border + black background
        g.fill(x, y, x + w, y + h, 0xFF222222);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);

        // Gutter background
        g.fill(x + 1, y + 1, x + GUTTER_W, y + h - 1, 0xFF0A0A0A);
        g.fill(x + GUTTER_W, y + 1, x + GUTTER_W + 1, y + h - 1, 0xFF333333);

        int contentX = x + GUTTER_W + 1 + PADDING;
        int contentRight = x + w - SCROLLBAR_W - 2;

        // Clip text area
        g.enableScissor(x + GUTTER_W + 1, y + 1, contentRight, y + h - 1);

        for (int i = 0; i < vis; i++) {
            int li = scrollLine + i;
            if (li >= lines.size()) break;
            int lineY = y + 1 + i * LINE_H;

            if (li == highlightLine)
                g.fill(x + GUTTER_W + 1, lineY, contentRight, lineY + LINE_H, 0x8800AA00);

            if (hasSelection())
                drawSelectionOnLine(g, font, contentX, lineY, li);

            g.text(font, lines.get(li), contentX, lineY, 0xFFFFFFFF, false);
        }

        g.disableScissor();

        // Gutter line numbers
        g.enableScissor(x + 1, y + 1, x + GUTTER_W, y + h - 1);
        for (int i = 0; i < vis; i++) {
            int li = scrollLine + i;
            if (li >= lines.size()) break;
            int lineY = y + 1 + i * LINE_H;
            int numColor = (li == highlightLine) ? 0xFF00FF44 : 0xFF666666;
            String num = String.valueOf(li + 1);
            g.text(font, num, x + GUTTER_W - PADDING - 1 - font.width(num), lineY, numColor, false);
        }
        g.disableScissor();

        // Cursor blink
        if (!readOnly) {
            boolean show = ((System.currentTimeMillis() - cursorBlinkEpoch) % 1000) < 500;
            if (show && cursorLine >= scrollLine && cursorLine < scrollLine + vis) {
                int lineY = y + 1 + (cursorLine - scrollLine) * LINE_H;
                String pre = sub(lines.get(cursorLine), 0, cursorCol);
                g.fill(contentX + font.width(pre), lineY, contentX + font.width(pre) + 1, lineY + LINE_H, 0xFFCCCCCC);
            }
        }

        // Scrollbar
        drawScrollbar(g, x + w - SCROLLBAR_W - 1, y + 1, SCROLLBAR_W, h - 2, vis);
    }

    private void drawSelectionOnLine(GuiGraphicsExtractor g, Font font, int cx, int lineY, int li) {
        int[] sel = normalizedSel();
        int sl = sel[0], sc = sel[1], el = sel[2], ec = sel[3];
        if (li < sl || li > el) return;
        if (sl == el && sc == ec) return;

        String line = lines.get(li);
        int s, e;
        boolean fullToEnd = false;
        if (li == sl && li == el) {
            s = Math.min(sc, line.length());
            e = Math.min(ec, line.length());
        } else if (li == sl) {
            s = Math.min(sc, line.length());
            e = line.length();
            fullToEnd = true;
        } else if (li == el) {
            s = 0;
            e = Math.min(ec, line.length());
        } else {
            s = 0;
            e = line.length();
            fullToEnd = true;
        }
        if (s >= e && !fullToEnd) return;
        int x0 = cx + font.width(sub(line, 0, s));
        int x1 = cx + font.width(sub(line, 0, e));
        if (fullToEnd) x1 = Math.max(x1, x0 + 4);
        if (x1 > x0) g.fill(x0, lineY, x1, lineY + LINE_H, 0xA0006080);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int w, int h, int vis) {
        g.fill(x, y, x + w, y + h, 0xFF111111);
        if (lines.size() <= vis) return;
        int barH = Math.max(6, (int)((float)h * vis / lines.size()));
        int barY = y + (int)((float)(h - barH) * scrollLine / Math.max(1, lines.size() - vis));
        g.fill(x + 1, barY, x + w - 1, barY + barH, 0xFF555555);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    public boolean keyPressed(KeyEvent event, int h) {
        if (readOnly) return false;
        resetBlink();

        if (event.isSelectAll()) { selectAll(); return true; }
        if (event.isCopy())      { copySelection(); return true; }
        if (event.isCut())       { copySelection(); deleteSelection(); scrollToCursor(h); return true; }
        if (event.isPaste())     { paste(); scrollToCursor(h); return true; }

        boolean shift = event.hasShiftDown();
        return switch (event.key()) {
            case 263 -> { moveLeft(shift);  scrollToCursor(h); yield true; }
            case 262 -> { moveRight(shift); scrollToCursor(h); yield true; }
            case 265 -> { moveUp(shift);    scrollToCursor(h); yield true; }
            case 264 -> { moveDown(shift);  scrollToCursor(h); yield true; }
            case 268 -> { moveHome(shift);  scrollToCursor(h); yield true; }
            case 269 -> { moveEnd(shift);   scrollToCursor(h); yield true; }
            case 266 -> { scrollLine = Math.max(0, scrollLine - visibleLines(h)); yield true; }
            case 267 -> { scrollLine = Math.min(scrollLine + visibleLines(h), Math.max(0, lines.size() - visibleLines(h))); yield true; }
            case 259 -> { backspace();    scrollToCursor(h); yield true; }
            case 261 -> { deleteKey();    scrollToCursor(h); yield true; }
            case 257, 335 -> { insertNewline(); scrollToCursor(h); yield true; }
            default  -> false;
        };
    }

    public boolean charTyped(char c, int h) {
        if (readOnly) return false;
        if (c == '\t') { insertText("  ", h); return true; }
        if (Character.isISOControl(c)) return false;
        insertText(String.valueOf(c), h);
        return true;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    /** ax/ay are absolute screen coords; x/y/h are the editor's absolute rect. */
    public boolean mouseClicked(double ax, double ay, int x, int y, int h) {
        int contentX = x + GUTTER_W + 1 + PADDING;
        int row = Math.max(0, Math.min((int)((ay - y - 1) / LINE_H) + scrollLine, lines.size() - 1));
        int col = charAtPixel(font(), lines.get(row), (int)(ax - contentX));
        cursorLine = row;
        cursorCol  = col;
        selAnchorLine = -1;
        resetBlink();
        return true;
    }

    public boolean mouseScrolled(double delta, int h) {
        int step = (int)Math.signum(delta) * -3;
        scrollLine = Math.max(0, Math.min(scrollLine + step, Math.max(0, lines.size() - visibleLines(h))));
        return true;
    }

    // ── Scroll helpers ────────────────────────────────────────────────────────

    public void scrollToCursor(int h) {
        int vis = visibleLines(h);
        if (cursorLine < scrollLine) scrollLine = cursorLine;
        else if (cursorLine >= scrollLine + vis) scrollLine = cursorLine - vis + 1;
        clampScroll(vis);
    }

    public void scrollToLine(int target, int h) {
        int vis = visibleLines(h);
        if (target < scrollLine || target >= scrollLine + vis)
            scrollLine = Math.max(0, target - vis / 2);
        clampScroll(vis);
    }

    private void clampScroll(int vis) {
        scrollLine = Math.max(0, Math.min(scrollLine, Math.max(0, lines.size() - vis)));
    }

    private int visibleLines(int h) { return Math.max(1, h / LINE_H); }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void moveLeft(boolean shift) {
        if (shift) { if (!hasSelection()) startSelection(); }
        else if (hasSelection()) { moveCursorToSelStart(); clearSelection(); return; }
        if (cursorCol > 0) cursorCol--;
        else if (cursorLine > 0) { cursorLine--; cursorCol = lines.get(cursorLine).length(); }
        if (!shift) clearSelection();
    }

    private void moveRight(boolean shift) {
        if (shift) { if (!hasSelection()) startSelection(); }
        else if (hasSelection()) { moveCursorToSelEnd(); clearSelection(); return; }
        String line = lines.get(cursorLine);
        if (cursorCol < line.length()) cursorCol++;
        else if (cursorLine < lines.size() - 1) { cursorLine++; cursorCol = 0; }
        if (!shift) clearSelection();
    }

    private void moveUp(boolean shift) {
        if (shift) { if (!hasSelection()) startSelection(); } else clearSelection();
        if (cursorLine > 0) { cursorLine--; cursorCol = Math.min(cursorCol, lines.get(cursorLine).length()); }
    }

    private void moveDown(boolean shift) {
        if (shift) { if (!hasSelection()) startSelection(); } else clearSelection();
        if (cursorLine < lines.size() - 1) { cursorLine++; cursorCol = Math.min(cursorCol, lines.get(cursorLine).length()); }
    }

    private void moveHome(boolean shift) {
        if (shift) { if (!hasSelection()) startSelection(); } else clearSelection();
        cursorCol = 0;
    }

    private void moveEnd(boolean shift) {
        if (shift) { if (!hasSelection()) startSelection(); } else clearSelection();
        cursorCol = lines.get(cursorLine).length();
    }

    private void selectAll() {
        selAnchorLine = 0; selAnchorCol = 0;
        cursorLine = lines.size() - 1;
        cursorCol  = lines.get(cursorLine).length();
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    private void insertText(String text, int h) {
        if (hasSelection()) deleteSelection();
        if (totalLen() + text.length() > MAX_PROGRAM_LEN) return;

        String[] parts = text.split("\n", -1);
        String cur    = lines.get(cursorLine);
        int sc        = Math.min(cursorCol, cur.length());
        String before = cur.substring(0, sc);
        String after  = cur.substring(sc);

        if (parts.length == 1) {
            lines.set(cursorLine, before + parts[0] + after);
            cursorCol = sc + parts[0].length();
        } else {
            lines.set(cursorLine, before + parts[0]);
            for (int i = 1; i < parts.length - 1; i++)
                lines.add(cursorLine + i, parts[i]);
            lines.add(cursorLine + parts.length - 1, parts[parts.length - 1] + after);
            cursorLine += parts.length - 1;
            cursorCol  = parts[parts.length - 1].length();
        }
        scrollToCursor(h);
        resetBlink();
    }

    private void insertNewline() {
        if (hasSelection()) deleteSelection();
        String cur  = lines.get(cursorLine);
        int sc      = Math.min(cursorCol, cur.length());
        lines.set(cursorLine, cur.substring(0, sc));
        lines.add(cursorLine + 1, cur.substring(sc));
        cursorLine++; cursorCol = 0;
        resetBlink();
    }

    private void backspace() {
        if (hasSelection()) { deleteSelection(); return; }
        if (cursorCol > 0) {
            String l = lines.get(cursorLine);
            lines.set(cursorLine, l.substring(0, cursorCol - 1) + l.substring(cursorCol));
            cursorCol--;
        } else if (cursorLine > 0) {
            String prev = lines.get(cursorLine - 1);
            cursorCol = prev.length();
            lines.set(cursorLine - 1, prev + lines.get(cursorLine));
            lines.remove(cursorLine--);
        }
    }

    private void deleteKey() {
        if (hasSelection()) { deleteSelection(); return; }
        String l = lines.get(cursorLine);
        if (cursorCol < l.length()) {
            lines.set(cursorLine, l.substring(0, cursorCol) + l.substring(cursorCol + 1));
        } else if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, l + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
        }
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int[] sel = normalizedSel();
        int sl = sel[0], sc = Math.min(sel[1], lines.get(sel[0]).length());
        int el = sel[2], ec = Math.min(sel[3], lines.get(sel[2]).length());
        String merged = lines.get(sl).substring(0, sc) + lines.get(el).substring(ec);
        for (int i = el; i > sl; i--) lines.remove(i);
        lines.set(sl, merged);
        if (lines.isEmpty()) lines.add("");
        cursorLine = sl; cursorCol = sc;
        clearSelection();
    }

    private void copySelection() {
        if (!hasSelection()) return;
        int[] sel = normalizedSel();
        int sl = sel[0], sc = Math.min(sel[1], lines.get(sel[0]).length());
        int el = sel[2], ec = Math.min(sel[3], lines.get(sel[2]).length());
        StringBuilder sb = new StringBuilder();
        if (sl == el) {
            sb.append(lines.get(sl), sc, ec);
        } else {
            sb.append(lines.get(sl).substring(sc)).append('\n');
            for (int i = sl + 1; i < el; i++) sb.append(lines.get(i)).append('\n');
            sb.append(lines.get(el), 0, ec);
        }
        TextFieldHelper.setClipboardContents(Minecraft.getInstance(), sb.toString());
    }

    private void paste() {
        String text = TextFieldHelper.getClipboardContents(Minecraft.getInstance());
        if (text != null && !text.isEmpty()) insertText(text, 0);
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    private boolean hasSelection() { return selAnchorLine >= 0; }
    private void startSelection()  { selAnchorLine = cursorLine; selAnchorCol = cursorCol; }
    private void clearSelection()  { selAnchorLine = -1; }

    private void moveCursorToSelStart() {
        int[] s = normalizedSel();
        cursorLine = s[0]; cursorCol = s[1];
    }

    private void moveCursorToSelEnd() {
        int[] s = normalizedSel();
        cursorLine = s[2]; cursorCol = s[3];
    }

    /** Returns [startLine, startCol, endLine, endCol] in document order. */
    private int[] normalizedSel() {
        if (selAnchorLine < cursorLine || (selAnchorLine == cursorLine && selAnchorCol <= cursorCol))
            return new int[]{ selAnchorLine, selAnchorCol, cursorLine, cursorCol };
        return new int[]{ cursorLine, cursorCol, selAnchorLine, selAnchorCol };
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private int totalLen() {
        int n = Math.max(0, lines.size() - 1);
        for (String l : lines) n += l.length();
        return n;
    }

    private void resetBlink() { cursorBlinkEpoch = System.currentTimeMillis(); }

    private Font font() { return Minecraft.getInstance().font; }

    private int charAtPixel(Font font, String line, int px) {
        if (px <= 0) return 0;
        for (int i = 1; i <= line.length(); i++)
            if (font.width(line.substring(0, i)) > px) return i - 1;
        return line.length();
    }

    private String sub(String s, int a, int b) {
        a = Math.max(0, Math.min(a, s.length()));
        b = Math.max(a, Math.min(b, s.length()));
        return s.substring(a, b);
    }
}
