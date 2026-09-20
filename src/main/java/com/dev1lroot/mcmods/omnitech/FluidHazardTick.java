/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Applies effects to players standing in a hazardous fluid — see
 * {@link FluidPhysicsRegistry.FluidPhysics#toxic()} and {@code radioactiveLevel()}.
 * Flammable fluids are handled separately, in
 * {@link com.dev1lroot.mcmods.omnitech.blocks.fluid.FlammableLiquidBlock}.
 *
 * <p>Checked every {@value #INTERVAL_TICKS} ticks, mirroring
 * {@link com.dev1lroot.mcmods.omnitech.radiation.RadiationTick}'s cadence.
 */
public class FluidHazardTick {

    private static final int INTERVAL_TICKS = 100; // 5 s
    private static final int EFFECT_DURATION = 140; // 7 s — outlasts the check interval

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (++tickCounter % INTERVAL_TICKS != 0) return;

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (player.level() != level) continue;

            boolean inToxic = false;
            int maxRad = 0;

            // A single block-position check misses partial submersion (the entity's
            // hitbox overlapping the fluid without its floor(position) landing inside
            // it), so check containment per fluid type the way NeoForge itself does —
            // Entity#isInFluidType samples the whole bounding box, not one block.
            for (OmniTechFluids.FluidObject fluidObject : OmniTechFluids.all().values()) {
                if (!player.isInFluidType(fluidObject.type.get())) continue;
                var physics = FluidPhysicsRegistry.get(fluidObject.source.get());
                if (physics.toxic()) inToxic = true;
                if (physics.radioactiveLevel() > maxRad) maxRad = physics.radioactiveLevel();
            }

            if (inToxic) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, EFFECT_DURATION, 0, false, true));
            }

            if (maxRad > 0) {
                int amplifier = Math.min(maxRad, 3) - 1; // level 1..3 -> amplifier 0..2 (Radiation I..III)
                Holder<MobEffect> effect = OmniTechMobEffects.RADIATION;
                MobEffectInstance existing = player.getEffect(effect);
                if (existing == null || existing.getAmplifier() < amplifier) {
                    player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, amplifier, false, true));
                }
            }
        }
    }
}
