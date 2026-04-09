package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.gui.RocketMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class RocketEntity extends Entity implements MenuProvider {

    public static final int MAX_FUEL = 16000;
    /** Fuel consumed per tick when thrusting (in mB). */
    public static final int FUEL_PER_TICK = 10;

    private static final EntityDataAccessor<Integer> FUEL =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);

    private final SimpleContainer inventory = new SimpleContainer(2);

    public RocketEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUEL, 0);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false; // Rocket is indestructible
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            processBucketSlot();

            boolean thrusting = false;
            for (Entity passenger : getPassengers()) {
                if (passenger instanceof ServerPlayer sp && sp.getLastClientInput().jump()) {
                    thrusting = true;
                    break;
                }
            }

            int fuel = getEntityData().get(FUEL);
            Vec3 motion = getDeltaMovement();

            if (thrusting && fuel > 0) {
                double newY = Math.min(motion.y + 0.18, 0.8);
                setDeltaMovement(motion.x * 0.9, newY, motion.z * 0.9);
                fuel = Math.max(0, fuel - FUEL_PER_TICK);
                getEntityData().set(FUEL, fuel);
            } else {
                double newY = Math.max(motion.y - 0.08, -3.0);
                setDeltaMovement(motion.x * 0.9, newY, motion.z * 0.9);
            }

            move(MoverType.SELF, getDeltaMovement());

            if (onGround()) {
                setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private void processBucketSlot() {
        ItemStack input = inventory.getItem(0);
        int fuel = getEntityData().get(FUEL);

        if (input.is(Items.WATER_BUCKET) && fuel + 1000 <= MAX_FUEL) {
            ItemStack output = inventory.getItem(1);
            if (output.isEmpty()) {
                inventory.setItem(1, new ItemStack(Items.BUCKET));
                inventory.setItem(0, ItemStack.EMPTY);
                getEntityData().set(FUEL, fuel + 1000);
            } else if (output.is(Items.BUCKET) && output.getCount() < output.getMaxStackSize()) {
                output.grow(1);
                inventory.setItem(0, ItemStack.EMPTY);
                getEntityData().set(FUEL, fuel + 1000);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.getVehicle() == this) {
            // Already riding — right-click opens GUI
            if (!level().isClientSide() && player instanceof ServerPlayer sp) {
                sp.openMenu(this, buf -> buf.writeInt(getId()));
            }
            return InteractionResult.SUCCESS_SERVER;
        } else {
            // Not riding — right-click mounts
            if (!level().isClientSide() && !hasPassenger(player)) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("fuel", getEntityData().get(FUEL));
        ItemStack slot0 = inventory.getItem(0);
        ItemStack slot1 = inventory.getItem(1);
        if (!slot0.isEmpty()) output.store("slot0", ItemStack.CODEC, slot0);
        if (!slot1.isEmpty()) output.store("slot1", ItemStack.CODEC, slot1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        getEntityData().set(FUEL, input.getIntOr("fuel", 0));
        input.read("slot0", ItemStack.CODEC).ifPresent(s -> inventory.setItem(0, s));
        input.read("slot1", ItemStack.CODEC).ifPresent(s -> inventory.setItem(1, s));
    }

    public int getFuelAmount() {
        return getEntityData().get(FUEL);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    // ── MenuProvider ─────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.omnitech.rocket");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new RocketMenu(id, playerInventory, this, new SimpleContainerData(2));
    }
}
