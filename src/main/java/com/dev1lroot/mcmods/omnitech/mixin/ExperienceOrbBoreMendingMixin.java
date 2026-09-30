/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.mixin;

import com.dev1lroot.mcmods.omnitech.items.BoreItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mending on a bore head: the head sits inside the bore, not in an equipment slot, so
 * vanilla never finds it. Let a held bore's head drink from each orb first; whatever XP
 * is left goes on to vanilla's own Mending pass and then to the player.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbBoreMendingMixin {

    @ModifyVariable(method = "repairPlayerItems", at = @At("HEAD"), argsOnly = true)
    private int omnitech$repairBoreHead(int amount, ServerPlayer player) {
        return BoreItem.repairHeldHeadsWithXp(player, amount);
    }
}
