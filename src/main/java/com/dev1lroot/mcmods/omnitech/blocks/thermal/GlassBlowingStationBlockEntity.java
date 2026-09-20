/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.thermal;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.GlassBlowingStationMenu;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

/**
 * Block entity for the Glass Blowing Station.
 *
 * <p>Interaction is stonecutter-style: put sand in {@link #SLOT_INPUT}, pick which shape to
 * blow from the recipe list in {@link GlassBlowingStationMenu}, take the result from
 * {@link #SLOT_OUTPUT}. The one addition over vanilla's stonecutter is heat — like
 * {@link SmelterBlockEntity}, this block only accepts external heat (from a Heater, Boiler, ...
 * anything pushing into {@link IHeatReceiver}) and the result can't actually be taken until the
 * station reaches the recipe's melting temperature; the menu's result slot checks that live,
 * every time, so cooling back down mid-session locks it again immediately.
 */
public class GlassBlowingStationBlockEntity extends BaseContainerBlockEntity implements IHeatReceiver, IColdReceiver {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT  = 2;

    private static final int MAX_HEAT = 2000;
    private static final int AMBIENT_TEMPERATURE = 15;
    private static final int HEAT_LOSS_INTERVAL = 20; // ticks between ambient-drift steps

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int temperature = 0;
    private int heatLossTimer = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> temperature;
                case 1 -> MAX_HEAT;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 0) temperature = value;
        }
        @Override public int getCount() { return 2; }
    };

    public GlassBlowingStationBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.GLASS_BLOWING_STATION.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.glass_blowing_station");
    }

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                          { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new GlassBlowingStationMenu(containerId, playerInventory, this, dataAccess);
    }

    // ── IHeatReceiver / IColdReceiver ────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (temperature >= MAX_HEAT) return 0;
        int absorbed = Math.min(celsius, MAX_HEAT - temperature);
        temperature += absorbed;
        return absorbed;
    }

    @Override
    public int addCold(int celsius) {
        if (temperature <= 0) return 0;
        int absorbed = Math.min(celsius, temperature);
        temperature -= absorbed;
        return absorbed;
    }

    public int getTemperature() { return temperature; }

    // ── Server tick — ambient heat drift + LIT state; crafting itself is menu-driven ──

    public static void serverTick(Level level, BlockPos pos, BlockState state, GlassBlowingStationBlockEntity be) {
        boolean changed = false;

        if (be.temperature != AMBIENT_TEMPERATURE) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                if (be.temperature > AMBIENT_TEMPERATURE) be.temperature--;
                else be.temperature++;
                changed = true;
            }
        } else {
            be.heatLossTimer = 0;
        }

        boolean shouldBeLit = be.temperature >= minRequiredTemperature();
        if (state.getValue(GlassBlowingStationBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(GlassBlowingStationBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        if (changed) {
            be.setChanged();
            if (!level.isClientSide()) level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** The lowest {@code requiredMinimalTemperature} across all loaded recipes (0 if none loaded yet). */
    private static int minRequiredTemperature() {
        int min = Integer.MAX_VALUE;
        for (var recipe : com.dev1lroot.mcmods.omnitech.recipes.GlassBlowingRecipeManager.getAllRecipes()) {
            min = Math.min(min, recipe.getRequiredMinimalTemperature());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter =
                new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(output);
            return output.buildResult();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        temperature = input.getIntOr("Temperature", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Temperature", temperature);
    }
}
