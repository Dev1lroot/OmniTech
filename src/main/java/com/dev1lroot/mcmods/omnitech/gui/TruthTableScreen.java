package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.network.AssembleTruthTablePacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Truth Table editor — opened by right-clicking the Truth Table item.
 *
 * <p>Layout: 4 rows × 3 columns (A | B | OUT). All 12 cells are toggle
 * buttons. Row order is free — the server canonicalises by input pair before
 * matching against the 12 known gate patterns.
 *
 * <p>Rendering note: all custom drawing happens in {@link #extractBackground}
 * so that buttons (rendered by super.extractRenderState) appear on top.
 */
public class TruthTableScreen extends Screen {

    // ── Panel geometry ────────────────────────────────────────────────────────
    private static final int GUI_W    = 230;
    private static final int GUI_H    = 228;
    private static final int CELL_W   = 20;
    private static final int CELL_H   = 18;
    private static final int COL_STEP = 28;
    private static final int ROW_STEP = 24;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG_OUTER  = 0xFF0D1117;
    private static final int C_BG_INNER  = 0xFF161B22;
    private static final int C_BORDER    = 0xFF30363D;
    private static final int C_TITLE     = 0xFF58A6FF;
    private static final int C_HEADER_A  = 0xFF7EE787;
    private static final int C_HEADER_B  = 0xFF79C0FF;
    private static final int C_HEADER_O  = 0xFFFF7B72;
    private static final int C_ROW_LABEL = 0xFF6E7681;
    private static final int C_HINT      = 0xFF8B949E;
    private static final int C_CELL_HIGH = 0xFF238636;

    // ── State ─────────────────────────────────────────────────────────────────
    private final InteractionHand hand;
    private final int initialBits;
    // cells[row][col]: col 0=A, col 1=B, col 2=Out
    private final boolean[][] cells = new boolean[4][3];
    private Button[][] toggleButtons;
    private boolean assembled = false;

    public TruthTableScreen(InteractionHand hand, int bits) {
        super(Component.translatable("gui.omnitech.truth_table"));
        this.hand = hand;
        this.initialBits = bits;
        decodeBits(bits);
    }

    // ── Bit packing ───────────────────────────────────────────────────────────

    private void decodeBits(int bits) {
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 3; c++)
                cells[r][c] = ((bits >> (r * 3 + c)) & 1) == 1;
    }

    private int encodeBits() {
        int bits = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 3; c++)
                if (cells[r][c]) bits |= (1 << (r * 3 + c));
        return bits;
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private int panelLeft() { return (width  - GUI_W) / 2; }
    private int panelTop()  { return (height - GUI_H) / 2; }
    private int tableLeft() { return panelLeft() + 89; }
    private int tableTop()  { return panelTop()  + 64; }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int tl = tableLeft();
        int tt = tableTop();

        toggleButtons = new Button[4][3];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                final int row = r, col = c;
                int bx = tl + c * COL_STEP;
                int by = tt + r * ROW_STEP;
                toggleButtons[r][c] = Button.builder(
                        cellLabel(cells[r][c]),
                        btn -> {
                            cells[row][col] = !cells[row][col];
                            btn.setMessage(cellLabel(cells[row][col]));
                        })
                        .bounds(bx, by, CELL_W, CELL_H)
                        .build();
                addRenderableWidget(toggleButtons[r][c]);
            }
        }

        int left = panelLeft();
        addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.truth_table.assemble"),
                btn -> assemble())
                .bounds(left + (GUI_W - 110) / 2, panelTop() + GUI_H - 32, 110, 20)
                .build());
    }

    private Component cellLabel(boolean value) {
        return Component.literal(value ? "1" : "0");
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void assemble() {
        assembled = true;
        ClientPacketDistributor.sendToServer(new AssembleTruthTablePacket(encodeBits(), true));
        onClose();
    }

    @Override
    public void onClose() {
        if (!assembled) {
            ClientPacketDistributor.sendToServer(new AssembleTruthTablePacket(encodeBits(), false));
        }
        super.onClose();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    //
    // All drawing goes in extractBackground. Screen.extractRenderState (not
    // overridden) iterates renderables and draws all buttons on top.

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        int left = panelLeft();
        int top  = panelTop();
        int tl   = tableLeft();
        int tt   = tableTop();

        // ── Panel ─────────────────────────────────────────────────────────────
        g.fill(left - 1, top - 1, left + GUI_W + 1, top + GUI_H + 1, C_BORDER);
        g.fill(left, top, left + GUI_W, top + GUI_H, C_BG_OUTER);
        g.fill(left + 4, top + 4, left + GUI_W - 4, top + GUI_H - 4, C_BG_INNER);
        g.fill(left + 4, top + 22, left + GUI_W - 4, top + 23, C_BORDER);

        // ── Title ─────────────────────────────────────────────────────────────
        String titleStr = title.getString();
        g.text(font, titleStr, left + GUI_W / 2 - font.width(titleStr) / 2, top + 8, C_TITLE, false);

        // ── Hint lines ────────────────────────────────────────────────────────
        String h1 = Component.translatable("gui.omnitech.truth_table.hint1").getString();
        String h2 = Component.translatable("gui.omnitech.truth_table.hint2").getString();
        g.text(font, h1, left + GUI_W / 2 - font.width(h1) / 2, top + 28, C_HINT, false);
        g.text(font, h2, left + GUI_W / 2 - font.width(h2) / 2, top + 40, C_HINT, false);

        // ── Column headers ────────────────────────────────────────────────────
        int headerY = tt - 12;
        g.text(font, "#",   tl - 14 - font.width("#"),                            headerY, C_ROW_LABEL, false);
        g.text(font, "A",   tl + 0 * COL_STEP + CELL_W / 2 - font.width("A")   / 2, headerY, C_HEADER_A, false);
        g.text(font, "B",   tl + 1 * COL_STEP + CELL_W / 2 - font.width("B")   / 2, headerY, C_HEADER_B, false);
        g.text(font, "OUT", tl + 2 * COL_STEP + CELL_W / 2 - font.width("OUT") / 2, headerY, C_HEADER_O, false);
        g.fill(tl - 20, headerY + 10, tl + 2 * COL_STEP + CELL_W + 4, headerY + 11, C_BORDER);

        // ── Row numbers ───────────────────────────────────────────────────────
        for (int r = 0; r < 4; r++) {
            String num = String.valueOf(r + 1);
            int rowMidY = tt + r * ROW_STEP + CELL_H / 2 - 4;
            g.text(font, num, tl - 14 - font.width(num), rowMidY, C_ROW_LABEL, false);
        }

        // ── Green highlight behind OUT=1 cells ────────────────────────────────
        for (int r = 0; r < 4; r++) {
            if (cells[r][2]) {
                int bx = tl + 2 * COL_STEP;
                int by = tt + r * ROW_STEP;
                g.fill(bx - 1, by - 1, bx + CELL_W + 1, by + CELL_H + 1, C_CELL_HIGH);
            }
        }

        // ── Legend ────────────────────────────────────────────────────────────
        int legendY = tt + 4 * ROW_STEP + 8;
        g.fill(left + 8, legendY - 2, left + GUI_W - 8, legendY - 1, C_BORDER);
        String l1 = Component.translatable("gui.omnitech.truth_table.legend1").getString();
        String l2 = Component.translatable("gui.omnitech.truth_table.legend2").getString();
        g.text(font, l1, left + GUI_W / 2 - font.width(l1) / 2, legendY + 2,  C_HINT, false);
        g.text(font, l2, left + GUI_W / 2 - font.width(l2) / 2, legendY + 13, C_HINT, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
