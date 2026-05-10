package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.items.RomItem;
import com.dev1lroot.mcmods.omnitech.network.FlashRomPacket;
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
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ProgrammingStationScreen extends AbstractContainerScreen<ProgrammingStationMenu> {

    private static final int W = 320;
    private static final int H = 240;

    // Hex editor area (offset from leftPos/topPos)
    private static final int EDITOR_X = 8;
    private static final int EDITOR_Y = 40;
    private static final int EDITOR_W = 304;
    private static final int EDITOR_H = 104;  // ~11 visible rows at 9px each

    private final HexEditorWidget hexEditor;
    private Button flashBtn;
    private Button fillZeroBtn;
    private Button loadBinBtn;

    // Track ROM reinsertion
    private ItemStack lastRomStack = ItemStack.EMPTY;
    private boolean hadRom = false;

    public ProgrammingStationScreen(ProgrammingStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999;
        hexEditor = new HexEditorWidget(menu.getInitialRomData().clone());
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (W - this.font.width(this.title)) / 2;

        flashBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.flash_rom"),
                b -> doFlash())
                .bounds(this.leftPos + EDITOR_X, this.topPos + EDITOR_Y + EDITOR_H + 4, 80, 12)
                .build());

        fillZeroBtn = addRenderableWidget(Button.builder(
                Component.literal("Fill 0x00"),
                b -> fillZero())
                .bounds(this.leftPos + EDITOR_X + 84, this.topPos + EDITOR_Y + EDITOR_H + 4, 70, 12)
                .build());

        loadBinBtn = addRenderableWidget(Button.builder(
                Component.literal("Load .bin"),
                b -> loadBinFile())
                .bounds(this.leftPos + EDITOR_X + 158, this.topPos + EDITOR_Y + EDITOR_H + 4, 70, 12)
                .build());
    }

    // ── Ticking ───────────────────────────────────────────────────────────────

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack rom = menu.getSlot(0).getItem();
        boolean hasRom = !rom.isEmpty() && rom.getItem() instanceof RomItem r
                && RomItem.TYPE_FIRMWARE.equals(r.getRomType());

        // Reload hex data when a new ROM is inserted
        if (hasRom && !hadRom) {
            hexEditor.setData(menu.getInitialRomData().clone());
        }
        hadRom      = hasRom;
        lastRomStack = rom.copy();

        flashBtn.active    = hasRom;
        fillZeroBtn.active = hasRom;
        loadBinBtn.active  = true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);

        // Window background
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);

        // ROM slot frame
        g.fill(this.leftPos + 150, this.topPos + 16, this.leftPos + 170, this.topPos + 36, 0xFF000000);
        g.fill(this.leftPos + 151, this.topPos + 17, this.leftPos + 169, this.topPos + 35, 0xFF8B8B8B);

        // Hex editor
        hexEditor.render(g, this.font,
                this.leftPos + EDITOR_X, this.topPos + EDITOR_Y, EDITOR_W, EDITOR_H);

        // Player inventory background rows
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = this.leftPos + 8 + col * 18;
                int sy = this.topPos  + 152 + row * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
                g.fill(sx, sy, sx + 16, sy + 16, 0xFFC6C6C6);
            }
        }
        // Hotbar row
        for (int col = 0; col < 9; col++) {
            int sx = this.leftPos + 8 + col * 18;
            int sy = this.topPos  + 210;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFFC6C6C6);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);
        g.text(this.font, Component.translatable("gui.omnitech.rom_slot"), 120, 8, 0xFF404040, false);
        g.text(this.font, Component.translatable("gui.omnitech.hex_editor"), EDITOR_X, 32, 0xFF606060, false);

        boolean hasRom = !menu.getSlot(0).getItem().isEmpty();
        byte[] data = hexEditor.getData();
        String info = hasRom
                ? (data.length > 0 ? data.length + " bytes" : "Empty ROM")
                : "§7No ROM";
        g.text(this.font, Component.literal(info),
                EDITOR_X + 160, EDITOR_Y + EDITOR_H + 7, 0xFFCCCCCC, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { this.minecraft.player.closeContainer(); return true; }
        if (hexEditor.keyPressed(event.key(), EDITOR_H)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (cp > 0 && cp < 65536 && hexEditor.charTyped((char) cp)) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            int ex = this.leftPos + EDITOR_X;
            int ey = this.topPos  + EDITOR_Y;
            if (mx >= ex && mx < ex + EDITOR_W && my >= ey && my < ey + EDITOR_H) {
                hexEditor.mouseClicked(mx, my, ex, ey, EDITOR_H);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int ex = this.leftPos + EDITOR_X;
        int ey = this.topPos  + EDITOR_Y;
        if (x >= ex && x < ex + EDITOR_W && y >= ey && y < ey + EDITOR_H) {
            hexEditor.mouseScrolled(scrollY, EDITOR_H);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doFlash() {
        ClientPacketDistributor.sendToServer(
                new FlashRomPacket(menu.getBlockPos(), hexEditor.getData().clone()));
    }

    private void fillZero() {
        byte[] data = hexEditor.getData();
        if (data.length > 0) {
            Arrays.fill(data, (byte) 0);
        } else {
            hexEditor.setData(new byte[65536]); // default 64 KB blank ROM
        }
    }

    private void loadBinFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(2);
            filters.put(stack.UTF8("*.bin")).put(stack.UTF8("*.img")).flip();
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                    "Load Firmware Binary", "", filters, "Firmware binary (*.bin, *.img)", false);
            if (path == null) return;
            try {
                byte[] data = Files.readAllBytes(Path.of(path));
                hexEditor.setData(data);
            } catch (IOException ignored) {}
        }
    }
}
