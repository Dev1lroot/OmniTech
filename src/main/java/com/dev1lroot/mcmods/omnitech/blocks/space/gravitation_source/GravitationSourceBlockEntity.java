/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.GravitationSourceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Block entity for the {@link GravitationSourceBlock}.
 *
 * <p>On each server tick, finds all entities inside a sphere of {@link #radius} blocks
 * and applies an impulse that cancels normal gravity and pulls them toward the block's
 * center.  A static {@link #SERVER_ACTIVE_SOURCES} map is kept up-to-date so the
 * {@link com.dev1lroot.mcmods.omnitech.network.GravityFieldSyncPacket} sender can
 * quickly enumerate all active sources without scanning every block entity.
 */
public class GravitationSourceBlockEntity extends BaseContainerBlockEntity {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final int MIN_RADIUS     = 1;
    public static final int MAX_RADIUS     = 64;
    public static final int DEFAULT_RADIUS = 8;

    // ── Button IDs ────────────────────────────────────────────────────────────

    public static final int BTN_MINUS_5 = 0;
    public static final int BTN_MINUS_1 = 1;
    public static final int BTN_PLUS_1  = 2;
    public static final int BTN_PLUS_5  = 3;

    // ── Server-side registry ──────────────────────────────────────────────────

    /** Per-position entry: dimension key + radius. */
    public record GravityEntry(ResourceKey<Level> dimension, int radius) {}

    /**
     * All currently loaded, active GravitationSource block entities on the server.
     * Updated every tick by {@link #serverTick}; entries removed in {@link #setRemoved}.
     */
    public static final ConcurrentHashMap<BlockPos, GravityEntry> SERVER_ACTIVE_SOURCES =
            new ConcurrentHashMap<>();

    // ── State ─────────────────────────────────────────────────────────────────

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private int radius = DEFAULT_RADIUS;

    // ── ContainerData ─────────────────────────────────────────────────────────

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i)             { return i == 0 ? radius : 0; }
        @Override public void set(int i, int val)   { if (i == 0) radius = Math.clamp(val, MIN_RADIUS, MAX_RADIUS); }
        @Override public int getCount()             { return 1; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public GravitationSourceBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.GRAVITATION_SOURCE.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.gravitation_source");
    }

    @Override protected NonNullList<ItemStack> getItems()                     { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items)           { this.items = items; }
    @Override public int getContainerSize()                                   { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new GravitationSourceMenu(containerId, inv, this, dataAccess);
    }

    // ── Radius control ────────────────────────────────────────────────────────

    public boolean adjustRadius(int buttonId) {
        int delta = switch (buttonId) {
            case BTN_MINUS_5 -> -5;
            case BTN_MINUS_1 -> -1;
            case BTN_PLUS_1  -> +1;
            case BTN_PLUS_5  -> +5;
            default          -> 0;
        };
        if (delta == 0) return false;
        radius = Math.clamp(radius + delta, MIN_RADIUS, MAX_RADIUS);
        setChanged();
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   GravitationSourceBlockEntity be) {
        // Keep SERVER_ACTIVE_SOURCES current so LivingEntityGravityMixin and the
        // network sync can find this source.  Physics are handled by the mixin.
        SERVER_ACTIVE_SOURCES.put(pos.immutable(), new GravityEntry(level.dimension(), be.radius));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void setRemoved() {
        SERVER_ACTIVE_SOURCES.remove(getBlockPos().immutable());
        super.setRemoved();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getRadius() { return radius; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        radius = Math.clamp(input.getIntOr("Radius", DEFAULT_RADIUS), MIN_RADIUS, MAX_RADIUS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Radius", radius);
    }
}
