package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import com.dev1lroot.mcmods.omnitech.network.UploadProgramPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class ProgrammingStationScreen extends AbstractContainerScreen<ProgrammingStationMenu> {

    private static final int W = 320;
    private static final int H = 280;

    // Editor position (absolute offsets from leftPos/topPos)
    private static final int EDITOR_X = 8;
    private static final int EDITOR_Y = 40;
    private static final int EDITOR_W = 304;
    private static final int EDITOR_H = 136;   // 15 visible lines

    private final CodeEditorWidget editor = new CodeEditorWidget();
    private Button uploadBtn;

    // MCU reinsertion tracking
    private ItemStack lastMCU = ItemStack.EMPTY;
    private boolean hadMCU = false;

    public ProgrammingStationScreen(ProgrammingStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999; // hide default "Inventory" label
        editor.setText(menu.getInitialProgram());
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (W - this.font.width(this.title)) / 2;

        uploadBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.upload"),
                b -> doUpload())
                .bounds(this.leftPos + EDITOR_X, this.topPos + EDITOR_Y + EDITOR_H + 4, 88, 12)
                .build());
    }

    // ── Ticking ───────────────────────────────────────────────────────────────

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack mc = menu.getSlot(0).getItem();
        boolean hasMCU = !mc.isEmpty() && mc.getItem() instanceof MicrocontrollerItem;

        // Reload program when MCU is inserted and carries a program
        if (hasMCU && !hadMCU) {
            String prog = mc.getOrDefault(OmniTechDataComponents.PROGRAM.get(), "");
            if (!prog.isEmpty()) editor.setText(prog);
        }
        hadMCU = hasMCU;
        lastMCU = mc.copy();

        uploadBtn.active = hasMCU;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);

        // Window background
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);

        // MCU slot frame
        g.fill(this.leftPos + 150, this.topPos + 16, this.leftPos + 170, this.topPos + 36, 0xFF000000);
        g.fill(this.leftPos + 151, this.topPos + 17, this.leftPos + 169, this.topPos + 35, 0xFF8B8B8B);

        // Code editor
        editor.render(g, this.font, this.leftPos + EDITOR_X, this.topPos + EDITOR_Y, EDITOR_W, EDITOR_H);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);
        g.text(this.font, Component.translatable("gui.omnitech.microcontroller_slot"), 120, 8, 0xFF404040, false);
        g.text(this.font, Component.translatable("gui.omnitech.program"), 8, 32, 0xFF606060, false);

        // Status right of upload button
        boolean hasMCU = !menu.getSlot(0).getItem().isEmpty();
        String status = hasMCU ? "§aReady" : "§7No Microcontroller";
        g.text(this.font, Component.literal(status), EDITOR_X + 92, EDITOR_Y + EDITOR_H + 6, 0xFFFFFFFF, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { this.minecraft.player.closeContainer(); return true; }
        if (editor.keyPressed(event, EDITOR_H)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (cp > 0 && cp < 65536 && editor.charTyped((char) cp, EDITOR_H)) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            int ex = this.leftPos + EDITOR_X;
            int ey = this.topPos + EDITOR_Y;
            if (mx >= ex && mx < ex + EDITOR_W && my >= ey && my < ey + EDITOR_H) {
                editor.mouseClicked(mx, my, ex, ey, EDITOR_H);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int ex = this.leftPos + EDITOR_X;
        int ey = this.topPos + EDITOR_Y;
        if (x >= ex && x < ex + EDITOR_W && y >= ey && y < ey + EDITOR_H) {
            editor.mouseScrolled(scrollY, EDITOR_H);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    private void doUpload() {
        ClientPacketDistributor.sendToServer(
                new UploadProgramPacket(menu.getBlockPos(), editor.getText()));
    }
}
