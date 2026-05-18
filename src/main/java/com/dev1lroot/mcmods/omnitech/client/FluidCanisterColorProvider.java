/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

/**
 * Replaced by {@link FluidCanisterTintSource} — kept as empty shell for reference.
 *
 * <p>In NeoForge 26.1 the {@code ItemColor} callback API was removed in favour of
 * the data-driven {@link net.minecraft.client.color.item.ItemTintSource} system.
 * Fluid tinting for {@link com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem}
 * is now handled by {@link FluidCanisterTintSource}, which is registered via
 * {@link net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.ItemTintSources}
 * and referenced in the item definition JSON.
 */
public final class FluidCanisterColorProvider {
    private FluidCanisterColorProvider() {}
}
