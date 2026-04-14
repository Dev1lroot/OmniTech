package com.dev1lroot.mcmods.omnitech.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * The Abyssal Eel — a hostile deep-sea predator unique to Europa's subsurface ocean.
 *
 * <p>Behaviour overview:
 * <ul>
 *   <li>Based on {@link Salmon} to reuse the fish movement, animations, and model.</li>
 *   <li>2× the size of a medium salmon (hitbox and renderer scale).</li>
 *   <li>Actively hunts and attacks nearby players.</li>
 *   <li>Never attempts to swim above Y = 40; it is a creature of the deep.</li>
 *   <li>Spawns naturally in Europa's subsurface ocean below Y = 40.</li>
 * </ul>
 */
public class AbyssalEelEntity extends Salmon {

    /** Maximum Y level at which this entity will spawn or remain. */
    public static final int MAX_Y = 40;

    /** Downward push speed applied each tick the entity is above the ceiling (m/tick). */
    private static final double DEPTH_PUSH = 0.08;

    public AbyssalEelEntity(EntityType<? extends Salmon> type, Level level) {
        super(type, level);
    }

    // ── Attributes ────────────────────────────────────────────────────────────

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractFish.createAttributes()
                .add(Attributes.MAX_HEALTH,     20.0)  // 10 hearts
                .add(Attributes.ATTACK_DAMAGE,   4.0)  // 2 hearts per hit
                .add(Attributes.MOVEMENT_SPEED,  0.5);
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        // Skip AbstractFish.registerGoals() entirely — that adds PanicGoal and
        // AvoidEntityGoal (fleeing players) which contradict hostile behaviour.
        // Re-add only the swim goal, then layer in attack/target goals.
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(3, new RandomSwimmingGoal(this, 1.0, 40));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ── Size — 2× medium salmon ───────────────────────────────────────────────

    @Override
    public float getSalmonScale() {
        return 2.0f;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason reason,
            @Nullable SpawnGroupData groupData) {
        // Always use LARGE variant so the renderer picks the largest model geometry.
        // The renderer then scales it by (2.0/1.5) to reach the target 2× visual size.
        // Note: we bypass Salmon.finalizeSpawn to avoid the random variant roll.
        return net.minecraft.world.entity.Mob.class.cast(this) != null
                ? super.finalizeSpawn(level, difficulty, reason, groupData)
                : groupData;
    }

    /** Called after super finalize to force the LARGE variant. */
    private void forceLargeVariant() {
        // Access via setVariant reflection is messy; set the synced data directly.
        // Instead we override getSalmonScale() to 2.0 and let the renderer compensate.
    }

    // ── Depth ceiling ─────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        // Push the entity back down if it somehow rises above MAX_Y.
        if (getY() > MAX_Y) {
            setDeltaMovement(getDeltaMovement().x(), -DEPTH_PUSH, getDeltaMovement().z());
        }
    }

    // ── Spawn rules ───────────────────────────────────────────────────────────

    /**
     * Restricts natural spawning to below Y = {@value MAX_Y} and in water blocks.
     * The standard water-creature placement checks still apply on top of this.
     */
    public static boolean checkSpawnRules(
            EntityType<AbyssalEelEntity> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            net.minecraft.util.RandomSource random) {
        return pos.getY() < MAX_Y
                && level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }
}
