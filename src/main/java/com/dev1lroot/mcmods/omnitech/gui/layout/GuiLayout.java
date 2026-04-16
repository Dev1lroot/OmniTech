package com.dev1lroot.mcmods.omnitech.gui.layout;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Root data class for a GUI layout JSON file.
 * Populated by Gson; all fields default to sensible values if omitted.
 */
public class GuiLayout {

    /** Mod-relative texture path, e.g. {@code "textures/gui/empty.png"}. */
    public String background    = "textures/gui/empty.png";
    /** Whether to render the player-inventory grid and hotbar. */
    public boolean draw_inventory = true;
    /** GUI panel width in pixels (sets {@code AbstractContainerScreen.imageWidth}). */
    public int width  = 176;
    /** GUI panel height in pixels (sets {@code AbstractContainerScreen.imageHeight}). */
    public int height = 166;

    /** Player-inventory positioning sub-object. */
    public InventoryLayout inventory = new InventoryLayout();

    /** Ordered list of UI elements to render. */
    public List<GuiElementDef> elements = new ArrayList<>();

    // ── Convenience ──────────────────────────────────────────────────────────

    public List<GuiElementDef> getElementsByType(String type) {
        return elements.stream().filter(e -> type.equals(e.type)).toList();
    }

    public Optional<GuiElementDef> getElementById(String id) {
        return elements.stream().filter(e -> id.equals(e.id)).findFirst();
    }

    /**
     * Registers the standard 3-row player inventory and hotbar into any menu.
     *
     * <p>Usage (inside an {@code AbstractContainerMenu} subclass):
     * <pre>{@code layout.addPlayerInventory(playerInventory, this::addSlot);}</pre>
     *
     * @param playerInventory the player's inventory
     * @param slotAdder       reference to the menu's {@code addSlot} — passed as
     *                        {@code this::addSlot} from within the menu constructor
     */
    public void addPlayerInventory(Inventory playerInventory, Consumer<Slot> slotAdder) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slotAdder.accept(new Slot(playerInventory, col + row * 9 + 9,
                        inventory.x + col * 18, inventory.y + row * 18));

        for (int col = 0; col < 9; col++)
            slotAdder.accept(new Slot(playerInventory, col,
                    inventory.x + col * 18, inventory.hotbar_y));
    }

    // ── Nested ───────────────────────────────────────────────────────────────

    public static class InventoryLayout {
        /** Left edge of the 9-column player-inventory grid. */
        public int x       = 8;
        /** Top edge of the 3-row player-inventory grid. */
        public int y       = 84;
        /** Y position of the "Inventory" label drawn above the grid. */
        public int label_y = 72;
        /** Top edge of the hotbar row. */
        public int hotbar_y = 142;
    }
}
