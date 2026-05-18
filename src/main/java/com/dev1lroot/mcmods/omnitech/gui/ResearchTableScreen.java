/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.ResearchLoader;
import com.dev1lroot.mcmods.omnitech.network.MinesweeperResultPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int W = 218;
    private static final int H = 244;

    /** Left panel: input slots at (8+col*18, 14+row*18), output at (8, 91). */
    private static final int MINESWEEPER_GUI_X = 76;
    private static final int MINESWEEPER_GUI_Y = 14;
    private static final int CELL = 14;
    private static final int GRID = 9;
    private static final int NUM_MINES = 10;

    // ── Minesweeper state (all client-side) ───────────────────────────────────

    private enum State { IDLE, PLAYING, WON, LOST }

    private boolean[] mines       = new boolean[GRID * GRID];
    private boolean[] revealed    = new boolean[GRID * GRID];
    private boolean[] flagged     = new boolean[GRID * GRID];
    private int[]     neighbors   = new int[GRID * GRID];
    private State     msState     = State.IDLE;
    private int       winsThisSession = 0;

    /** Screen-space top-left of the minesweeper grid (set in init). */
    private int msX, msY;

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999; // hide default "Inventory" label
        this.titleLabelX     = 9;
        this.titleLabelY     = 9999; // we draw it manually in extractLabels
    }

    @Override
    protected void init() {
        super.init();
        msX = this.leftPos + MINESWEEPER_GUI_X;
        msY = this.topPos  + MINESWEEPER_GUI_Y;
        resetGame();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    protected void containerTick() {
        super.containerTick();
        // If research was completed (progress reset to 0 after completion), also reset session wins
        if (menu.getProgress() == 0 && msState == State.IDLE) winsThisSession = 0;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);

        // ── Window background ──────────────────────────────────────────────
        g.fill(leftPos, topPos, leftPos + W, topPos + H, 0xFFC6C6C6);

        // ── Divider between left and right panels ──────────────────────────
        g.fill(leftPos + 73, topPos + 4, leftPos + 74, topPos + H - 6, 0xFF888888);

        // ── Input slot frames (3×3) ────────────────────────────────────────
        for (int i = 0; i < 9; i++) {
            int sx = leftPos + 8 + (i % 3) * 18;
            int sy = topPos  + 14 + (i / 3) * 18;
            drawSlotFrame(g, sx, sy);
        }

        // ── Progress bar ───────────────────────────────────────────────────
        int progress = menu.getProgress();
        int pbX = leftPos + 8, pbY = topPos + 70;
        int pbW = 58, pbH = 8;
        g.fill(pbX - 1, pbY - 1, pbX + pbW + 1, pbY + pbH + 1, 0xFF555555); // border
        g.fill(pbX, pbY, pbX + pbW, pbY + pbH, 0xFF222222);                  // bg
        if (progress > 0) {
            int filled = (pbW * progress) / 100;
            int barColor = progress >= 100 ? 0xFF00FF00 : 0xFF00AAFF;
            g.fill(pbX, pbY, pbX + filled, pbY + pbH, barColor);
        }

        // ── Output slot frame ──────────────────────────────────────────────
        int osx = leftPos + 8, osy = topPos + 91;
        g.fill(osx - 2, osy - 2, osx + 18, osy + 18, 0xFF444444); // highlight border
        drawSlotFrame(g, osx, osy);

        // ── Player inventory frames ────────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(g, leftPos + 8 + col * 18, topPos + 164 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(g, leftPos + 8 + col * 18, topPos + 222);
        }

        // ── Minesweeper grid ───────────────────────────────────────────────
        renderMinesweeper(g);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Left panel title (top-left)
        g.text(font, Component.literal("Research Table"), 9, 4, 0xFF404040, false);

        // Left panel labels
        int progress = menu.getProgress();
        g.text(font, Component.literal("Progress: " + progress + "%"), 9, 61, 0xFF404040, false);
        g.text(font, Component.literal("Output:"), 9, 82, 0xFF606060, false);

        // Right panel header
        g.text(font, Component.literal("Minesweeper"), MINESWEEPER_GUI_X + 2, 5, 0xFF404040, false);

        // Research info
        String researchName = getActiveResearchName();
        int statusY = MINESWEEPER_GUI_Y + GRID * CELL + 4;

        if (researchName != null) {
            g.text(font, Component.literal(researchName).withColor(0xFF00AAFF),
                    MINESWEEPER_GUI_X + 2, statusY, 0xFFFFFFFF, false);
        } else {
            g.text(font, Component.literal("Insert items to research"),
                    MINESWEEPER_GUI_X + 2, statusY, 0xFFAAAAAA, false);
        }

        // Win / status message
        int msgY = statusY + 10;
        switch (msState) {
            case IDLE ->
                g.text(font, Component.literal("Click to start game"),
                        MINESWEEPER_GUI_X + 2, msgY, 0xFF888888, false);
            case PLAYING -> {
                int winsNeeded = getWinsRequired();
                g.text(font, Component.literal("Wins: " + winsThisSession + "/" + winsNeeded),
                        MINESWEEPER_GUI_X + 2, msgY, 0xFF00CC00, false);
            }
            case WON ->
                g.text(font, Component.literal("You won! Click to play again"),
                        MINESWEEPER_GUI_X + 2, msgY, 0xFF00FF00, false);
            case LOST ->
                g.text(font, Component.literal("You lost! Progress reset. Click to retry"),
                        MINESWEEPER_GUI_X + 2, msgY, 0xFFFF4444, false);
        }

        // Player inventory label
        g.text(font, Component.translatable("container.inventory"), 8, 155, 0xFF404040, false);
    }

    // ── Minesweeper rendering ─────────────────────────────────────────────────

    private void renderMinesweeper(GuiGraphicsExtractor g) {
        boolean hasResearch = getActiveResearchName() != null;

        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int idx = row * GRID + col;
                int px  = msX + col * CELL;
                int py  = msY + row * CELL;

                if (!hasResearch && msState == State.IDLE) {
                    // Gray out board when no research matched
                    g.fill(px, py, px + CELL, py + CELL, 0xFF555555);
                    g.fill(px + 1, py + 1, px + CELL - 1, py + CELL - 1, 0xFF999999);
                    continue;
                }

                if (!revealed[idx]) {
                    // Unrevealed cell
                    g.fill(px, py, px + CELL, py + CELL, 0xFF555555);         // border
                    g.fill(px + 1, py + 1, px + CELL - 1, py + CELL - 1, 0xFFAAAAAA); // face

                    // Highlight top/left (raised look)
                    g.fill(px + 1, py + 1, px + CELL - 1, py + 2, 0xFFCCCCCC);
                    g.fill(px + 1, py + 1, px + 2, py + CELL - 1, 0xFFCCCCCC);

                    if (flagged[idx]) {
                        // Flag: small orange square
                        g.fill(px + 4, py + 3, px + 10, py + 9, 0xFFFF6600);
                        g.fill(px + 5, py + 4, px + 9, py + 8, 0xFFFF0000);
                    }

                    // Show mines after game over (but only un-flagged mines)
                    if (msState == State.LOST && mines[idx] && !flagged[idx]) {
                        g.fill(px + 3, py + 3, px + CELL - 3, py + CELL - 3, 0xFF111111);
                    }
                } else {
                    // Revealed cell
                    if (msState == State.LOST && mines[idx]) {
                        // Exploded mine
                        g.fill(px, py, px + CELL, py + CELL, 0xFF880000);
                        g.fill(px + 2, py + 2, px + CELL - 2, py + CELL - 2, 0xFFFF2200);
                    } else {
                        // Safe cell
                        g.fill(px, py, px + CELL, py + CELL, 0xFF888888);
                        g.fill(px + 1, py + 1, px + CELL - 1, py + CELL - 1, 0xFFCCCCCC);
                        int n = neighbors[idx];
                        if (n > 0) {
                            g.text(font, Component.literal(String.valueOf(n)),
                                    px + 4, py + 3, numberColor(n), false);
                        }
                    }
                }
            }
        }
    }

    private static int numberColor(int n) {
        return switch (n) {
            case 1 -> 0xFF0000FF;
            case 2 -> 0xFF008000;
            case 3 -> 0xFFFF0000;
            case 4 -> 0xFF000080;
            case 5 -> 0xFF800000;
            case 6 -> 0xFF008080;
            case 7 -> 0xFF000000;
            default -> 0xFF808080;
        };
    }

    private static void drawSlotFrame(GuiGraphicsExtractor g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
        g.fill(sx,     sy,     sx + 16, sy + 16, 0xFFC6C6C6);
    }

    // ── Input handling ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        // Is the click inside the minesweeper grid?
        if (mx >= msX && mx < msX + GRID * CELL && my >= msY && my < msY + GRID * CELL) {
            int col = (int) ((mx - msX) / CELL);
            int row = (int) ((my - msY) / CELL);

            if (msState == State.WON || msState == State.LOST) {
                resetGame();
                return true;
            }

            if (getActiveResearchName() == null) return true; // no research active

            if (event.button() == 0) leftClick(row, col);
            else if (event.button() == 1) rightClick(row, col);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void leftClick(int row, int col) {
        int idx = row * GRID + col;
        if (revealed[idx] || flagged[idx]) return;

        if (msState == State.IDLE) {
            generateMines(idx);
            msState = State.PLAYING;
        }
        if (msState != State.PLAYING) return;

        if (mines[idx]) {
            revealed[idx] = true;
            msState = State.LOST;
            ClientPacketDistributor.sendToServer(
                    new MinesweeperResultPacket(menu.getBlockEntity().getBlockPos(), false));
        } else {
            floodReveal(idx);
            if (isWon()) {
                msState = State.WON;
                winsThisSession++;
                ClientPacketDistributor.sendToServer(
                        new MinesweeperResultPacket(menu.getBlockEntity().getBlockPos(), true));
            }
        }
    }

    private void rightClick(int row, int col) {
        int idx = row * GRID + col;
        if (revealed[idx]) return;
        if (msState == State.IDLE || msState == State.PLAYING) {
            flagged[idx] = !flagged[idx];
            if (msState == State.IDLE) msState = State.PLAYING;
        }
    }

    // ── Minesweeper logic ─────────────────────────────────────────────────────

    private void generateMines(int safeIdx) {
        Set<Integer> safeCells = new HashSet<>();
        int safeRow = safeIdx / GRID, safeCol = safeIdx % GRID;
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            int r = safeRow + dr, c = safeCol + dc;
            if (r >= 0 && r < GRID && c >= 0 && c < GRID) safeCells.add(r * GRID + c);
        }

        Random rng = new Random();
        int placed = 0;
        while (placed < NUM_MINES) {
            int cell = rng.nextInt(GRID * GRID);
            if (!mines[cell] && !safeCells.contains(cell)) {
                mines[cell] = true;
                placed++;
            }
        }

        // Precompute neighbor counts
        for (int r = 0; r < GRID; r++) for (int c = 0; c < GRID; c++) {
            if (mines[r * GRID + c]) continue;
            int cnt = 0;
            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < GRID && nc >= 0 && nc < GRID && mines[nr * GRID + nc]) cnt++;
            }
            neighbors[r * GRID + c] = cnt;
        }
    }

    private void floodReveal(int idx) {
        if (idx < 0 || idx >= GRID * GRID) return;
        if (revealed[idx] || flagged[idx] || mines[idx]) return;
        revealed[idx] = true;
        if (neighbors[idx] == 0) {
            int row = idx / GRID, col = idx % GRID;
            for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr, nc = col + dc;
                if (nr >= 0 && nr < GRID && nc >= 0 && nc < GRID) floodReveal(nr * GRID + nc);
            }
        }
    }

    private boolean isWon() {
        for (int i = 0; i < GRID * GRID; i++) {
            if (!mines[i] && !revealed[i]) return false;
        }
        return true;
    }

    private void resetGame() {
        mines     = new boolean[GRID * GRID];
        revealed  = new boolean[GRID * GRID];
        flagged   = new boolean[GRID * GRID];
        neighbors = new int[GRID * GRID];
        msState   = State.IDLE;
    }

    // ── Research helpers ──────────────────────────────────────────────────────

    private String getActiveResearchName() {
        java.util.List<ItemStack> inputItems = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) inputItems.add(menu.getSlot(i).getItem());
        Optional<ResearchLoader.ResearchDefinition> res = ResearchLoader.findMatch(inputItems);
        return res.map(ResearchLoader.ResearchDefinition::displayName).orElse(null);
    }

    private int getWinsRequired() {
        java.util.List<ItemStack> inputItems = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) inputItems.add(menu.getSlot(i).getItem());
        Optional<ResearchLoader.ResearchDefinition> res = ResearchLoader.findMatch(inputItems);
        return res.map(ResearchLoader.ResearchDefinition::winsRequired).orElse(4);
    }
}
