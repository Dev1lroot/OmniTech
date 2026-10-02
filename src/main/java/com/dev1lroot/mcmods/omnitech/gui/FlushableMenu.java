/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.io.FluidFlushable;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** A machine menu whose tanks have flush buttons; resolved on the server only. */
public interface FlushableMenu {
    @Nullable FluidFlushable flushTarget(Player player);
}
