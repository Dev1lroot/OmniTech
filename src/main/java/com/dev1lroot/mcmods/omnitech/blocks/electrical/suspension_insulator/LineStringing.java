/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator;

import com.dev1lroot.mcmods.omnitech.util.ConductorMetals;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Two-click span stringing: the first coil click on an insulator remembers it
 * as the start of a line, the second click on another insulator strings the
 * span and consumes the coils. Pending starts live on the server only.
 */
final class LineStringing {
    private LineStringing() {}

    private record Start(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<UUID, Start> PENDING = new HashMap<>();

    static void click(ServerPlayer player, ItemStack coil, SuspensionInsulatorBlockEntity here) {
        Level level = here.getLevel();
        BlockPos pos = here.getBlockPos();
        Start start = PENDING.get(player.getUUID());

        if (start == null || start.dimension != level.dimension()
                || !(level.getBlockEntity(start.pos) instanceof SuspensionInsulatorBlockEntity from)) {
            begin(player, level, pos);
            return;
        }
        if (start.pos.equals(pos)) {
            PENDING.remove(player.getUUID());
            player.sendOverlayMessage(Component.translatable("message.omnitech.insulator.cancelled"));
            return;
        }

        double length = from.attachPoint().distanceTo(here.attachPoint());
        if (length > SuspensionInsulatorBlockEntity.MAX_SPAN) {
            fail(player, Component.translatable("message.omnitech.insulator.too_long",
                    String.format("%.1f", length), SuspensionInsulatorBlockEntity.MAX_SPAN));
            return;
        }
        if (from.isLinkedTo(pos)) {
            fail(player, Component.translatable("message.omnitech.insulator.already_linked"));
            return;
        }
        if (!from.hasFreeSlot() || !here.hasFreeSlot()) {
            fail(player, Component.translatable("message.omnitech.insulator.full",
                    SuspensionInsulatorBlockEntity.MAX_LINKS));
            return;
        }
        int coils = SuspensionInsulatorBlockEntity.coilsFor(length);
        boolean creative = player.getAbilities().instabuild;
        if (!creative && coil.getCount() < coils) {
            fail(player, Component.translatable("message.omnitech.insulator.need_coils", coils));
            return;
        }

        String metal = ConductorMetals.metalOf(coil);
        float len = (float) length;
        from.addLink(new SuspensionInsulatorBlockEntity.Link(pos, metal, len));
        here.addLink(new SuspensionInsulatorBlockEntity.Link(start.pos, metal, len));
        if (!creative) coil.shrink(coils);
        PENDING.remove(player.getUUID());

        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
        player.sendOverlayMessage(Component.translatable("message.omnitech.insulator.linked",
                ConductorMetals.displayName(metal),
                String.format("%.1f", length),
                ElectricUnits.formatResistance(ConductorMetals.spanResistance(metal, length)))
                .withStyle(ChatFormatting.GREEN));
    }

    private static void begin(ServerPlayer player, Level level, BlockPos pos) {
        PENDING.put(player.getUUID(), new Start(level.dimension(), pos));
        player.sendOverlayMessage(Component.translatable("message.omnitech.insulator.start",
                pos.getX(), pos.getY(), pos.getZ()));
    }

    private static void fail(ServerPlayer player, Component reason) {
        PENDING.remove(player.getUUID());
        player.sendOverlayMessage(reason.copy().withStyle(ChatFormatting.RED));
    }
}
