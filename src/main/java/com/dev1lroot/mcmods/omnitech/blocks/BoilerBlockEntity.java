package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

public class BoilerBlockEntity extends BlockEntity implements IHeatReceiver {

    public static final int MAX_FLUID = 8000;
    public static final int CONVERSION_RATE = 20; // мб воды в пар за тик
    public static final int TRANSFER_RATE = 100;  // скорость всасывания/выталкивания
    public static final int MIN_BOIL_HEAT = 100;
    public static final int MAX_HEAT = 500;

    private int storedHeat = 0;
    private FluidStack waterTank = FluidStack.EMPTY;
    private FluidStack steamTank = FluidStack.EMPTY;

    // Публичные обработчики для регистрации в Capabilities
    public final ResourceHandler<FluidResource> waterHandler = new InternalTank(true);
    public final ResourceHandler<FluidResource> steamHandler = new InternalTank(false);

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.BOILER.get(), pos, state);
    }

    @Override
    public boolean addHeat(int celsius) {
        if (storedHeat >= MAX_HEAT) return false;
        storedHeat = Math.min(MAX_HEAT, storedHeat + celsius);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        boolean dirty = false;

        // 1. АКТИВНОЕ ВТЯГИВАНИЕ ВОДЫ (со всех сторон, кроме верха)
        for (Direction face : Direction.values()) {
            if (face == Direction.UP) continue;

            if (be.waterTank.getAmount() < MAX_FLUID) {
                ResourceHandler<FluidResource> neighbor = level.getCapability(
                        Capabilities.Fluid.BLOCK, pos.relative(face), face.getOpposite());

                if (neighbor != null) {
                    dirty |= tryPullFluid(neighbor, be.waterHandler, Fluids.WATER);
                }
            }
        }

        // 2. ПРОЦЕСС КИПЕНИЯ
        if (be.storedHeat >= MIN_BOIL_HEAT && !be.waterTank.isEmpty()) {
            int toConvert = Math.min(be.waterTank.getAmount(), CONVERSION_RATE);
            int canFit = MAX_FLUID - be.steamTank.getAmount();
            int finalAmount = Math.min(toConvert, canFit);

            if (finalAmount > 0) {
                be.waterTank.shrink(finalAmount);

                // ВНИМАНИЕ: Замените Fluids.WATER на ваш кастомный пар (например, OmniTechFluids.STEAM.get())
                FluidStack steam = new FluidStack(OmniTechFluids.STEAM.get(), finalAmount);

                if (be.steamTank.isEmpty()) be.steamTank = steam;
                else be.steamTank.grow(finalAmount);

                if (level.getGameTime() % 40 == 0) be.storedHeat--; // Остывание при работе
                dirty = true;
            }
        }

        // 3. АКТИВНОЕ ВЫТАЛКИВАНИЕ ПАРА (только вверх)
        if (!be.steamTank.isEmpty()) {
            ResourceHandler<FluidResource> output = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos.above(), Direction.DOWN);

            if (output != null) {
                dirty |= tryPushFluid(be.steamHandler, output);
            }
        }

        // Обновление визуального состояния LIT
        boolean isLit = be.storedHeat >= MIN_BOIL_HEAT;
        if (state.getValue(BoilerBlock.LIT) != isLit) {
            level.setBlock(pos, state.setValue(BoilerBlock.LIT, isLit), 3);
        }

        if (dirty) be.setChanged();
    }

    // Вспомогательные методы для логики перемещения жидкостей
    private static boolean tryPullFluid(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, net.minecraft.world.level.material.Fluid filter) {
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty() && res.is(filter)) {
                    int available = Math.min(TRANSFER_RATE, from.getAmountAsInt(i));
                    int accepted = to.insert(res, available, tx);
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

    private static boolean tryPushFluid(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to) {
        try (Transaction tx = Transaction.openRoot()) {
            FluidResource res = from.getResource(0);
            if (!res.isEmpty()) {
                int available = Math.min(TRANSFER_RATE, (int)from.getAmountAsLong(0));
                int accepted = to.insert(res, available, tx);
                if (accepted > 0) {
                    from.extract(res, accepted, tx);
                    tx.commit();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedHeat = input.getIntOr("StoredHeat", 0);
        waterTank = input.read("WaterTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        steamTank = input.read("SteamTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("StoredHeat", storedHeat);
        output.store("WaterTank", FluidStack.OPTIONAL_CODEC, waterTank);
        output.store("SteamTank", FluidStack.OPTIONAL_CODEC, steamTank);
    }

    private class InternalTank extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        private final boolean isWater;
        InternalTank(boolean water) { this.isWater = water; }

        @Override protected FluidStack createSnapshot() { return isWater ? waterTank : steamTank; }
        @Override protected void revertToSnapshot(FluidStack s) {
            if(isWater) waterTank = s; else steamTank = s;
        }

        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int index) {
            FluidStack s = isWater ? waterTank : steamTank;
            return s.isEmpty() ? FluidResource.EMPTY : FluidResource.of(s);
        }

        @Override public long getAmountAsLong(int index) { return isWater ? waterTank.getAmount() : steamTank.getAmount(); }

        @Override public long getCapacityAsLong(int index, FluidResource res) { return MAX_FLUID; }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            // Разрешаем вставлять только воду в водяной бак
            return isWater && resource.is(Fluids.WATER);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (!isValid(index, resource)) return 0;
            int space = MAX_FLUID - waterTank.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;

            updateSnapshots(tx);
            waterTank = waterTank.isEmpty() ? resource.toStack(toInsert) : waterTank.copyWithAmount(waterTank.getAmount() + toInsert);
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