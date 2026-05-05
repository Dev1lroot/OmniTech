package com.dev1lroot.mcmods.omnitech.gui.guidebook;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen guidebook viewer.
 *
 * <h3>Navigation</h3>
 * <ul>
 *   <li>"&lt;" / "&gt;" buttons on bottom-left / bottom-right</li>
 *   <li>Left/Right arrow keys or Page Up/Down</li>
 *   <li>Mouse wheel: scroll within the current page</li>
 *   <li>Escape: close</li>
 * </ul>
 *
 * <h3>Links</h3>
 * Clicking a {@link GuidebookPage.PageToken.LinkBlock} either opens the URL in the
 * system browser or jumps to another page in the book.
 */
public class GuidebookScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int GUI_W      = 256;
    private static final int GUI_H      = 196;
    private static final int BORDER     = 4;
    private static final int HEADER_H   = 16;
    private static final int FOOTER_H   = 22;
    private static final int PAD_X      = 12;
    private static final int SCROLL_STEP = 12;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_OUTER      = 0xFF3D2B1F;
    private static final int C_PAGE       = 0xFFF5E6C8;
    private static final int C_ACCENT     = 0xFFE5D298;
    private static final int C_SEPARATOR  = 0xFFB8A070;
    private static final int C_HEADING    = 0xFF2A1505;
    private static final int C_SUBHEADING = 0xFF5C3A1E;
    private static final int C_BODY       = 0xFF3D2B1F;
    private static final int C_META       = 0xFF8B6942;
    private static final int C_LINK       = 0xFF1A6FB5;
    private static final int C_LINK_HOVER = 0xFF2CA0F0;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<GuidebookPage> pages;
    private int currentPage   = 0;
    private int scrollOffset  = 0;
    private int totalContentHeight = 0;

    /** Hit regions for clickable links, rebuilt each frame. */
    private final List<RenderedLink> renderedLinks = new ArrayList<>();

    /** Layout values — recalculated in init(). */
    private int guiLeft, guiTop;
    private int contentLeft, contentTop, contentBottom, contentWidth;

    private record RenderedLink(int x, int y, int w, int h, GuidebookPage.LinkTarget target) {}

    public GuidebookScreen(List<GuidebookPage> pages) {
        super(Component.translatable("item.omnitech.guidebook"));
        this.pages = pages;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        guiLeft = (width  - GUI_W) / 2;
        guiTop  = (height - GUI_H) / 2;

        contentLeft   = guiLeft + PAD_X;
        contentTop    = guiTop + BORDER + HEADER_H + 2;
        contentBottom = guiTop + GUI_H - BORDER - FOOTER_H - 2;
        contentWidth  = GUI_W - PAD_X * 2;

        int btnY = guiTop + GUI_H - BORDER - FOOTER_H + 4;

        addRenderableWidget(
            Button.builder(Component.literal("<"), b -> goToPrev())
                .bounds(guiLeft + 6, btnY, 20, 14).build());

        addRenderableWidget(
            Button.builder(Component.literal(">"), b -> goToNext())
                .bounds(guiLeft + GUI_W - 26, btnY, 20, 14).build());
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, C_OUTER);
        g.fill(guiLeft + BORDER, guiTop + BORDER,
               guiLeft + GUI_W - BORDER, guiTop + GUI_H - BORDER, C_PAGE);
        // Header
        g.fill(guiLeft + BORDER, guiTop + BORDER,
               guiLeft + GUI_W - BORDER, guiTop + BORDER + HEADER_H, C_ACCENT);
        g.fill(guiLeft + BORDER, guiTop + BORDER + HEADER_H,
               guiLeft + GUI_W - BORDER, guiTop + BORDER + HEADER_H + 1, C_SEPARATOR);
        // Footer
        int footerTop = guiTop + GUI_H - BORDER - FOOTER_H;
        g.fill(guiLeft + BORDER, footerTop - 1,
               guiLeft + GUI_W - BORDER, footerTop, C_SEPARATOR);
        g.fill(guiLeft + BORDER, footerTop,
               guiLeft + GUI_W - BORDER, guiTop + GUI_H - BORDER, C_ACCENT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        if (pages.isEmpty()) {
            String msg = "No guidebook content found.";
            g.text(font, msg, guiLeft + GUI_W / 2 - font.width(msg) / 2, guiTop + GUI_H / 2 - 4, C_META, false);
            return;
        }

        GuidebookPage page = pages.get(currentPage);

        // Header: chapter name, no shadow
        String chapter = page.chapterName();
        g.text(font, chapter, guiLeft + GUI_W / 2 - font.width(chapter) / 2,
               guiTop + BORDER + 4, C_META, false);

        // Footer: page counter, no shadow
        String num = (currentPage + 1) + " / " + pages.size();
        g.text(font, num, guiLeft + GUI_W / 2 - font.width(num) / 2,
               guiTop + GUI_H - BORDER - FOOTER_H + 7, C_META, false);

        // Content (scissored)
        renderedLinks.clear();
        g.enableScissor(contentLeft, contentTop, contentLeft + contentWidth, contentBottom);
        totalContentHeight = renderContent(g, page, mx, my);
        g.disableScissor();
    }

    private int renderContent(GuiGraphicsExtractor g, GuidebookPage page, int mx, int my) {
        int y = contentTop - scrollOffset;
        for (GuidebookPage.PageToken token : page.tokens())
            y = renderToken(g, token, y, mx, my);
        return (y + scrollOffset) - contentTop;
    }

    private int renderToken(GuiGraphicsExtractor g, GuidebookPage.PageToken token, int y, int mx, int my) {
        return switch (token) {

            case GuidebookPage.PageToken.Heading h -> {
                g.text(font, h.text(), contentLeft, y, C_HEADING, false);
                yield y + font.lineHeight + 3;
            }

            case GuidebookPage.PageToken.SubHeading sh -> {
                g.text(font, sh.text(), contentLeft, y, C_SUBHEADING, false);
                yield y + font.lineHeight + 2;
            }

            case GuidebookPage.PageToken.Body b -> {
                List<FormattedCharSequence> lines = font.split(b.content(), contentWidth);
                int lineY = y;
                for (FormattedCharSequence line : lines) {
                    g.text(font, line, contentLeft, lineY, C_BODY, false);
                    lineY += font.lineHeight + 1;
                }
                yield lineY + 2;
            }

            case GuidebookPage.PageToken.Image img -> {
                int w = Math.min(img.displayWidth(), contentWidth);
                int h = img.displayHeight();
                int x = contentLeft + (contentWidth - w) / 2;
                g.blit(img.texture(), x, y, x + w, y + h, 0f, 1f, 0f, 1f);
                yield y + h + 4;
            }

            case GuidebookPage.PageToken.LinkBlock link -> {
                boolean hovered = mx >= contentLeft && mx < contentLeft + contentWidth
                        && my >= y && my < y + font.lineHeight + 2;
                int color = hovered ? C_LINK_HOVER : C_LINK;

                String label = link.label();
                // Append a small indicator for the link type
                String displayLabel = link.target() instanceof GuidebookPage.LinkTarget.Url
                        ? label + " [web]"
                        : label + " [->p" + (((GuidebookPage.LinkTarget.PageJump) link.target()).pageIndex() + 1) + "]";

                g.text(font, displayLabel, contentLeft, y, color, false);

                // Underline
                int labelW = font.width(displayLabel);
                g.fill(contentLeft, y + font.lineHeight, contentLeft + labelW, y + font.lineHeight + 1, color);

                // Record hit region (include scroll offset in stored coords so mouse tests work)
                renderedLinks.add(new RenderedLink(contentLeft, y, labelW, font.lineHeight + 2, link.target()));

                yield y + font.lineHeight + 5;
            }

            case GuidebookPage.PageToken.Spacer s -> y + s.pixels();
        };
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int visibleH = contentBottom - contentTop;
        int maxScroll = Math.max(0, totalContentHeight - visibleH);
        scrollOffset = Math.max(0, Math.min(scrollOffset + (int)(-dy * SCROLL_STEP), maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            for (RenderedLink link : renderedLinks) {
                if (mx >= link.x() && mx < link.x() + link.w()
                        && my >= link.y() && my < link.y() + link.h()) {
                    handleLink(link.target());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) {
            goToPrev();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            goToNext();
            return true;
        }
        return super.keyPressed(event);
    }

    // ── Link handling ─────────────────────────────────────────────────────────

    private void handleLink(GuidebookPage.LinkTarget target) {
        switch (target) {
            case GuidebookPage.LinkTarget.Url u -> {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                        Desktop.getDesktop().browse(URI.create(u.url()));
                    else
                        OmniTech.LOGGER.info("[Guidebook] Open URL: {}", u.url());
                } catch (Exception e) {
                    OmniTech.LOGGER.warn("[Guidebook] Cannot open URL {}: {}", u.url(), e.getMessage());
                }
            }
            case GuidebookPage.LinkTarget.PageJump j -> {
                if (j.pageIndex() >= 0 && j.pageIndex() < pages.size()) {
                    currentPage = j.pageIndex();
                    scrollOffset = 0;
                }
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void goToPrev() {
        if (currentPage > 0) { currentPage--; scrollOffset = 0; }
    }

    private void goToNext() {
        if (currentPage < pages.size() - 1) { currentPage++; scrollOffset = 0; }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
