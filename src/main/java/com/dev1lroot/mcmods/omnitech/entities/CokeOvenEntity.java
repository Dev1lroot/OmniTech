package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.blocks.CokeBrickBlock;
import com.dev1lroot.mcmods.omnitech.gui.CokeOvenMenu;
import com.dev1lroot.mcmods.omnitech.recipes.CokingRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.CokingRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Optional;

public class CokeOvenEntity extends Entity implements MenuProvider {

    // ── Synced data ───────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Boolean> LIT =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PROGRESS =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PROGRESS_MAX =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Direction> FACING =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.DIRECTION);

    // ── Inventory ─────────────────────────────────────────────────────────────
    // Slots 0-8:  coal input
    // Slot  9:    coal being processed (locked, no external access)
    // Slots 10-18: coke_coal output

    public static final int SLOT_INPUT_START   = 0;
    public static final int SLOT_INPUT_END     = 8;
    public static final int SLOT_ACTIVE        = 9;
    public static final int SLOT_OUTPUT_START  = 10;
    public static final int SLOT_OUTPUT_END    = 18;
    public static final int INVENTORY_SIZE     = 19;

    public static final int CREOSOTE_CAPACITY = 16_000;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private FluidStack creosoteFluid = FluidStack.EMPTY;


    // Server-only processing state
    private BlockPos cornerPos;
    private int processTimer     = 0;
    private int processTotalTime = 0;
    private int tickCount        = 0;
    private CokingRecipe currentRecipe = null;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Used by the entity type registry (deserialization). */
    public CokeOvenEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.cornerPos = BlockPos.ZERO;
    }

    /** Used when forming a new structure. */
    public CokeOvenEntity(Level level, BlockPos corner, Direction facing) {
        this(OmniTechEntities.COKE_OVEN.get(), level);
        this.cornerPos = corner;
        getEntityData().set(FACING, facing);
        // Position: center of bottom face of the 3x3x3 structure
        this.setPos(corner.getX() + 1.5, corner.getY(), corner.getZ() + 1.5);
    }

    // ── Entity overrides ──────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LIT, false);
        builder.define(PROGRESS, 0);
        builder.define(PROGRESS_MAX, 100);
        builder.define(FACING, Direction.NORTH);
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        // Sample light from the air block just outside the south face center, not from inside the solid structure
        return new Vec3(getX(), getY() + 1.5, getZ() + 1.5);
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isNoGravity() { return true; }

    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        return new AABB(pos.x - 1.5, pos.y, pos.z - 1.5, pos.x + 1.5, pos.y + 3.0, pos.z + 1.5);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        tickCount++;

        // Validate structure every 20 ticks
        if (tickCount % 20 == 0 && !validateStructure()) {
            onStructureBroken();
            return;
        }

        // Process recipe
        tickProcessing();

        // Transfer items/fluids every 4 ticks
        if (tickCount % 4 == 0) {
            pushOutputs();
        }
        if (tickCount % 4 == 2) {
            pullInputs();
        }
    }

    private void tickProcessing() {
        // If no active coal, try to pull one from input
        if (inventory.getItem(SLOT_ACTIVE).isEmpty()) {
            for (int i = SLOT_INPUT_START; i <= SLOT_INPUT_END; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    Optional<CokingRecipe> recipe = CokingRecipeManager.findRecipe(stack);
                    if (recipe.isPresent()) {
                        currentRecipe = recipe.get();
                        inventory.setItem(SLOT_ACTIVE, stack.copyWithCount(1));
                        stack.shrink(1);
                        inventory.setItem(i, stack);
                        processTimer = 0;
                        processTotalTime = currentRecipe.getProductionTime();
                        getEntityData().set(PROGRESS_MAX, processTotalTime);
                        getEntityData().set(LIT, true);
                        break;
                    }
                }
            }
        }

        // Advance processing
        if (!inventory.getItem(SLOT_ACTIVE).isEmpty() && currentRecipe != null) {
            processTimer++;
            getEntityData().set(PROGRESS, processTimer);

            if (processTimer >= processTotalTime) {
                finishProcessing();
            }
        } else {
            getEntityData().set(LIT, false);
            getEntityData().set(PROGRESS, 0);
        }
    }

    private void finishProcessing() {
        if (currentRecipe == null) return;

        // Produce coke coal in output slots
        ItemStack output = currentRecipe.getOutput();
        if (!output.isEmpty()) {
            insertIntoOutputSlots(output);
        }

        // Produce creosote
        int creosoteToAdd = currentRecipe.getCreosoteAmount();
        var creosoteFluidType = OmniTechFluids.get("creosote");
        if (creosoteFluidType != null && creosoteToAdd > 0) {
            int space = CREOSOTE_CAPACITY - creosoteFluid.getAmount();
            int toAdd = Math.min(creosoteToAdd, space);
            if (toAdd > 0) {
                if (creosoteFluid.isEmpty()) {
                    creosoteFluid = new FluidStack(creosoteFluidType.source.get(), toAdd);
                } else {
                    creosoteFluid.grow(toAdd);
                }
            }
        }

        // Clear active slot
        inventory.setItem(SLOT_ACTIVE, ItemStack.EMPTY);
        processTimer = 0;
        currentRecipe = null;
        getEntityData().set(LIT, false);
        getEntityData().set(PROGRESS, 0);
    }

    private void insertIntoOutputSlots(ItemStack output) {
        ItemStack remaining = output.copy();
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END && !remaining.isEmpty(); i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing.isEmpty()) {
                inventory.setItem(i, remaining.copy());
                remaining.setCount(0);
            } else if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int toAdd = Math.min(space, remaining.getCount());
                existing.grow(toAdd);
                remaining.shrink(toAdd);
            }
        }
    }

    // ── Push/pull via adjacent block capabilities ─────────────────────────────

    private void pushOutputs() {
        if (level() == null) return;
        var fluidEntry = OmniTechFluids.get("creosote");

        forEachOuterFace((outerPos, faceDir) -> {
            // Push creosote fluid
            if (fluidEntry != null && !creosoteFluid.isEmpty()) {
                var fluidHandler = level().getCapability(Capabilities.Fluid.BLOCK, outerPos, faceDir);
                if (fluidHandler != null) {
                    tryPushFluid(fluidHandler);
                }
            }
            // Push coke_coal items
            var itemHandler = level().getCapability(Capabilities.Item.BLOCK, outerPos, faceDir);
            if (itemHandler != null) {
                pushItemsTo(itemHandler);
            }
        });
    }

    private void pullInputs() {
        if (level() == null) return;
        int totalInput = countInputItems();
        if (totalInput >= (SLOT_INPUT_END - SLOT_INPUT_START + 1) * 64) return; // input full

        forEachOuterFace((outerPos, faceDir) -> {
            var itemHandler = level().getCapability(Capabilities.Item.BLOCK, outerPos, faceDir);
            if (itemHandler != null) {
                pullCoalFrom(itemHandler);
            }
        });
    }

    private int countInputItems() {
        int count = 0;
        for (int i = SLOT_INPUT_START; i <= SLOT_INPUT_END; i++) {
            count += inventory.getItem(i).getCount();
        }
        return count;
    }

    @FunctionalInterface
    private interface OuterFaceConsumer {
        void accept(BlockPos outerPos, Direction faceDir);
    }

    private void forEachOuterFace(OuterFaceConsumer consumer) {
        if (cornerPos == null) return;
        // Top and bottom faces (y = cornerY+3 and cornerY-1)
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                consumer.accept(cornerPos.offset(dx, 3, dz), Direction.DOWN);
                consumer.accept(cornerPos.offset(dx, -1, dz), Direction.UP);
            }
        }
        // North/South faces
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                consumer.accept(cornerPos.offset(dx, dy, -1), Direction.SOUTH);
                consumer.accept(cornerPos.offset(dx, dy, 3), Direction.NORTH);
            }
        }
        // East/West faces
        for (int dy = 0; dy < 3; dy++) {
            for (int dz = 0; dz < 3; dz++) {
                consumer.accept(cornerPos.offset(-1, dy, dz), Direction.EAST);
                consumer.accept(cornerPos.offset(3, dy, dz), Direction.WEST);
            }
        }
    }

    private void tryPushFluid(ResourceHandler<FluidResource> target) {
        if (creosoteFluid.isEmpty()) return;
        try (var tx = Transaction.openRoot()) {
            FluidResource res = FluidResource.of(creosoteFluid);
            int toSend = Math.min(1000, creosoteFluid.getAmount());
            int accepted = target.insert(res, toSend, tx);
            if (accepted > 0) {
                creosoteFluid.shrink(accepted);
                if (creosoteFluid.getAmount() <= 0) creosoteFluid = FluidStack.EMPTY;
                tx.commit();
            }
        }
    }

    private void pushItemsTo(ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> target) {
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            try (var tx = Transaction.openRoot()) {
                var res = net.neoforged.neoforge.transfer.item.ItemResource.of(stack);
                int toSend = Math.min(stack.getCount(), stack.getMaxStackSize());
                int accepted = target.insert(res, toSend, tx);
                if (accepted > 0) {
                    stack.shrink(accepted);
                    inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
                    tx.commit();
                    break; // one push per tick
                }
            }
        }
    }

    private void pullCoalFrom(ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> source) {
        // Find an empty or compatible input slot
        int targetSlot = -1;
        for (int i = SLOT_INPUT_START; i <= SLOT_INPUT_END; i++) {
            if (inventory.getItem(i).isEmpty()) { targetSlot = i; break; }
        }
        if (targetSlot == -1) return;

        // Try to extract one coal from the source
        for (int si = 0; si < source.size(); si++) {
            var res = source.getResource(si);
            if (res.isEmpty()) continue;
            // Check if this item is coal (by testing against known coal items)
            ItemStack testStack = res.toStack(1);
            if (!isCoal(testStack)) continue;

            try (var tx = Transaction.openRoot()) {
                int amount = Math.min(64, (int) source.getAmountAsLong(si));
                int extracted = source.extract(res, amount, tx);
                if (extracted > 0) {
                    // Also check against existing stacks
                    boolean placed = false;
                    for (int ii = SLOT_INPUT_START; ii <= SLOT_INPUT_END; ii++) {
                        ItemStack existing = inventory.getItem(ii);
                        if (existing.isEmpty()) {
                            inventory.setItem(ii, res.toStack(extracted));
                            placed = true;
                            break;
                        } else if (ItemStack.isSameItemSameComponents(existing, testStack)) {
                            int space = existing.getMaxStackSize() - existing.getCount();
                            if (space > 0) {
                                int toAdd = Math.min(space, extracted);
                                existing.grow(toAdd);
                                placed = true;
                                break;
                            }
                        }
                    }
                    if (placed) { tx.commit(); return; }
                }
            }
        }
    }

    public static boolean isCoal(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    // ── Structure validation ──────────────────────────────────────────────────

    public boolean validateStructure() {
        if (level() == null || cornerPos == null) return false;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && y == 1 && z == 1) continue;
                    BlockPos p = cornerPos.offset(x, y, z);
                    BlockState bs = level().getBlockState(p);
                    if (!(bs.getBlock() instanceof CokeBrickBlock)) return false;
                    if (!bs.getValue(CokeBrickBlock.FORMED)) return false;
                }
            }
        }
        return true;
    }

    public boolean isStructurePos(BlockPos pos) {
        if (cornerPos == null) return false;
        int rx = pos.getX() - cornerPos.getX();
        int ry = pos.getY() - cornerPos.getY();
        int rz = pos.getZ() - cornerPos.getZ();
        if (rx < 0 || rx > 2 || ry < 0 || ry > 2 || rz < 0 || rz > 2) return false;
        return !(rx == 1 && ry == 1 && rz == 1); // exclude hollow center
    }

    public void onStructureBroken() {
        if (level() == null) return;
        BlockPos center = cornerPos.offset(1, 1, 1);

        // Drop all inventory items
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                Block.popResource(level(), center, stack);
            }
        }

        // Set all remaining bricks back to FORMED=false
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && y == 1 && z == 1) continue;
                    BlockPos p = cornerPos.offset(x, y, z);
                    BlockState bs = level().getBlockState(p);
                    if (bs.getBlock() instanceof CokeBrickBlock && bs.getValue(CokeBrickBlock.FORMED)) {
                        level().setBlock(p, bs.setValue(CokeBrickBlock.FORMED, false), Block.UPDATE_ALL);
                    }
                }
            }
        }

        this.discard();
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.coke_oven");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new CokeOvenMenu(id, inv, this);
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(this, buf -> buf.writeInt(this.getId()));
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public SimpleContainer getInventory() { return inventory; }
    public FluidStack getCreosoteFluid()  { return creosoteFluid; }
    public BlockPos getCornerPos()        { return cornerPos; }
    public Direction getFacing()          { return getEntityData().get(FACING); }
    public boolean isLit()                { return getEntityData().get(LIT); }
    public int getProcessTimer()          { return getEntityData().get(PROGRESS); }
    public int getProcessTotalTime()      { return getEntityData().get(PROGRESS_MAX); }

    public float getProgressFraction() {
        int total = getEntityData().get(PROGRESS_MAX);
        if (total <= 0) return 0f;
        return (float) getEntityData().get(PROGRESS) / total;
    }

    // ── Save / Load ───────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("cornerX", cornerPos.getX());
        output.putInt("cornerY", cornerPos.getY());
        output.putInt("cornerZ", cornerPos.getZ());
        output.putString("facing", getEntityData().get(FACING).getSerializedName());
        output.putInt("processTimer", processTimer);
        output.putInt("processTotalTime", processTotalTime);

        output.store("creosote", FluidStack.OPTIONAL_CODEC, creosoteFluid);

        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                output.store("item_" + i, ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        int cx = input.getIntOr("cornerX", 0);
        int cy = input.getIntOr("cornerY", 0);
        int cz = input.getIntOr("cornerZ", 0);
        cornerPos = new BlockPos(cx, cy, cz);
        Direction facing = Direction.byName(input.getStringOr("facing", "north"));
        getEntityData().set(FACING, facing != null ? facing : Direction.NORTH);
        processTimer = input.getIntOr("processTimer", 0);
        processTotalTime = input.getIntOr("processTotalTime", 0);

        creosoteFluid = input.read("creosote", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);

        for (int i = 0; i < INVENTORY_SIZE; i++) {
            final int slot = i;
            input.read("item_" + i, ItemStack.CODEC).ifPresent(s -> inventory.setItem(slot, s));
        }

        // Restore synced data from loaded state
        getEntityData().set(PROGRESS, processTimer);
        getEntityData().set(PROGRESS_MAX, processTotalTime > 0 ? processTotalTime : 100);
    }
}
