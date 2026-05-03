package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class LogicMachineScreen extends AbstractContainerScreen<LogicMachineMenu> {

    private static final int W = 176;
    private static final int H = 218;

    // Debug editor bounds (absolute offsets from leftPos/topPos)
    private static final int EDITOR_X = 4;
    private static final int EDITOR_Y = 26;
    private static final int EDITOR_W = 168;
    private static final int EDITOR_H = 104;   // 11 visible lines

    private Button runBtn, stopBtn, resetBtn;
    private Button ctrlTabBtn, dbgTabBtn;

    private final CodeEditorWidget debugEditor = new CodeEditorWidget();
    private boolean inDebugTab = false;
    private ItemStack lastMCU = ItemStack.EMPTY;
    private boolean hadMCU = false;

    public LogicMachineScreen(LogicMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX  = (W - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 132;

        runBtn   = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.run"),
                b -> sendBtn(LogicMachineBlockEntity.BTN_RUN))
                .bounds(this.leftPos + 8, this.topPos + 40, 48, 12).build());
        stopBtn  = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.stop"),
                b -> sendBtn(LogicMachineBlockEntity.BTN_STOP))
                .bounds(this.leftPos + 62, this.topPos + 40, 48, 12).build());
        resetBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.reset"),
                b -> sendBtn(LogicMachineBlockEntity.BTN_RESET))
                .bounds(this.leftPos + 116, this.topPos + 40, 48, 12).build());

        ctrlTabBtn = addRenderableWidget(Button.builder(
                Component.literal("Ctrl"),
                b -> switchTab(false))
                .bounds(this.leftPos + 130, this.topPos + 4, 20, 10).build());
        dbgTabBtn = addRenderableWidget(Button.builder(
                Component.literal("Dbg"),
                b -> switchTab(true))
                .bounds(this.leftPos + 152, this.topPos + 4, 20, 10).build());

        debugEditor.setReadOnly(true);
        debugEditor.setText(menu.getProgram());
        updateTabVisibility();
    }

    private void switchTab(boolean debug) {
        inDebugTab = debug;
        if (debug) debugEditor.setText(menu.getProgram());
        updateTabVisibility();
    }

    private void updateTabVisibility() {
        runBtn.visible   = !inDebugTab;
        stopBtn.visible  = !inDebugTab;
        resetBtn.visible = !inDebugTab;
    }

    // ── Ticking ───────────────────────────────────────────────────────────────

    @Override
    protected void containerTick() {
        super.containerTick();

        ItemStack mc = menu.getSlot(0).getItem();
        boolean hasMCU = !mc.isEmpty() && mc.getItem() instanceof MicrocontrollerItem;

        boolean mcuChanged = hasMCU != hadMCU ||
                (hasMCU && !ItemStack.isSameItemSameComponents(mc, lastMCU));

        if (mcuChanged && inDebugTab) debugEditor.setText(menu.getProgram());
        hadMCU = hasMCU;
        lastMCU = mc.copy();

        // Controls tab button states
        if (!inDebugTab) {
            runBtn.active   = hasMCU && !menu.isRunning() && !menu.hasError();
            stopBtn.active  = menu.isRunning();
            resetBtn.active = hasMCU;
        }

        // Debug tab: track executing line
        if (inDebugTab) {
            int currentLine = menu.getCurrentLine();
            debugEditor.setHighlightLine(menu.isRunning() || menu.isHalted() ? currentLine : -1);
            if (menu.isRunning()) debugEditor.scrollToLine(currentLine, EDITOR_H);
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);

        // Window background
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);

        // MCU slot frame (top-right, always visible in both tabs)
        g.fill(this.leftPos + 114, this.topPos + 2, this.leftPos + 134, this.topPos + 22, 0xFF000000);
        g.fill(this.leftPos + 115, this.topPos + 3, this.leftPos + 133, this.topPos + 21, 0xFF8B8B8B);

        if (inDebugTab) {
            // Code editor covers the controls area
            debugEditor.render(g, this.font, this.leftPos + EDITOR_X, this.topPos + EDITOR_Y, EDITOR_W, EDITOR_H);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!inDebugTab) {
            // Controls tab
            g.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);
            g.text(this.font, Component.literal("MCU"), 88, 8, 0xFF404040, false);

            String status;
            int color;
            if (menu.hasError()) {
                status = "Compile Error"; color = 0xFFFF4444;
            } else if (!menu.hasMicrocontroller()) {
                status = "No Microcontroller"; color = 0xFF888888;
            } else if (menu.isRunning()) {
                status = "Running  (line " + (menu.getCurrentLine() + 1) + ")";
                color  = 0xFF44FF44;
            } else if (menu.isHalted()) {
                status = "Halted"; color = 0xFFFFAA00;
            } else {
                status = "Stopped"; color = 0xFFAAAAAA;
            }
            g.text(this.font, Component.literal(status), 8, 26, color, false);
        } else {
            // Debug tab
            g.text(this.font, Component.literal("DEBUG"), 8, 6, 0xFF44FF44, false);
            if (menu.isRunning()) {
                g.text(this.font,
                        Component.literal("Executing line " + (menu.getCurrentLine() + 1)),
                        8, 16, 0xFF88FF88, false);
            } else if (menu.isHalted()) {
                g.text(this.font, Component.literal("Halted at line " + (menu.getCurrentLine() + 1)),
                        8, 16, 0xFFFFAA00, false);
            } else {
                g.text(this.font, Component.literal("Stopped"), 8, 16, 0xFF888888, false);
            }
        }

        // Player inventory label (always shown)
        g.text(this.font, Component.translatable("container.inventory"), 8, 132, 0xFF404040, false);
    }

    // ── Input (debug editor scroll/key) ───────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { this.minecraft.player.closeContainer(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inDebugTab) {
            int ex = this.leftPos + EDITOR_X;
            int ey = this.topPos + EDITOR_Y;
            if (x >= ex && x < ex + EDITOR_W && y >= ey && y < ey + EDITOR_H) {
                debugEditor.mouseScrolled(scrollY, EDITOR_H);
                return true;
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendBtn(int id) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
}
