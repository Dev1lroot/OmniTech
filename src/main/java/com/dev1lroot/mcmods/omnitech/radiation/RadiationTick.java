package com.dev1lroot.mcmods.omnitech.radiation;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.UUID;

public class RadiationTick {

    /** Identifier for the radiation health-reduction attribute modifier. */
    private static final Identifier HEALTH_DAMAGE_ID =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "radiation_health_damage");

    // Duration applied each 5-second cycle (10 seconds of effect)
    private static final int EFFECT_DURATION = 200;
    // Re-apply interval: every 5 seconds
    private static final int APPLY_INTERVAL  = 100;
    // Health reduction cooldowns
    private static final long REDUCE_COOLDOWN_II  = 3_600L; // 3 minutes in ticks
    private static final long REDUCE_COOLDOWN_III =   400L; // 20 seconds in ticks

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != ServerLevel.OVERWORLD) return;

        RadiationSavedData data = RadiationSavedData.get(level);

        // Process pending explosion blocks every tick
        data.tick(level);

        // Apply radiation effects and health reduction every APPLY_INTERVAL ticks
        if (++tickCounter % APPLY_INTERVAL != 0) return;

        List<BlockPos> centers = data.centers;
        if (centers.isEmpty()) return;

        long gameTime = level.getGameTime();

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            int amplifier = computeAmplifier(player, centers);

            if (amplifier < 0) continue; // player out of all zones

            applyRadiationEffect(player, amplifier);
            applyHealthReduction(level, player, amplifier, gameTime, data);
        }
    }

    /** Returns the highest radiation tier (0/1/2) based on distance to any center, or -1 if out of range. */
    private static int computeAmplifier(Player player, List<BlockPos> centers) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        int best = -1;
        for (BlockPos center : centers) {
            double dx = x - center.getX();
            double dy = y - center.getY();
            double dz = z - center.getZ();
            double d  = Math.sqrt(dx*dx + dy*dy + dz*dz);
            int tier;
            if      (d <= 64)  tier = 2; // Radiation III
            else if (d <= 128) tier = 1; // Radiation II
            else if (d <= 256) tier = 0; // Radiation I
            else continue;
            if (tier > best) best = tier;
            if (best == 2) break; // can't get higher
        }
        return best;
    }

    private static void applyRadiationEffect(ServerPlayer player, int amplifier) {
        Holder<MobEffect> effect = OmniTechMobEffects.RADIATION;

        // Radiation III gets "permanent" duration (Integer.MAX_VALUE / 2).
        // Lower tiers get EFFECT_DURATION (10 seconds), refreshed each cycle.
        if (amplifier == 2) {
            MobEffectInstance existing = player.getEffect(effect);
            if (existing == null || existing.getAmplifier() < 2) {
                player.addEffect(new MobEffectInstance(effect, Integer.MAX_VALUE / 2, 2, false, true));
            }
            // Already has tier III — don't shorten it
        } else {
            MobEffectInstance existing = player.getEffect(effect);
            if (existing == null || existing.getAmplifier() < amplifier) {
                player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, amplifier, false, true));
            } else {
                // Refresh duration without changing amplifier
                player.addEffect(new MobEffectInstance(effect,
                        Math.max(existing.getDuration(), EFFECT_DURATION), existing.getAmplifier(), false, true));
            }
        }
    }

    private static void applyHealthReduction(ServerLevel level, ServerPlayer player,
                                              int amplifier, long gameTime,
                                              RadiationSavedData data) {
        if (amplifier < 1) return; // Radiation I has no health reduction

        UUID uuid = player.getUUID();
        RadiationSavedData.PlayerRadData pdata = data.getOrCreate(uuid);

        long cooldown = (amplifier >= 2) ? REDUCE_COOLDOWN_III : REDUCE_COOLDOWN_II;
        if (pdata.lastHealthDmgTick >= 0 && gameTime - pdata.lastHealthDmgTick < cooldown) return;

        pdata.lastHealthDmgTick = gameTime;
        pdata.totalReduction   += 1;
        data.setDirty();

        // Re-apply the cumulative max-health modifier
        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(HEALTH_DAMAGE_ID);
            double reduction = -pdata.totalReduction;
            // Clamp so player always has at least 2 hp (1 heart) max health
            double baseHealth = healthAttr.getBaseValue();
            if (baseHealth + reduction < 2.0) reduction = 2.0 - baseHealth;
            if (reduction < 0) {
                healthAttr.addPermanentModifier(
                        new AttributeModifier(HEALTH_DAMAGE_ID, reduction, AttributeModifier.Operation.ADD_VALUE));
                // Clamp current health to new max
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
        }
    }

    /** Prevent milk and explicit removeEffect() from clearing radiation effects. */
    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (event.getEffect().is(OmniTechMobEffects.RADIATION)) {
            event.setCanceled(true);
        }
    }

    /** On player death: clear radiation health reduction data and remove the MAX_HEALTH modifier. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        ServerLevel level = (ServerLevel) player.level();
        RadiationSavedData data = RadiationSavedData.get(level);
        data.removePlayer(uuid);

        // Remove max-health modifier directly (bypasses the Remove event cancellation)
        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) healthAttr.removeModifier(HEALTH_DAMAGE_ID);

        // Remove Radiation III (permanent) directly — bypasses Remove event
        player.removeEffectNoUpdate(OmniTechMobEffects.RADIATION);
    }
}
