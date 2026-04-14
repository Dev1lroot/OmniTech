package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.gui.RocketMenu;
import com.dev1lroot.mcmods.omnitech.network.RocketOrbitPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class RocketEntity extends Entity implements MenuProvider {

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    public enum RocketState {
        /** On the ground, waiting for launch input. */
        IDLE,
        /** 10-second irrevocable countdown before liftoff. */
        COUNTDOWN,
        /** Thrusting upward toward orbit altitude (Y ≥ 400). */
        ASCENDING,
        /** Frozen at orbit altitude; player selects destination. */
        ORBIT,
        /** Entered target dimension at Y = 300; slow cinematic descent. */
        DESCENDING;

        public static RocketState fromId(int id) {
            RocketState[] v = values();
            return (id >= 0 && id < v.length) ? v[id] : IDLE;
        }

        public int id() { return ordinal(); }
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final int MAX_FUEL       = 16_000;
    public static final int FUEL_PER_TICK  = 10;          // mB/tick during ascent

    /** Altitude (Y) at which the rocket enters orbit and freezes. */
    private static final double ORBIT_ALTITUDE    = 400.0;
    /** Arrival altitude in the target dimension after space travel. */
    public  static final double ARRIVAL_ALTITUDE  = 300.0;
    /** Maximum downward speed during the slow descent after arrival. */
    private static final double DESCENT_MAX_FALL = 0.5;
    /** Countdown duration in ticks (10 seconds × 20 ticks/s). */
    private static final int    COUNTDOWN_TICKS  = 200;

    // -------------------------------------------------------------------------
    // Synced data
    // -------------------------------------------------------------------------

    private static final EntityDataAccessor<Integer> FUEL =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);

    // -------------------------------------------------------------------------
    // Server-only fields (not synced; saved to NBT)
    // -------------------------------------------------------------------------

    /** Ticks elapsed in the current state (used for countdown). */
    private int stateTicks = 0;
    /** Saved orbit position — rocket is frozen here during ORBIT. */
    private double orbitX, orbitY, orbitZ;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final SimpleContainer inventory = new SimpleContainer(2);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public RocketEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUEL,  0);
        builder.define(STATE, RocketState.IDLE.id());
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            interpolation.interpolate();
            return;
        }

        processBucketSlot();

        switch (getState()) {
            case IDLE       -> tickIdle();
            case COUNTDOWN  -> tickCountdown();
            case ASCENDING  -> tickAscending();
            case ORBIT      -> tickOrbit();
            case DESCENDING -> tickDescending();
        }
    }

    // ── IDLE ─────────────────────────────────────────────────────────────────

    private void tickIdle() {
        Vec3 motion = getDeltaMovement();
        if (!onGround()) {
            setDeltaMovement(motion.x, Math.max(motion.y - 0.04, -2.0), motion.z);
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
        }

        // Launch only when on the ground and a player presses jump.
        if (onGround() && jumpPressed()) {
            setState(RocketState.COUNTDOWN);
            stateTicks = 0;
            sendCountdownTitle(10); // show "10" on first tick immediately
        }
    }

    // ── COUNTDOWN ────────────────────────────────────────────────────────────

    private void tickCountdown() {
        // Rocket stays on the ground; movement locked.
        setDeltaMovement(Vec3.ZERO);

        // Show countdown titles once per second.
        if (stateTicks % 20 == 0) {
            int secondsLeft = 10 - stateTicks / 20;
            if (secondsLeft > 0) sendCountdownTitle(secondsLeft);
        }

        stateTicks++;

        if (stateTicks >= COUNTDOWN_TICKS) {
            setState(RocketState.ASCENDING);
            stateTicks = 0;
        }
    }

    // ── ASCENDING ────────────────────────────────────────────────────────────

    private void tickAscending() {
        int fuel  = getFuelAmount();
        Vec3 motion = getDeltaMovement();

        if (fuel > 0) {
            double newY = Math.min(motion.y - 0.04 + 0.15, 0.8);
            setDeltaMovement(motion.x, newY, motion.z);
            setFuelAmount(fuel - FUEL_PER_TICK);
        } else {
            // Out of fuel — fall back to ground.
            double newY = Math.max(motion.y - 0.04, -2.0);
            setDeltaMovement(motion.x, newY, motion.z);
        }

        move(MoverType.SELF, getDeltaMovement());

        if (onGround()) {
            setDeltaMovement(Vec3.ZERO);
            setState(RocketState.IDLE);
            return;
        }

        if (getY() >= ORBIT_ALTITUDE) {
            // Reached orbit — freeze and notify the rider.
            orbitX = getX();
            orbitY = getY();
            orbitZ = getZ();
            setDeltaMovement(Vec3.ZERO);
            setState(RocketState.ORBIT);
            notifyRiderOrbit();
        }
    }

    // ── ORBIT ─────────────────────────────────────────────────────────────────

    private void tickOrbit() {
        // Hard-freeze — zero velocity, do not call move().
        setDeltaMovement(Vec3.ZERO);

        // If the rider dismounts in orbit, start falling back down.
        if (getPassengers().isEmpty()) {
            setState(RocketState.DESCENDING);
        }
    }

    // ── DESCENDING ───────────────────────────────────────────────────────────

    private void tickDescending() {
        Vec3 motion = getDeltaMovement();
        double newY = motion.y - 0.04;
        if (newY < -DESCENT_MAX_FALL) newY = -DESCENT_MAX_FALL;
        setDeltaMovement(motion.x, newY, motion.z);
        move(MoverType.SELF, getDeltaMovement());

        if (onGround()) {
            setDeltaMovement(Vec3.ZERO);
            setState(RocketState.IDLE);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean jumpPressed() {
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof ServerPlayer sp && sp.getLastClientInput().jump())
                return true;
        }
        return false;
    }

    private void notifyRiderOrbit() {
        for (Entity e : getPassengers()) {
            if (e instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new RocketOrbitPacket());
            }
        }
    }

    private void sendCountdownTitle(int seconds) {
        int color = seconds <= 3 ? 0xFF4444 : (seconds <= 6 ? 0xFFAA00 : 0xFFFFFF);
        Component title = Component.literal(String.valueOf(seconds))
                .withStyle(Style.EMPTY.withColor(color).withBold(true));
        Component sub = Component.literal("Launch sequence initiated — brace for liftoff")
                .withStyle(Style.EMPTY.withColor(0xAAAAAA));

        for (Entity e : getPassengers()) {
            if (e instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 2));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
                sp.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fuel bucket slot
    // -------------------------------------------------------------------------

    private void processBucketSlot() {
        ItemStack input = inventory.getItem(0);
        int fuel = getFuelAmount();

        if (input.is(Items.WATER_BUCKET) && fuel + 1000 <= MAX_FUEL) {
            ItemStack output = inventory.getItem(1);
            if (output.isEmpty()) {
                inventory.setItem(1, new ItemStack(Items.BUCKET));
                inventory.setItem(0, ItemStack.EMPTY);
                setFuelAmount(fuel + 1000);
            } else if (output.is(Items.BUCKET) && output.getCount() < output.getMaxStackSize()) {
                output.grow(1);
                inventory.setItem(0, ItemStack.EMPTY);
                setFuelAmount(fuel + 1000);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Entity overrides
    // -------------------------------------------------------------------------

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }

    @Override
    public InterpolationHandler getInterpolation() { return interpolation; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.getVehicle() == this) {
            if (!level().isClientSide() && player instanceof ServerPlayer sp) {
                // Only open inventory in IDLE/DESCENDING; in ORBIT the space map handles things.
                RocketState s = getState();
                if (s == RocketState.IDLE || s == RocketState.DESCENDING) {
                    sp.openMenu(this, buf -> buf.writeInt(getId()));
                }
            }
            return InteractionResult.SUCCESS_SERVER;
        } else {
            if (!level().isClientSide() && !hasPassenger(player)) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("fuel",        getFuelAmount());
        output.putInt("rocketState", getEntityData().get(STATE));
        output.putDouble("orbitX",   orbitX);
        output.putDouble("orbitY",   orbitY);
        output.putDouble("orbitZ",   orbitZ);

        ItemStack slot0 = inventory.getItem(0);
        ItemStack slot1 = inventory.getItem(1);
        if (!slot0.isEmpty()) output.store("slot0", ItemStack.CODEC, slot0);
        if (!slot1.isEmpty()) output.store("slot1", ItemStack.CODEC, slot1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setFuelAmount(input.getIntOr("fuel", 0));
        getEntityData().set(STATE, input.getIntOr("rocketState", RocketState.IDLE.id()));
        orbitX = input.getDoubleOr("orbitX", 0.0);
        orbitY = input.getDoubleOr("orbitY", 0.0);
        orbitZ = input.getDoubleOr("orbitZ", 0.0);
        input.read("slot0", ItemStack.CODEC).ifPresent(s -> inventory.setItem(0, s));
        input.read("slot1", ItemStack.CODEC).ifPresent(s -> inventory.setItem(1, s));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public int getFuelAmount() {
        return getEntityData().get(FUEL);
    }

    public void setFuelAmount(int amount) {
        getEntityData().set(FUEL, Math.max(0, Math.min(MAX_FUEL, amount)));
    }

    public RocketState getState() {
        return RocketState.fromId(getEntityData().get(STATE));
    }

    public void setState(RocketState state) {
        getEntityData().set(STATE, state.id());
    }

    public SimpleContainer getInventory() { return inventory; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.omnitech.rocket");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new RocketMenu(id, playerInventory, this, new SimpleContainerData(2));
    }
}
