package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.VMTerminal;
import com.dev1lroot.mcmods.omnitech.network.TerminalInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

public class LogicMachineScreen extends AbstractContainerScreen<LogicMachineMenu> {

    // 80 cols × 6px + 20px padding = 500px wide
    // 1px border + 24 rows × 9px = 217px + buttons/status = 250px high
    private static final int W = 500;
    private static final int H = 250;

    // Terminal display area (absolute within background image)
    private static final int TERM_X  = 10;
    private static final int TERM_Y  = 20;  // below status bar
    private static final int CHAR_W  = 6;
    private static final int CHAR_H  = 9;

    // ANSI terminal colors → ARGB
    private static final int[] ANSI_COLORS = {
        0xFF000000, // 0 black
        0xFFAA0000, // 1 red
        0xFF00AA00, // 2 green
        0xFFAAAA00, // 3 yellow / brown
        0xFF0000AA, // 4 blue
        0xFFAA00AA, // 5 magenta
        0xFF00AAAA, // 6 cyan
        0xFFAAAAAA, // 7 white/light gray
    };

    private Button runBtn, stopBtn, resetBtn;
    private boolean captureInput = false;

    public LogicMachineScreen(LogicMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999; // hide inventory label
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (W - this.font.width(this.title)) / 2;

        int bY = this.topPos + 3;
        runBtn   = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.run"),
                b -> clickBtn(LogicMachineBlockEntity.BTN_RUN))
                .bounds(this.leftPos + 10, bY, 60, 10).build());
        stopBtn  = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.stop"),
                b -> clickBtn(LogicMachineBlockEntity.BTN_STOP))
                .bounds(this.leftPos + 74, bY, 60, 10).build());
        resetBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.reset"),
                b -> clickBtn(LogicMachineBlockEntity.BTN_RESET))
                .bounds(this.leftPos + 138, bY, 60, 10).build());

        addRenderableWidget(Button.builder(
                Component.literal(captureInput ? "[Keys: ON]" : "[Keys: OFF]"),
                b -> toggleCapture(b))
                .bounds(this.leftPos + W - 90, bY, 80, 10).build());
    }

    private void toggleCapture(Button b) {
        captureInput = !captureInput;
        b.setMessage(Component.literal(captureInput ? "[Keys: ON]" : "[Keys: OFF]"));
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean running = menu.isRunning();
        runBtn.active   = !running;
        stopBtn.active  = running;
        resetBtn.active = true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);

        // Background
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFF1A1A1A);
        g.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + W - 1, this.topPos + H - 1, 0xFF111111);

        // Terminal border
        int tx = this.leftPos + TERM_X;
        int ty = this.topPos + TERM_Y;
        int tw = VMTerminal.COLS * CHAR_W;
        int th = VMTerminal.ROWS * CHAR_H;
        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xFF333333);
        g.fill(tx, ty, tx + tw, ty + th, 0xFF000000);

        // Render terminal cells
        LogicMachineBlockEntity lm = menu.getBlockEntity();
        VMTerminal terminal = lm != null ? lm.getTerminal() : null;
        if (terminal != null) {
            int[] cells = terminal.getCellSnapshot();
            int curRow = terminal.getCursorRow();
            int curCol = terminal.getCursorCol();
            long now = System.currentTimeMillis();
            boolean cursorVisible = (now / 500) % 2 == 0;

            for (int row = 0; row < VMTerminal.ROWS; row++) {
                for (int col = 0; col < VMTerminal.COLS; col++) {
                    int cell = cells[row * VMTerminal.COLS + col];
                    int fg = (cell >> 24) & 0x7;
                    int bg = (cell >> 16) & 0x7;
                    int ch = cell & 0xFFFF;

                    int cx = tx + col * CHAR_W;
                    int cy = ty + row * CHAR_H;

                    // Draw cursor
                    boolean isCursor = cursorVisible && row == curRow && col == curCol;
                    if (isCursor) {
                        g.fill(cx, cy, cx + CHAR_W, cy + CHAR_H, 0xFFCCCCCC);
                    } else if (bg != 0) {
                        g.fill(cx, cy, cx + CHAR_W, cy + CHAR_H, ANSI_COLORS[bg]);
                    }

                    if (ch > 0x20 && ch < 0x7F) {
                        int textColor = isCursor ? 0xFF000000 : ANSI_COLORS[fg];
                        g.text(this.font, String.valueOf((char) ch), cx, cy, textColor, false);
                    }
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Status bar (top-right of the terminal area)
        String status;
        int statusColor;
        if (menu.isRunning()) {
            status = "RUNNING"; statusColor = 0xFF44FF44;
        } else {
            status = "STOPPED"; statusColor = 0xFFAAAAAA;
        }
        g.text(this.font, Component.literal("Linux VM  |  " + status +
                (captureInput ? "  |  KEYBOARD CAPTURED" : "")),
                TERM_X, 7, statusColor, false);
    }

    // ── Keyboard input ────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Always allow closing
        if (event.isEscape() && !captureInput) {
            this.minecraft.player.closeContainer();
            return true;
        }

        if (!captureInput) return super.keyPressed(event);

        // Translate Minecraft KeyEvent → terminal byte sequence
        byte[] seq = translateKey(event);
        if (seq != null && seq.length > 0) {
            ClientPacketDistributor.sendToServer(new TerminalInputPacket(menu.getBlockPos(), seq));
            return true;
        }
        return true; // consume all keys when capturing
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!captureInput) return super.charTyped(event);

        int cp = event.codepoint();
        if (cp >= 0x20 && cp < 0x7F) {
            ClientPacketDistributor.sendToServer(
                    new TerminalInputPacket(menu.getBlockPos(), new byte[]{(byte) cp}));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Key translation ───────────────────────────────────────────────────────

    private static byte[] translateKey(KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown();

        if (ctrl) {
            // Ctrl+letter → control codes
            if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                return new byte[]{(byte) (key - GLFW.GLFW_KEY_A + 1)};
            }
            if (key == GLFW.GLFW_KEY_LEFT_BRACKET)  return new byte[]{0x1B};
            if (key == GLFW.GLFW_KEY_BACKSLASH)     return new byte[]{0x1C};
            if (key == GLFW.GLFW_KEY_RIGHT_BRACKET) return new byte[]{0x1D};
        }

        return switch (key) {
            case GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_KP_ENTER -> new byte[]{'\r'};
            case GLFW.GLFW_KEY_BACKSPACE -> new byte[]{0x7F}; // DEL
            case GLFW.GLFW_KEY_TAB      -> new byte[]{'\t'};
            case GLFW.GLFW_KEY_ESCAPE   -> new byte[]{0x1B};
            case GLFW.GLFW_KEY_UP       -> new byte[]{0x1B, '[', 'A'};
            case GLFW.GLFW_KEY_DOWN     -> new byte[]{0x1B, '[', 'B'};
            case GLFW.GLFW_KEY_RIGHT    -> new byte[]{0x1B, '[', 'C'};
            case GLFW.GLFW_KEY_LEFT     -> new byte[]{0x1B, '[', 'D'};
            case GLFW.GLFW_KEY_HOME     -> new byte[]{0x1B, '[', 'H'};
            case GLFW.GLFW_KEY_END      -> new byte[]{0x1B, '[', 'F'};
            case GLFW.GLFW_KEY_PAGE_UP  -> new byte[]{0x1B, '[', '5', '~'};
            case GLFW.GLFW_KEY_PAGE_DOWN-> new byte[]{0x1B, '[', '6', '~'};
            case GLFW.GLFW_KEY_DELETE   -> new byte[]{0x1B, '[', '3', '~'};
            case GLFW.GLFW_KEY_INSERT   -> new byte[]{0x1B, '[', '2', '~'};
            case GLFW.GLFW_KEY_F1  -> new byte[]{0x1B, 'O', 'P'};
            case GLFW.GLFW_KEY_F2  -> new byte[]{0x1B, 'O', 'Q'};
            case GLFW.GLFW_KEY_F3  -> new byte[]{0x1B, 'O', 'R'};
            case GLFW.GLFW_KEY_F4  -> new byte[]{0x1B, 'O', 'S'};
            default -> null;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clickBtn(int id) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
}
