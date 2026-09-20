/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.GlassBlowingStationBlockEntity;
import com.dev1lroot.mcmods.omnitech.recipes.GlassBlowingRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.GlassBlowingRecipeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu for the Glass Blowing Station — stonecutter-style: browse the recipe list matching
 * whatever's in the input slot, click one to select it, take the result to craft it. The only
 * addition over vanilla's stonecutter is that the result slot won't let you pick anything up
 * (see {@link #resultSlot}) unless the station is currently hot enough for the selected recipe —
 * checked fresh every time, not cached, so cooling down mid-session locks it again immediately.
 */
public class GlassBlowingStationMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT  = 0;
    public static final int RESULT_SLOT = 1;
    private static final int INV_SLOT_START = 2;
    private static final int INV_SLOT_END   = 29;
    private static final int USE_ROW_SLOT_START = 29;
    private static final int USE_ROW_SLOT_END   = 38;

    private final Container container;
    private final ContainerData data;
    private final DataSlot selectedRecipeIndex = DataSlot.standalone();
    private List<GlassBlowingRecipe> recipesForInput = new ArrayList<>();
    private ItemStack input = ItemStack.EMPTY;
    private final Slot inputSlot;
    private final Slot resultSlot;

    // Client constructor
    public GlassBlowingStationMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    // Server constructor
    public GlassBlowingStationMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.GLASS_BLOWING_STATION.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;
        addDataSlots(data);

        this.inputSlot = addSlot(new Slot(container, INPUT_SLOT, 20, 33));
        this.resultSlot = addSlot(new Slot(container, RESULT_SLOT, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }

            @Override
            public boolean mayPickup(Player player) {
                GlassBlowingRecipe recipe = selectedRecipe();
                return recipe != null && getTemperature() >= recipe.getRequiredMinimalTemperature();
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                carried.onCraftedBy(player, carried.getCount());
                ItemStack remaining = inputSlot.remove(1);
                if (!remaining.isEmpty()) {
                    setupResultSlot(selectedRecipeIndex.get());
                } else {
                    set(ItemStack.EMPTY);
                }
                super.onTake(player, carried);
            }
        });

        int invY = 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, invY + 58));
        }

        addDataSlot(selectedRecipeIndex);
    }

    // ── Data accessors ───────────────────────────────────────────────────────

    public int getTemperature()    { return data.get(0); }
    public int getMaxTemperature() { return data.get(1); }

    public int getSelectedRecipeIndex()      { return selectedRecipeIndex.get(); }
    public List<GlassBlowingRecipe> getVisibleRecipes() { return recipesForInput; }
    public int getNumberOfVisibleRecipes()   { return recipesForInput.size(); }
    public boolean hasInputItem()            { return inputSlot.hasItem() && !recipesForInput.isEmpty(); }

    private GlassBlowingRecipe selectedRecipe() {
        int index = selectedRecipeIndex.get();
        return (index >= 0 && index < recipesForInput.size()) ? recipesForInput.get(index) : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.GLASS_BLOWING_STATION.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (selectedRecipeIndex.get() == buttonId) return false;
        if (buttonId >= 0 && buttonId < recipesForInput.size()) {
            selectedRecipeIndex.set(buttonId);
            setupResultSlot(buttonId);
        }
        return true;
    }

    /**
     * A real block-entity-backed container doesn't call {@link #slotsChanged} the way vanilla's
     * stonecutter's purpose-built {@code SimpleContainer} does, so the input is checked here
     * instead. {@code broadcastChanges()} itself is only called server-side though — it's
     * {@code ServerPlayer.tick()} that drives it, once per tick, for whatever menu that player
     * currently has open — so the *client's* copy of this menu (what
     * {@link GlassBlowingStationScreen} actually reads to draw the recipe grid) never sees this
     * override fire on its own. {@link GlassBlowingStationScreen#containerTick()} calls
     * {@link #refreshRecipeListIfInputChanged()} directly to cover that side too; both run the
     * exact same deterministic computation from the input stack, so there's nothing to
     * reconcile between them, same as vanilla mirrors {@code clickMenuButton} on both sides.
     */
    @Override
    public void broadcastChanges() {
        refreshRecipeListIfInputChanged();
        super.broadcastChanges();
    }

    public void refreshRecipeListIfInputChanged() {
        ItemStack current = inputSlot.getItem();
        if (!ItemStack.matches(current, this.input)) {
            this.input = current.copy();
            setupRecipeList(current);
        }
    }

    private void setupRecipeList(ItemStack item) {
        selectedRecipeIndex.set(-1);
        resultSlot.set(ItemStack.EMPTY);
        recipesForInput = item.isEmpty() ? new ArrayList<>() : GlassBlowingRecipeManager.findRecipesFor(item);
    }

    private void setupResultSlot(int index) {
        if (!recipesForInput.isEmpty() && index >= 0 && index < recipesForInput.size()) {
            resultSlot.set(recipesForInput.get(index).getResult());
        } else {
            resultSlot.set(ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (slotIndex == RESULT_SLOT) {
            if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, result);
        } else if (slotIndex == INPUT_SLOT) {
            if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false)) return ItemStack.EMPTY;
        } else if (slotIndex >= INV_SLOT_START && slotIndex < INV_SLOT_END) {
            if (!moveItemStackTo(stack, 0, INPUT_SLOT + 1, false)
                    && !moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex >= USE_ROW_SLOT_START && slotIndex < USE_ROW_SLOT_END) {
            if (!moveItemStackTo(stack, 0, INPUT_SLOT + 1, false)
                    && !moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_START, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }
}
