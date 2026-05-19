/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.List;

/**
 * Penguin — a friendly, flocking animal that lives on ice in cold biomes.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Wanders on land, preferring to stay near other penguins (flocking).</li>
 *   <li>Hunts {@link AbstractFish} in water using amphibious pathfinding.</li>
 *   <li>Swim duration is capped at 1 minute; afterwards a 5-minute cooldown
 *       prevents re-entry into water (per the natural penguin foraging cycle).</li>
 *   <li>Tempted by raw cod and salmon held by players.</li>
 *   <li>Spawns naturally on ice-type blocks in cold biomes.</li>
 * </ul>
 *
 * <p>Extends {@link Chicken} to reuse its biped model and animations.
 * Egg-laying is suppressed by keeping {@code eggTime} pinned to
 * {@link Integer#MAX_VALUE} each tick.
 */
public class PenguinEntity extends Chicken {

    private static final int MAX_SWIM_TICKS     = 1200;  // 1 minute
    private static final int SWIM_COOLDOWN_TICKS = 6000; // 5 minutes
    private static final double EXIT_WATER_SPEED = 0.18;

    private long swimEnterTick   = -1;
    private long noSwimUntilTick = 0;

    public PenguinEntity(EntityType<? extends Chicken> type, Level level) {
        super(type, level);
        this.eggTime = Integer.MAX_VALUE;
        // Smooth turning and vertical pitch in water; full-speed land movement
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.4f, 1.0f, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 20);
    }

    // ── Attributes ────────────────────────────────────────────────────────────

    public static AttributeSupplier.Builder createAttributes() {
        return Chicken.createAttributes()
                .add(Attributes.MAX_HEALTH,    8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE,  2.0);
    }

    // ── Navigation — amphibious so it can path through water to hunt fish ──────

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        // === Water mode — axolotl-like ===
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true) {
            @Override public boolean canUse()           { return isInWater() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isInWater() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 40) {
            @Override public boolean canUse()           { return isInWater() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isInWater() && super.canContinueToUse(); }
        });

        // === Land mode — chicken-like ===
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.1,
                stack -> stack.is(Items.COD) || stack.is(Items.SALMON), false));
        this.goalSelector.addGoal(3, new PenguinFollowFlockGoal(this, 1.0, 16.0f, 5.0f));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return !isInWater() && super.canUse(); }
        });
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // === Targets ===
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, AbstractFish.class, true) {
            @Override public boolean canUse() { return isInWater() && canSwimNow() && super.canUse(); }
        });
    }

    // ── Swim timer ────────────────────────────────────────────────────────────

    /** {@code true} when the swimming cooldown has expired and the penguin may enter water. */
    public boolean canSwimNow() {
        return level().getGameTime() >= noSwimUntilTick;
    }

    @Override
    public void tick() {
        super.tick();
        // Suppress the inherited chicken egg-laying logic
        this.eggTime = Integer.MAX_VALUE;

        if (level().isClientSide()) return;

        long t = level().getGameTime();

        if (isInWater()) {
            if (swimEnterTick < 0) swimEnterTick = t;
            if (t - swimEnterTick > MAX_SWIM_TICKS) {
                // Penguin has been swimming too long — push it toward the surface
                noSwimUntilTick = t + SWIM_COOLDOWN_TICKS;
                swimEnterTick   = -1;
                setDeltaMovement(getDeltaMovement().x(), EXIT_WATER_SPEED, getDeltaMovement().z());
                getNavigation().stop();
            }
        } else {
            if (swimEnterTick >= 0) {
                // Just left water voluntarily — start the cooldown
                noSwimUntilTick = t + SWIM_COOLDOWN_TICKS;
                swimEnterTick   = -1;
            }
        }
    }

    // ── Spawn rules ───────────────────────────────────────────────────────────

    /**
     * Penguins spawn on top of any ice-type or snow block variant.
     * The biome filter (cold biomes) is handled separately via the NeoForge biome modifier.
     */
    public static boolean checkSpawnRules(
            EntityType<PenguinEntity> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        BlockPos below = pos.below();
        var ground = level.getBlockState(below);
        return (ground.is(Blocks.ICE)
                || ground.is(Blocks.PACKED_ICE)
                || ground.is(Blocks.BLUE_ICE)
                || ground.is(Blocks.FROSTED_ICE)
                || ground.is(Blocks.SNOW_BLOCK))
                && level.getRawBrightness(pos, 0) > 0;
    }

    // ── Flock goal ────────────────────────────────────────────────────────────

    /**
     * Steers the penguin toward the nearest same-species neighbour when
     * it drifts too far apart.  Acts as a simple flocking / schooling
     * behaviour without a designated leader.
     */
    static final class PenguinFollowFlockGoal extends Goal {

        private final PenguinEntity self;
        private final double        speed;
        private final float         lookRange;
        private final float         closeEnough;
        private PenguinEntity       leader;

        PenguinFollowFlockGoal(PenguinEntity self, double speed, float lookRange, float closeEnough) {
            this.self        = self;
            this.speed       = speed;
            this.lookRange   = lookRange;
            this.closeEnough = closeEnough;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (self.isInWater()) return false;
            List<PenguinEntity> neighbours = self.level().getEntitiesOfClass(
                    PenguinEntity.class,
                    self.getBoundingBox().inflate(lookRange),
                    e -> e != self && e.isAlive());
            if (neighbours.isEmpty()) return false;
            leader = neighbours.get(0);
            return self.distanceToSqr(leader) > (double) (closeEnough * closeEnough);
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null
                    && leader.isAlive()
                    && !leader.isRemoved()
                    && self.distanceToSqr(leader) > (double) (closeEnough * closeEnough);
        }

        @Override
        public void start() {
            self.getNavigation().moveTo(leader, speed);
        }

        @Override
        public void tick() {
            if (leader != null && leader.isAlive())
                self.getNavigation().moveTo(leader, speed);
        }

        @Override
        public void stop() {
            leader = null;
            self.getNavigation().stop();
        }
    }
}
