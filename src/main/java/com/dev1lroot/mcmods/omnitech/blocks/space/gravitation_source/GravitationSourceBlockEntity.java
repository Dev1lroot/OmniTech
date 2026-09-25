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
 * <p>Gravity within the field is graduated: full strength inside {@link #innerRadius},
 * zero strength at {@link #outerRadius}, and linearly interpolated between them.
 * Entities inside the outer sphere are pulled toward this block by a fraction of the
 * base gravity constant proportional to the falloff factor α ∈ [0, 1].
 *
 * <p>Multiple active sources are combined additively (vector sum), so entities
 * between two sources experience a blended gravity direction.
 */
public class GravitationSourceBlockEntity extends BaseContainerBlockEntity {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final int MIN_RADIUS           = 1;
    public static final int MAX_RADIUS           = 64;
    public static final int DEFAULT_OUTER_RADIUS = 8;
    public static final int DEFAULT_INNER_RADIUS = 4;

    // ── Button IDs (outer radius) ─────────────────────────────────────────────

    public static final int BTN_MINUS_5 = 0;
    public static final int BTN_MINUS_1 = 1;
    public static final int BTN_PLUS_1  = 2;
    public static final int BTN_PLUS_5  = 3;

    // ── Button IDs (inner radius) ─────────────────────────────────────────────

    public static final int BTN_INNER_MINUS_5 = 4;
    public static final int BTN_INNER_MINUS_1 = 5;
    public static final int BTN_INNER_PLUS_1  = 6;
    public static final int BTN_INNER_PLUS_5  = 7;

    // ── Server-side registry ──────────────────────────────────────────────────

    /** Per-position entry: dimension key + outer/inner radii. */
    public record GravityEntry(ResourceKey<Level> dimension, int outerRadius, int innerRadius) {}

    /**
     * All currently loaded, active GravitationSource block entities on the server.
     * Updated every tick by {@link #serverTick}; entries removed in {@link #setRemoved}. Keyed by
     * dimension + position, so sources at the same coordinates in different dimensions coexist.
     */
    public static final ConcurrentHashMap<net.minecraft.core.GlobalPos, GravityEntry> SERVER_ACTIVE_SOURCES =
            new ConcurrentHashMap<>();

    // ── State ─────────────────────────────────────────────────────────────────

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private int outerRadius = DEFAULT_OUTER_RADIUS;
    private int innerRadius = DEFAULT_INNER_RADIUS;

    // ── ContainerData (slots: 0=outerRadius, 1=innerRadius) ──────────────────

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) { case 0 -> outerRadius; case 1 -> innerRadius; default -> 0; };
        }
        @Override
        public void set(int i, int val) {
            switch (i) {
                case 0 -> outerRadius = Math.clamp(val, MIN_RADIUS, MAX_RADIUS);
                case 1 -> innerRadius = Math.clamp(val, 0, outerRadius);
            }
        }
        @Override public int getCount() { return 2; }
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

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items)  { this.items = items; }
    @Override public int getContainerSize()                          { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new GravitationSourceMenu(containerId, inv, this, dataAccess);
    }

    // ── Radius control ────────────────────────────────────────────────────────

    public boolean adjustRadius(int buttonId) {
        switch (buttonId) {
            case BTN_MINUS_5 -> outerRadius = Math.clamp(outerRadius - 5, MIN_RADIUS, MAX_RADIUS);
            case BTN_MINUS_1 -> outerRadius = Math.clamp(outerRadius - 1, MIN_RADIUS, MAX_RADIUS);
            case BTN_PLUS_1  -> outerRadius = Math.clamp(outerRadius + 1, MIN_RADIUS, MAX_RADIUS);
            case BTN_PLUS_5  -> outerRadius = Math.clamp(outerRadius + 5, MIN_RADIUS, MAX_RADIUS);
            case BTN_INNER_MINUS_5 -> innerRadius = Math.clamp(innerRadius - 5, 0, outerRadius);
            case BTN_INNER_MINUS_1 -> innerRadius = Math.clamp(innerRadius - 1, 0, outerRadius);
            case BTN_INNER_PLUS_1  -> innerRadius = Math.clamp(innerRadius + 1, 0, outerRadius);
            case BTN_INNER_PLUS_5  -> innerRadius = Math.clamp(innerRadius + 5, 0, outerRadius);
            default -> { return false; }
        }
        // Clamp inner to not exceed outer if outer was reduced
        innerRadius = Math.min(innerRadius, outerRadius);
        setChanged();
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   GravitationSourceBlockEntity be) {
        SERVER_ACTIVE_SOURCES.put(net.minecraft.core.GlobalPos.of(level.dimension(), pos.immutable()),
                new GravityEntry(level.dimension(), be.outerRadius, be.innerRadius));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void setRemoved() {
        if (level != null) {
            SERVER_ACTIVE_SOURCES.remove(net.minecraft.core.GlobalPos.of(level.dimension(), getBlockPos().immutable()));
        }
        super.setRemoved();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getOuterRadius() { return outerRadius; }
    public int getInnerRadius() { return innerRadius; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        // "Radius" key kept for world-upgrade compatibility from the old single-radius format
        outerRadius = Math.clamp(input.getIntOr("OuterRadius",
                input.getIntOr("Radius", DEFAULT_OUTER_RADIUS)), MIN_RADIUS, MAX_RADIUS);
        innerRadius = Math.clamp(input.getIntOr("InnerRadius", DEFAULT_INNER_RADIUS),
                0, outerRadius);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("OuterRadius", outerRadius);
        output.putInt("InnerRadius", innerRadius);
    }
}
