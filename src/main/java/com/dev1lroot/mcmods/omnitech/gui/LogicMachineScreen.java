package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class LogicMachineScreen extends AbstractContainerScreen<LogicMachineMenu> {

    private static final int W = 320;
    private static final int H = 256;

    private static final int EDITOR_X = 4;
    private static final int EDITOR_Y = 26;
    private static final int EDITOR_W = 312;
    private static final int EDITOR_H = 136;

    private Button runBtn, stopBtn, resetBtn;
    private Button ctrlTabBtn, dbgTabBtn, sysTabBtn;

    private final CodeEditorWidget debugEditor = new CodeEditorWidget();

    private enum Tab { CTRL, DBG, SYS }
    private Tab activeTab = Tab.CTRL;

    private ItemStack lastMCU = ItemStack.EMPTY;
    private boolean hadMCU = false;

    public LogicMachineScreen(LogicMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX  = (W - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 166;

        runBtn   = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.run"),
                b -> sendBtn(LogicMachineBlockEntity.BTN_RUN))
                .bounds(this.leftPos + 8, this.topPos + 40, 90, 12).build());
        stopBtn  = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.stop"),
                b -> sendBtn(LogicMachineBlockEntity.BTN_STOP))
                .bounds(this.leftPos + 106, this.topPos + 40, 90, 12).build());
        resetBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.reset"),
                b -> sendBtn(LogicMachineBlockEntity.BTN_RESET))
                .bounds(this.leftPos + 204, this.topPos + 40, 90, 12).build());

        ctrlTabBtn = addRenderableWidget(Button.builder(
                Component.literal("CTL"),
                b -> switchTab(Tab.CTRL))
                .bounds(this.leftPos + 230, this.topPos + 4, 20, 10).build());
        dbgTabBtn = addRenderableWidget(Button.builder(
                Component.literal("DBG"),
                b -> switchTab(Tab.DBG))
                .bounds(this.leftPos + 252, this.topPos + 4, 20, 10).build());
        sysTabBtn = addRenderableWidget(Button.builder(
                Component.literal("SYS"),
                b -> switchTab(Tab.SYS))
                .bounds(this.leftPos + 274, this.topPos + 4, 20, 10).build());

        debugEditor.setReadOnly(true);
        debugEditor.setText(menu.getProgram());
        updateTabVisibility();
    }

    private void switchTab(Tab tab) {
        activeTab = tab;
        if (tab == Tab.DBG) refreshConsole();
        updateTabVisibility();
    }

    private void refreshConsole() {
        String console = menu.getConsole();
        if (console.isEmpty()) {
            String src = menu.getProgram();
            debugEditor.setText(src.isEmpty() ? "(no output yet)" : src);
        } else {
            debugEditor.setText(console);
        }
    }

    private void updateTabVisibility() {
        runBtn.visible   = activeTab == Tab.CTRL;
        stopBtn.visible  = activeTab == Tab.CTRL;
        resetBtn.visible = activeTab == Tab.CTRL;
    }

    // ── Ticking ───────────────────────────────────────────────────────────────

    @Override
    protected void containerTick() {
        super.containerTick();

        ItemStack mc = menu.getSlot(0).getItem();
        boolean hasMCU = !mc.isEmpty() && mc.getItem() instanceof MicrocontrollerItem;

        boolean mcuChanged = hasMCU != hadMCU ||
                (hasMCU && !ItemStack.isSameItemSameComponents(mc, lastMCU));

        if (mcuChanged && activeTab == Tab.DBG) refreshConsole();
        hadMCU = hasMCU;
        lastMCU = mc.copy();

        if (activeTab == Tab.CTRL) {
            runBtn.active   = hasMCU && !menu.isRunning() && !menu.hasError();
            stopBtn.active  = menu.isRunning();
            resetBtn.active = hasMCU;
        }

        if (activeTab == Tab.DBG) {
            // Refresh console text every tick while running
            if (menu.isRunning()) refreshConsole();
            debugEditor.setHighlightLine(-1); // no line highlight for console view
            // Auto-scroll to bottom
            debugEditor.scrollToLine(Integer.MAX_VALUE, EDITOR_H);
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);

        // MCU slot frame (always visible)
        g.fill(this.leftPos + 294, this.topPos + 2, this.leftPos + 316, this.topPos + 22, 0xFF000000);
        g.fill(this.leftPos + 295, this.topPos + 3, this.leftPos + 315, this.topPos + 21, 0xFF8B8B8B);

        if (activeTab == Tab.DBG) {
            debugEditor.render(g, this.font, this.leftPos + EDITOR_X, this.topPos + EDITOR_Y, EDITOR_W, EDITOR_H);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // MCU label (always)
        g.text(this.font, Component.literal("MCU"), 291, 8, 0xFF404040, false);

        if (activeTab == Tab.CTRL) {
            g.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);
            String status;
            int color;
            if (!menu.hasMicrocontroller()) {
                status = "No Microcontroller"; color = 0xFF888888;
            } else if (menu.isRunning()) {
                status = String.format("Running  PC:0x%08X", menu.getCurrentLine() << 2);
                color  = 0xFF44FF44;
            } else if (menu.isHalted()) {
                status = String.format("Halted  PC:0x%08X", menu.getCurrentLine() << 2);
                color = 0xFFFFAA00;
            } else {
                status = "Stopped"; color = 0xFFAAAAAA;
            }
            g.text(this.font, Component.literal(status), 8, 26, color, false);

        } else if (activeTab == Tab.DBG) {
            g.text(this.font, Component.literal("CONSOLE"), 8, 6, 0xFF44FF44, false);
            String status;
            if (menu.isRunning())     status = String.format("Running  PC:0x%08X", menu.getCurrentLine() << 2);
            else if (menu.isHalted()) status = String.format("Halted   PC:0x%08X", menu.getCurrentLine() << 2);
            else                      status = "Stopped";
            g.text(this.font, Component.literal(status), 64, 6, 0xFF888888, false);

        } else { // SYS tab
            g.text(this.font, Component.literal("SYSTEM"), 8, 6, 0xFF44AAFF, false);
            int y = 20;
            int ram = menu.getTotalRamBytes();
            int gpioN = menu.getGpioCount();
            int floppyN = menu.getFloppyCount();
            int ramCards = menu.getRamCardCount();

            g.text(this.font, Component.literal(
                    "RAM: " + ram + " B  (" + ramCards + " card" + (ramCards != 1 ? "s" : "") + ")"),
                    8, y, 0xFF44FF88, false);
            y += 10;
            g.text(this.font, Component.literal("GPIO Ports: " + gpioN),
                    8, y, 0xFFFFAA44, false);
            y += 10;
            g.text(this.font, Component.literal("Floppy Drives: " + floppyN),
                    8, y, 0xFFFF88AA, false);
            y += 10;

            // RISC-V quick reference
            y += 4;
            g.text(this.font, Component.literal("RISC-V RV32GC — 32i + 32f regs"), 8, y, 0xFFCCCCCC, false);
            y += 9;
            g.text(this.font, Component.literal("  I: lw/sw/lb/sb/lh/sh/jal/beq/…"), 8, y, 0xFF888888, false);
            y += 9;
            g.text(this.font, Component.literal("  M: mul/div/rem  A: lr.w/sc.w/amo*.w"), 8, y, 0xFF888888, false);
            y += 9;
            g.text(this.font, Component.literal("  F: flw/fsw/fadd.s/fmul.s/fcvt.w.s/…"), 8, y, 0xFF888888, false);
            y += 9;
            g.text(this.font, Component.literal("  D: fld/fsd/fadd.d/fmul.d/fcvt.w.d/…"), 8, y, 0xFF888888, false);
            y += 9;
            g.text(this.font, Component.literal("  C: c.add/c.mv/c.lw/c.fld/c.beqz/…"), 8, y, 0xFF888888, false);
            y += 9;
            g.text(this.font, Component.literal("  CSR: fcsr/frm/fflags  GPIO: 0xF0000000"), 8, y, 0xFF888888, false);
            y += 9;
            g.text(this.font, Component.literal("  ECALL a7: 0:halt 1:slp 2:rnd 10/11:gpio"), 8, y, 0xFF888888, false);
            y += 9;
            g.text(this.font, Component.literal("  20-25:display  30:floppy"), 8, y, 0xFF888888, false);
        }

        // Player inventory label (always)
        g.text(this.font, Component.translatable("container.inventory"), 8, 166, 0xFF404040, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { this.minecraft.player.closeContainer(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (activeTab == Tab.DBG) {
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
