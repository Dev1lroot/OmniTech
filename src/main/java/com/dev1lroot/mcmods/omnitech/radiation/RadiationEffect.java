package com.dev1lroot.mcmods.omnitech.radiation;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class RadiationEffect extends MobEffect {

    public RadiationEffect() {
        super(MobEffectCategory.HARMFUL, 0x33BB22); // sickly green
    }

    /** Ticks every 10 seconds (200 ticks) for nausea/damage. */
    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return tickCount > 0 && tickCount % 200 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplification) {
        if (!(entity instanceof Player player)) return true;

        // Apply 1 second of nausea
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20, 0, false, false));

        // 50% chance to deal 1 hp radiation damage
        if (level.getRandom().nextBoolean()) {
            player.hurt(level.damageSources().magic(), 1.0f);
        }
        return true;
    }
}
