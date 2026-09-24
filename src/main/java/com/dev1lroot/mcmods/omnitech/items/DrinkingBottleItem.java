/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.chemistry.MixtureNaming;
import com.dev1lroot.mcmods.omnitech.recipes.DrinkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A glass bottle for drinks: a {@link FlaskItem} in every other respect (so it fills from a
 * Fluid Tank, the Fermenter or the Fluid Filler and pours back out the same way), except that
 * holding use drinks one {@value #SIP} mB sip.
 *
 * <p>What a sip does comes from {@link DrinkManager}: each component applies its drink's
 * nutrition and effects in proportion to its share of the bottle. Anything not meant to be drunk
 * tastes foul (nausea) and, if it is toxic or radioactive, poisons the drinker. Drinkability is
 * decided on the server only — the drink table isn't synced — so any non-empty bottle can be
 * raised to the lips.
 */
public class DrinkingBottleItem extends FlaskItem {

    /** mB drunk per sip — a full bottle is {@value FlaskItem#CAPACITY} / {@value #SIP} sips. */
    public static final int SIP = 125;
    private static final int DRINK_TICKS = 32;

    public DrinkingBottleItem(Properties properties) {
        super(properties);
    }

    // ── Drinking ──────────────────────────────────────────────────────────────

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (isEmpty(player.getItemInHand(hand))) return InteractionResult.PASS;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return isEmpty(stack) ? ItemUseAnimation.NONE : ItemUseAnimation.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return isEmpty(stack) ? 0 : DRINK_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Solution contents = getSolution(stack);
        if (contents.isEmpty()) return stack;

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);

        if (!level.isClientSide()) {
            Solution sip = contents.scaledTo(Math.min(SIP, contents.totalAmount()));
            applySip(sip, level, entity);
            setSolution(stack, contents.minus(sip));
        }
        return stack;
    }

    /** Applies what drinking {@code sip} does: each component weighted by its share of the sip. */
    private static void applySip(Solution sip, Level level, LivingEntity entity) {
        float total = sip.totalAmount();
        float nutrition = 0f, saturation = 0f;
        boolean foul = false, poisonous = false;

        for (Solution.Part part : sip.components()) {
            float share = part.amount() / total;
            DrinkManager.Drink drink = DrinkManager.find(part.fluid());
            if (drink == null) {
                foul = true;
                var physics = FluidPhysicsRegistry.get(part.fluid());
                if (physics.toxic() || physics.radioactiveLevel() > 0) poisonous = true;
                continue;
            }
            nutrition += drink.nutrition() * share;
            saturation += drink.saturation() * share;
            if (drink.clearsEffects()) entity.removeAllEffects();
            for (DrinkManager.Effect effect : drink.effects()) {
                int duration = effect.scaled() ? Math.round(effect.duration() * share) : effect.duration();
                if (duration < 20 || level.getRandom().nextFloat() >= effect.chance()) continue;
                BuiltInRegistries.MOB_EFFECT.get(effect.id()).ifPresent(holder ->
                        entity.addEffect(new MobEffectInstance(holder, duration, effect.amplifier())));
            }
        }

        if (entity instanceof Player player && nutrition > 0f) {
            player.getFoodData().eat(Math.round(nutrition), saturation);
        }
        if (foul) entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200));
        if (poisonous) entity.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 1));
    }

    // ── Display ───────────────────────────────────────────────────────────────

    @Override
    public Component getName(ItemStack stack) {
        Solution solution = getSolution(stack);
        if (solution.isEmpty()) return Component.translatable("item.omnitech.drinking_bottle");
        Component known = MixtureNaming.nameOf(solution);
        if (known != null) return Component.translatable("item.omnitech.drinking_bottle.filled", known);
        Solution.Part main = solution.dominant();
        return Component.translatable("item.omnitech.drinking_bottle.filled",
                main.resource().toStack(main.amount()).getHoverName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        int sips = (getTotalAmount(stack) + SIP - 1) / SIP;
        if (sips > 0) {
            tooltip.accept(Component.translatable("item.omnitech.drinking_bottle.sips", sips)
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
