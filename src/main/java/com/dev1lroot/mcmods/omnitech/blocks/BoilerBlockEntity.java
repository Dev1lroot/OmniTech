package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.gui.BoilerMenu;
import com.dev1lroot.mcmods.omnitech.recipes.BoilerRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.BoilerRecipeManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Optional;

public class BoilerBlockEntity extends BaseContainerBlockEntity implements IHeatReceiver, IColdReceiver {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MAX_FLUID     = 8000;
    public static final int TRANSFER_RATE = 100;
    public static final int MIN_BOIL_HEAT = 100;
    public static final int MAX_HEAT      =  500;
    /** Minimum temperature (cold floor) — mirrors MAX_HEAT in the negative direction. */
    public static final int MIN_HEAT      = -500;

    /** One output slot for recipe result items. */
    public static final int SLOT_COUNT  = 1;
    public static final int SLOT_OUTPUT = 0;

    // ── State ─────────────────────────────────────────────────────────────────

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int storedHeat    = 0;
    private int processProgress = 0;
    private int processTotalTime = 20;

    private FluidStack waterTank = FluidStack.EMPTY;
    private FluidStack steamTank = FluidStack.EMPTY;

    private BoilerRecipe currentRecipe = null;

    // ── Fluid capability handlers ─────────────────────────────────────────────

    public final ResourceHandler<FluidResource> waterHandler = new InternalTank(true);
    public final ResourceHandler<FluidResource> steamHandler = new InternalTank(false);

    // ── ContainerData (synced to GUI) ─────────────────────────────────────────
    // Indices: 0=temperature, 1=requiredTemperature, 2=processProgress, 3=processTotalTime,
    //          4=waterAmount, 5=steamAmount

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> storedHeat;
                case 1 -> currentRecipe != null ? currentRecipe.getRequiredMinimalTemperature() : 0;
                case 2 -> processProgress;
                case 3 -> processTotalTime;
                case 4 -> waterTank.getAmount();
                case 5 -> steamTank.getAmount();
                default -> 0;
            };
        }
        @Override public void set(int i, int value) {
            switch (i) {
                case 0 -> storedHeat      = value;
                case 2 -> processProgress = value;
                case 3 -> processTotalTime = value;
            }
        }
        @Override public int getCount() { return 6; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.BOILER.get(), pos, state);
    }

    // IMPORTANT FOR CUSTOM GUIs
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        // Вместо super или ручного создания, используем системный сборщик
        try (net.minecraft.util.ProblemReporter.ScopedCollector reporter =
                     new net.minecraft.util.ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {

            // Создаем чистый выход
            net.minecraft.world.level.storage.TagValueOutput output =
                    net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);

            // ВАЖНО: Вызываем ТВОЙ метод, который сохраняет предметы, температуру и жидкость!
            // Это гарантирует, что в пакете будет ВЕСЬ инвентарь (через ContainerHelper)
            this.saveAdditional(output);

            return output.buildResult();
        }
    }

    // ── IHeatReceiver ─────────────────────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (storedHeat >= MAX_HEAT) return 0;
        int absorbed = Math.min(celsius, MAX_HEAT - storedHeat);
        storedHeat += absorbed;
        return absorbed;
    }

    @Override
    public int addCold(int celsius) {
        if (storedHeat <= MIN_HEAT) return 0;
        int absorbed = Math.min(celsius, storedHeat - MIN_HEAT);
        storedHeat -= absorbed;
        return absorbed;
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.boiler");
    }

    @Override protected NonNullList<ItemStack> getItems()               { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items)     { this.items = items; }
    @Override public int getContainerSize()                             { return SLOT_COUNT; }

    @Override protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new BoilerMenu(containerId, playerInventory, this, dataAccess);
    }

    // ── Network sync ──────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be)
    {
        boolean dirty = false;

        // 1. Pull fluids from all sides except top
        for (Direction face : Direction.values()) {
            if (face == Direction.UP) continue;
            if (be.waterTank.getAmount() >= MAX_FLUID) continue;

            ResourceHandler<FluidResource> neighbor = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos.relative(face), face.getOpposite());

            if (neighbor != null) {
                // Определяем, какой фильтр использовать
                // Если бак пуст, фильтр не нужен (null или любая жидкость)
                // Если не пуст, достаем текущую жидкость из бака
                net.minecraft.world.level.material.Fluid currentFluid = be.waterTank.isEmpty()
                        ? null
                        : be.waterTank.getFluid();

                dirty |= tryPullAnyFluid(neighbor, be.waterHandler, currentFluid);
            }
        }

        // 2. Recipe matching
        Optional<BoilerRecipe> found = BoilerRecipeManager.findRecipe(be.waterTank);
        if (found.isPresent()) {
            BoilerRecipe recipe = found.get();
            if (be.currentRecipe == null || !be.currentRecipe.getId().equals(recipe.getId())) {
                be.currentRecipe   = recipe;
                be.processTotalTime = recipe.getProductionTime();
                be.processProgress = 0;
                dirty = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe   = null;
                be.processProgress = 0;
                dirty = true;
            }
        }

        // 3. Process recipe cycle — consume thermal energy every tick while running.
        //    Hot recipes consume heat (storedHeat falls toward 0).
        //    Cold recipes consume cold (storedHeat rises back toward 0).
        if (be.currentRecipe != null && be.canProcess()) {
            int consumption = be.currentRecipe.getHeatConsumptionPerTick();
            if (be.currentRecipe.getRequiredMinimalTemperature() >= 0) {
                be.storedHeat = Math.max(0, be.storedHeat - consumption);
            } else {
                be.storedHeat = Math.min(0, be.storedHeat + consumption);
            }
            be.processProgress++;
            dirty = true;

            if (be.processProgress >= be.processTotalTime) {
                be.process(level);
                be.processProgress = 0;
                dirty = true;
            }
        } else if (be.processProgress > 0) {
            be.processProgress = 0;
            dirty = true;
        }

        // 4. Push steam upward only
        if (!be.steamTank.isEmpty()) {
            ResourceHandler<FluidResource> output = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos.above(), Direction.DOWN);
            if (output != null)
                dirty |= tryPushFluid(be.steamHandler, output);
        }

        // 5. Update LIT blockstate — active for both hot and cold recipes
        boolean isLit = be.currentRecipe != null && be.canProcess();
        if (state.getValue(BoilerBlock.LIT) != isLit) {
            level.setBlock(pos, state.setValue(BoilerBlock.LIT, isLit), 3);
            dirty = true;
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static boolean tryPullAnyFluid(ResourceHandler<FluidResource> from,
                                           ResourceHandler<FluidResource> to,
                                           @Nullable net.minecraft.world.level.material.Fluid filter) {
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty()) {
                    // Если фильтр задан, проверяем совпадение. Если null — берем любую.
                    if (filter == null || res.is(filter)) {
                        int available = Math.min(TRANSFER_RATE, from.getAmountAsInt(i));
                        int accepted  = to.insert(res, available, tx);
                        if (accepted > 0) {
                            from.extract(res, accepted, tx);
                            tx.commit();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        int required = currentRecipe.getRequiredMinimalTemperature();
        if (required >= 0) {
            // hot recipe: need enough heat
            if (storedHeat < required) return false;
        } else {
            // cold recipe: need to be cold enough
            if (storedHeat > required) return false;
        }

        // Check input water
        if (waterTank.getAmount() < currentRecipe.getInputAmount()) return false;

        // Check steam tank has space
        FluidStack outFluid = currentRecipe.getOutputFluidStack();
        if (outFluid.isEmpty()) return false;
        if (!steamTank.isEmpty() && !steamTank.is(outFluid.getFluid())) return false;
        if ((MAX_FLUID - steamTank.getAmount()) < currentRecipe.getOutputAmount()) return false;

        // Check output slot has space for result item (if guaranteed drop)
        if (currentRecipe.hasResult() && currentRecipe.getResultChance() >= 1.0f) {
            ItemStack slot = items.get(SLOT_OUTPUT);
            if (!slot.isEmpty()) {
                // Check if the result item fits in the existing stack
                if (slot.getCount() >= slot.getMaxStackSize()) return false;
            }
        }

        return true;
    }

    private void process(Level level) {
        if (currentRecipe == null) return;

        // Consume input water
        int toConsume = currentRecipe.getInputAmount();
        waterTank = waterTank.copyWithAmount(waterTank.getAmount() - toConsume);
        if (waterTank.getAmount() <= 0) waterTank = FluidStack.EMPTY;

        // Produce output fluid (steam)
        FluidStack out = currentRecipe.getOutputFluidStack();
        if (!out.isEmpty()) {
            if (steamTank.isEmpty()) steamTank = out.copy();
            else steamTank.grow(out.getAmount());
        }

        // Roll item result
        ItemStack result = currentRecipe.rollResult(level.getRandom());
        if (!result.isEmpty()) {
            ItemStack existing = items.get(SLOT_OUTPUT);
            if (existing.isEmpty()) {
                items.set(SLOT_OUTPUT, result.copy());
            } else if (existing.is(result.getItem()) && existing.getCount() < existing.getMaxStackSize()) {
                existing.grow(result.getCount());
            }
            // If slot is full and item was rolled, it is lost (blocked by canProcess for chance=1.0)
        }

    }

    // ── Fluid transfer helpers ────────────────────────────────────────────────

    private static boolean tryPullFluid(ResourceHandler<FluidResource> from,
                                        ResourceHandler<FluidResource> to,
                                        net.minecraft.world.level.material.Fluid filter) {
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty() && res.is(filter)) {
                    int available = Math.min(TRANSFER_RATE, from.getAmountAsInt(i));
                    int accepted  = to.insert(res, available, tx);
                    if (accepted > 0) {
                        from.extract(res, accepted, tx);
                        tx.commit();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryPushFluid(ResourceHandler<FluidResource> from,
                                        ResourceHandler<FluidResource> to) {
        try (Transaction tx = Transaction.openRoot()) {
            FluidResource res = from.getResource(0);
            if (!res.isEmpty()) {
                int available = Math.min(TRANSFER_RATE, (int) from.getAmountAsLong(0));
                int accepted  = to.insert(res, available, tx);
                if (accepted > 0) {
                    from.extract(res, accepted, tx);
                    tx.commit();
                    return true;
                }
            }
        }
        return false;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        storedHeat     = input.getIntOr("StoredHeat", 0);
        processProgress = input.getIntOr("ProcessProgress", 0);
        processTotalTime = input.getIntOr("ProcessTotalTime", 20);
        waterTank = input.read("WaterTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        steamTank = input.read("SteamTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("StoredHeat",      storedHeat);
        output.putInt("ProcessProgress", processProgress);
        output.putInt("ProcessTotalTime", processTotalTime);
        output.store("WaterTank", FluidStack.OPTIONAL_CODEC, waterTank);
        output.store("SteamTank", FluidStack.OPTIONAL_CODEC, steamTank);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getWaterTank()    { return waterTank; }
    public FluidStack getSteamTank()    { return steamTank; }
    public int getStoredHeat()          { return storedHeat; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── InternalTank (fluid capability) ──────────────────────────────────────

    private class InternalTank extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        private final boolean isWater;

        InternalTank(boolean water) { this.isWater = water; }

        @Override protected FluidStack createSnapshot()        { return isWater ? waterTank : steamTank; }
        @Override protected void revertToSnapshot(FluidStack s) {
            if (isWater) waterTank = s; else steamTank = s;
        }

        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int index) {
            FluidStack s = isWater ? waterTank : steamTank;
            return s.isEmpty() ? FluidResource.EMPTY : FluidResource.of(s);
        }

        @Override public long getAmountAsLong(int index) {
            return isWater ? waterTank.getAmount() : steamTank.getAmount();
        }

        @Override public long getCapacityAsLong(int index, FluidResource res) { return MAX_FLUID; }

        @Override public boolean isValid(int index, FluidResource resource) {
            return isWater; // && resource.is(Fluids.WATER);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (!isValid(index, resource)) return 0;
            int space    = MAX_FLUID - waterTank.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;
            updateSnapshots(tx);
            waterTank = waterTank.isEmpty()
                    ? resource.toStack(toInsert)
                    : waterTank.copyWithAmount(waterTank.getAmount() + toInsert);
            return toInsert;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            FluidStack current = isWater ? waterTank : steamTank;
            if (current.isEmpty() || !resource.matches(current)) return 0;
            int toExt = Math.min(amount, current.getAmount());
            if (toExt <= 0) return 0;
            updateSnapshots(tx);
            if (isWater) {
                waterTank = waterTank.copyWithAmount(waterTank.getAmount() - toExt);
                if (waterTank.getAmount() <= 0) waterTank = FluidStack.EMPTY;
            } else {
                steamTank = steamTank.copyWithAmount(steamTank.getAmount() - toExt);
                if (steamTank.getAmount() <= 0) steamTank = FluidStack.EMPTY;
            }
            return toExt;
        }
    }
}
