package com.dev1lroot.mcmods.omnitech.entities;

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

public class CokeOvenEntity extends Entity implements MenuProvider {

    // ── Synced data ───────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Boolean>   LIT          =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer>   PROGRESS     =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>   PROGRESS_MAX =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Direction> FACING       =
            SynchedEntityData.defineId(CokeOvenEntity.class, EntityDataSerializers.DIRECTION);

    // ── Inventory ─────────────────────────────────────────────────────────────
    // Slot 0 (input): coal/charcoal to be converted into coke coal
    // Slot 1 (fuel):  furnace fuel that heats the oven; consumed for burn time
    // Slot 2 (output): coke coal result

    public static final int SLOT_INPUT    = 0;
    public static final int SLOT_FUEL     = 1;
    public static final int SLOT_OUTPUT   = 2;
    public static final int INVENTORY_SIZE = 3;

    public static final int CREOSOTE_CAPACITY = 16_000;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private FluidStack creosoteFluid = FluidStack.EMPTY;

    // Server-only processing state
    private BlockPos cornerPos;
    private int processTimer     = 0;
    private int processTotalTime = 0;
    private int fuelBurnTime     = 0;  // ticks remaining from last fuel piece
    private int tickCount        = 0;
    private CokingRecipe currentRecipe = null;

    // ── Constructors ──────────────────────────────────────────────────────────

    public CokeOvenEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.cornerPos = BlockPos.ZERO;
    }

    public CokeOvenEntity(Level level, BlockPos corner, Direction facing) {
        this(OmniTechEntities.COKE_OVEN.get(), level);
        this.cornerPos = corner;
        getEntityData().set(FACING, facing);
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
        return new Vec3(getX(), getY() + 1.5, getZ() + 1.5);
    }

    @Override public boolean isPickable()  { return true;  }
    @Override public boolean isPushable()  { return false; }
    @Override public boolean isNoGravity() { return true;  }

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

        if (tickCount % 20 == 0 && !validateStructure()) {
            onStructureBroken();
            return;
        }

        tickProcessing();

        if (tickCount % 4 == 0) pushOutputs();
        if (tickCount % 4 == 2) {
            pullInputs();
            pullFuel();
        }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private void tickProcessing() {
        // Resolve recipe for whatever is in the input slot
        ItemStack inputStack = inventory.getItem(SLOT_INPUT);
        if (currentRecipe == null && !inputStack.isEmpty()) {
            currentRecipe = CokingRecipeManager.findRecipe(inputStack).orElse(null);
            if (currentRecipe != null) {
                processTotalTime = currentRecipe.getProductionTime();
                getEntityData().set(PROGRESS_MAX, processTotalTime);
            }
        }
        // Invalidate recipe if input is gone or changed
        if (inputStack.isEmpty() || (currentRecipe != null && !currentRecipe.matches(inputStack))) {
            currentRecipe = null;
            processTimer = 0;
            getEntityData().set(PROGRESS, 0);
        }

        boolean canProcess = currentRecipe != null
                && !inputStack.isEmpty()
                && canInsertOutput(currentRecipe.getOutput());

        // Consume one fuel piece when the fire is out and there is work to do
        if (fuelBurnTime == 0 && canProcess) {
            ItemStack fuelStack = inventory.getItem(SLOT_FUEL);
            int burnTime = getFuelBurnTime(fuelStack);
            if (burnTime > 0) {
                fuelBurnTime = burnTime;
                fuelStack.shrink(1);
                inventory.setItem(SLOT_FUEL, fuelStack.isEmpty() ? ItemStack.EMPTY : fuelStack);
            }
        }

        if (fuelBurnTime > 0) {
            fuelBurnTime--;
            if (canProcess) {
                processTimer++;
                getEntityData().set(PROGRESS, processTimer);
                if (processTimer >= processTotalTime) finishProcessing();
            }
            getEntityData().set(LIT, true);
        } else {
            getEntityData().set(LIT, false);
        }
    }

    private void finishProcessing() {
        if (currentRecipe == null) return;

        // Consume one coal from input
        ItemStack input = inventory.getItem(SLOT_INPUT);
        input.shrink(1);
        inventory.setItem(SLOT_INPUT, input.isEmpty() ? ItemStack.EMPTY : input);

        // Produce coke coal
        ItemStack output = currentRecipe.getOutput();
        if (!output.isEmpty()) insertIntoOutput(output);

        // Produce creosote
        int toAdd = currentRecipe.getCreosoteAmount();
        var creosoteType = OmniTechFluids.get("creosote");
        if (creosoteType != null && toAdd > 0) {
            int space = CREOSOTE_CAPACITY - creosoteFluid.getAmount();
            int added = Math.min(toAdd, space);
            if (added > 0) {
                if (creosoteFluid.isEmpty())
                    creosoteFluid = new FluidStack(creosoteType.source.get(), added);
                else
                    creosoteFluid.grow(added);
            }
        }

        processTimer = 0;
        currentRecipe = null;
        getEntityData().set(PROGRESS, 0);
    }

    private void insertIntoOutput(ItemStack output) {
        ItemStack existing = inventory.getItem(SLOT_OUTPUT);
        if (existing.isEmpty()) {
            inventory.setItem(SLOT_OUTPUT, output.copy());
        } else if (ItemStack.isSameItemSameComponents(existing, output)) {
            int space = existing.getMaxStackSize() - existing.getCount();
            existing.grow(Math.min(space, output.getCount()));
        }
    }

    private boolean canInsertOutput(ItemStack output) {
        if (output.isEmpty()) return false;
        ItemStack existing = inventory.getItem(SLOT_OUTPUT);
        if (existing.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(existing, output)
                && existing.getCount() < existing.getMaxStackSize();
    }

    private int getFuelBurnTime(ItemStack stack) {
        var lvl = level();
        if (lvl == null || stack.isEmpty()) return 0;
        return lvl.fuelValues().burnDuration(stack);
    }

    // ── Push / pull ───────────────────────────────────────────────────────────

    private void pushOutputs() {
        if (level() == null) return;
        var fluidEntry = OmniTechFluids.get("creosote");

        forEachOuterFace((outerPos, faceDir) -> {
            if (fluidEntry != null && !creosoteFluid.isEmpty()) {
                var fh = level().getCapability(Capabilities.Fluid.BLOCK, outerPos, faceDir);
                if (fh != null) tryPushFluid(fh);
            }
            var ih = level().getCapability(Capabilities.Item.BLOCK, outerPos, faceDir);
            if (ih != null) pushOutputItemsTo(ih);
        });
    }

    /** Pull coal into SLOT_INPUT from adjacent block handlers. */
    private void pullInputs() {
        if (level() == null) return;
        ItemStack existing = inventory.getItem(SLOT_INPUT);
        if (!existing.isEmpty() && existing.getCount() >= existing.getMaxStackSize()) return;

        forEachOuterFace((outerPos, faceDir) -> {
            var ih = level().getCapability(Capabilities.Item.BLOCK, outerPos, faceDir);
            if (ih != null) pullFilteredFrom(ih, SLOT_INPUT, CokeOvenEntity::isCoal);
        });
    }

    /** Pull furnace fuel into SLOT_FUEL from adjacent block handlers. */
    private void pullFuel() {
        if (level() == null) return;
        ItemStack existing = inventory.getItem(SLOT_FUEL);
        if (!existing.isEmpty() && existing.getCount() >= existing.getMaxStackSize()) return;

        forEachOuterFace((outerPos, faceDir) -> {
            var ih = level().getCapability(Capabilities.Item.BLOCK, outerPos, faceDir);
            if (ih != null) pullFilteredFrom(ih, SLOT_FUEL, s -> getFuelBurnTime(s) > 0);
        });
    }

    @FunctionalInterface
    private interface ItemFilter { boolean test(ItemStack stack); }

    private void pullFilteredFrom(ResourceHandler<ItemResource> source,
                                  int targetSlot, ItemFilter filter) {
        ItemStack existing = inventory.getItem(targetSlot);

        for (int si = 0; si < source.size(); si++) {
            var res = source.getResource(si);
            if (res.isEmpty()) continue;
            ItemStack testStack = res.toStack(1);
            if (!filter.test(testStack)) continue;
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, testStack)) continue;

            try (var tx = Transaction.openRoot()) {
                int space = existing.isEmpty()
                        ? testStack.getMaxStackSize()
                        : existing.getMaxStackSize() - existing.getCount();
                int extracted = source.extract(res, Math.min(space, (int) source.getAmountAsLong(si)), tx);
                if (extracted > 0) {
                    if (existing.isEmpty()) inventory.setItem(targetSlot, res.toStack(extracted));
                    else                    existing.grow(extracted);
                    tx.commit();
                    return;
                }
            }
        }
    }

    /** Push only from SLOT_OUTPUT — never insert into another machine's output slot (target enforces its own restrictions). */
    private void pushOutputItemsTo(ResourceHandler<ItemResource> target) {
        ItemStack stack = inventory.getItem(SLOT_OUTPUT);
        if (stack.isEmpty()) return;
        try (var tx = Transaction.openRoot()) {
            var res = ItemResource.of(stack);
            int accepted = target.insert(res, Math.min(stack.getCount(), stack.getMaxStackSize()), tx);
            if (accepted > 0) {
                stack.shrink(accepted);
                inventory.setItem(SLOT_OUTPUT, stack.isEmpty() ? ItemStack.EMPTY : stack);
                tx.commit();
            }
        }
    }

    private void tryPushFluid(ResourceHandler<FluidResource> target) {
        if (creosoteFluid.isEmpty()) return;
        try (var tx = Transaction.openRoot()) {
            FluidResource res = FluidResource.of(creosoteFluid);
            int accepted = target.insert(res, Math.min(1000, creosoteFluid.getAmount()), tx);
            if (accepted > 0) {
                creosoteFluid.shrink(accepted);
                if (creosoteFluid.getAmount() <= 0) creosoteFluid = FluidStack.EMPTY;
                tx.commit();
            }
        }
    }

    @FunctionalInterface
    private interface OuterFaceConsumer { void accept(BlockPos outerPos, Direction faceDir); }

    private void forEachOuterFace(OuterFaceConsumer consumer) {
        if (cornerPos == null) return;
        for (int dx = 0; dx < 3; dx++)
            for (int dz = 0; dz < 3; dz++) {
                consumer.accept(cornerPos.offset(dx,  3, dz), Direction.DOWN);
                consumer.accept(cornerPos.offset(dx, -1, dz), Direction.UP);
            }
        for (int dx = 0; dx < 3; dx++)
            for (int dy = 0; dy < 3; dy++) {
                consumer.accept(cornerPos.offset(dx, dy, -1), Direction.SOUTH);
                consumer.accept(cornerPos.offset(dx, dy,  3), Direction.NORTH);
            }
        for (int dy = 0; dy < 3; dy++)
            for (int dz = 0; dz < 3; dz++) {
                consumer.accept(cornerPos.offset(-1, dy, dz), Direction.EAST);
                consumer.accept(cornerPos.offset( 3, dy, dz), Direction.WEST);
            }
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    public boolean validateStructure() {
        if (level() == null || cornerPos == null) return false;
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && y == 1 && z == 1) continue;
                    BlockPos p  = cornerPos.offset(x, y, z);
                    BlockState bs = level().getBlockState(p);
                    if (!(bs.getBlock() instanceof CokeBrickBlock)) return false;
                    if (!bs.getValue(CokeBrickBlock.FORMED)) return false;
                }
        return true;
    }

    public boolean isStructurePos(BlockPos pos) {
        if (cornerPos == null) return false;
        int rx = pos.getX() - cornerPos.getX();
        int ry = pos.getY() - cornerPos.getY();
        int rz = pos.getZ() - cornerPos.getZ();
        if (rx < 0 || rx > 2 || ry < 0 || ry > 2 || rz < 0 || rz > 2) return false;
        return !(rx == 1 && ry == 1 && rz == 1);
    }

    public void onStructureBroken() {
        if (level() == null) return;
        BlockPos center = cornerPos.offset(1, 1, 1);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) Block.popResource(level(), center, stack);
        }
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && y == 1 && z == 1) continue;
                    BlockPos p  = cornerPos.offset(x, y, z);
                    BlockState bs = level().getBlockState(p);
                    if (bs.getBlock() instanceof CokeBrickBlock && bs.getValue(CokeBrickBlock.FORMED))
                        level().setBlock(p, bs.setValue(CokeBrickBlock.FORMED, false), Block.UPDATE_ALL);
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

    // ── Accessors ─────────────────────────────────────────────────────────────

    public SimpleContainer getInventory()  { return inventory; }
    public FluidStack getCreosoteFluid()   { return creosoteFluid; }
    public int getCreosoteAmount()         { return creosoteFluid.getAmount(); }
    public int getCreosoteCapacity()       { return CREOSOTE_CAPACITY; }
    public BlockPos getCornerPos()         { return cornerPos; }
    public Direction getFacing()           { return getEntityData().get(FACING); }
    public boolean isLit()                 { return getEntityData().get(LIT); }
    public int getProcessTimer()           { return getEntityData().get(PROGRESS); }
    public int getProcessTotalTime()       { return getEntityData().get(PROGRESS_MAX); }

    public float getProgressFraction() {
        int total = getEntityData().get(PROGRESS_MAX);
        return total > 0 ? (float) getEntityData().get(PROGRESS) / total : 0f;
    }

    public static boolean isCoal(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    // ── Save / Load ───────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("cornerX", cornerPos.getX());
        output.putInt("cornerY", cornerPos.getY());
        output.putInt("cornerZ", cornerPos.getZ());
        output.putString("facing", getEntityData().get(FACING).getSerializedName());
        output.putInt("processTimer",     processTimer);
        output.putInt("processTotalTime", processTotalTime);
        output.putInt("fuelBurnTime",     fuelBurnTime);
        output.store("creosote", FluidStack.OPTIONAL_CODEC, creosoteFluid);
        output.store("item_input",  ItemStack.CODEC, inventory.getItem(SLOT_INPUT));
        output.store("item_fuel",   ItemStack.CODEC, inventory.getItem(SLOT_FUEL));
        output.store("item_output", ItemStack.CODEC, inventory.getItem(SLOT_OUTPUT));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        cornerPos = new BlockPos(
                input.getIntOr("cornerX", 0),
                input.getIntOr("cornerY", 0),
                input.getIntOr("cornerZ", 0));
        Direction facing = Direction.byName(input.getStringOr("facing", "north"));
        getEntityData().set(FACING, facing != null ? facing : Direction.NORTH);
        processTimer     = input.getIntOr("processTimer",     0);
        processTotalTime = input.getIntOr("processTotalTime", 0);
        fuelBurnTime     = input.getIntOr("fuelBurnTime",     0);
        creosoteFluid    = input.read("creosote", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        input.read("item_input",  ItemStack.CODEC).ifPresent(s -> inventory.setItem(SLOT_INPUT,  s));
        input.read("item_fuel",   ItemStack.CODEC).ifPresent(s -> inventory.setItem(SLOT_FUEL,   s));
        input.read("item_output", ItemStack.CODEC).ifPresent(s -> inventory.setItem(SLOT_OUTPUT, s));
        getEntityData().set(PROGRESS,     processTimer);
        getEntityData().set(PROGRESS_MAX, processTotalTime > 0 ? processTotalTime : 100);
    }
}
