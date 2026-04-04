package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.gui.StirlingEngineMenu;
import com.dev1lroot.mcmods.omnitech.util.KineticNetworkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
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
import org.jetbrains.annotations.Nullable;

public class StirlingEngineBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_FLUID = 4000;
    public static final int CONSUMPTION_RATE = 10;
    public static final int WATER_OUTPUT_RATE = 10;
    public static final int KF_PRODUCTION = 2;

    private FluidStack steamTank = FluidStack.EMPTY;
    private FluidStack waterTank = FluidStack.EMPTY;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> steamTank.getAmount();
                case 1 -> MAX_FLUID;
                case 2 -> waterTank.getAmount();
                case 3 -> MAX_FLUID;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 4; }
    };

    public final ResourceHandler<FluidResource> steamHandler = new InternalTank(true);
    public final ResourceHandler<FluidResource> waterHandler = new InternalTank(false);

    public StirlingEngineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.STIRLING_ENGINE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.stirling_engine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new StirlingEngineMenu(containerId, playerInventory, this, this.dataAccess);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StirlingEngineBlockEntity be) {
        boolean dirty = false;

        // 1. Втягивание пара (со всех сторон кроме низа)
        if (be.steamTank.getAmount() < MAX_FLUID) {
            for (Direction face : Direction.values()) {
                if (face == Direction.DOWN) continue;
                var neighbor = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(face), face.getOpposite());
                if (neighbor != null) {
                    dirty |= tryPullFluid(neighbor, be.steamHandler, OmniTechFluids.STEAM.get());
                }
            }
        }

        // 2. Логика работы
        boolean canWork = be.steamTank.getAmount() >= CONSUMPTION_RATE &&
                be.waterTank.getAmount() + WATER_OUTPUT_RATE <= MAX_FLUID;

        if (canWork) {
            be.steamTank.shrink(CONSUMPTION_RATE);
            if (be.steamTank.isEmpty()) be.steamTank = FluidStack.EMPTY;

            if (be.waterTank.isEmpty()) be.waterTank = new FluidStack(Fluids.WATER, WATER_OUTPUT_RATE);
            else be.waterTank.grow(WATER_OUTPUT_RATE);

            KineticNetworkUtil.propagateKineticForce(level, pos, KF_PRODUCTION);
            dirty = true;
        }

        // 3. Выталкивание воды вниз
        if (!be.waterTank.isEmpty()) {
            var output = level.getCapability(Capabilities.Fluid.BLOCK, pos.below(), Direction.UP);
            if (output != null) {
                dirty |= tryPushFluid(be.waterHandler, output);
            }
        }

        if (state.getValue(StirlingEngineBlock.LIT) != canWork) {
            level.setBlock(pos, state.setValue(StirlingEngineBlock.LIT, canWork), 3);
        }

        if (dirty) {
            be.setChanged();
        }
    }

    private static boolean tryPullFluid(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, net.minecraft.world.level.material.Fluid filter) {
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty() && res.is(filter)) {
                    int available = Math.min(100, (int)from.getAmountAsLong(i));
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
                int available = Math.min(100, (int)from.getAmountAsLong(0));
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
        steamTank = input.read("SteamTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        waterTank = input.read("WaterTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("SteamTank", FluidStack.OPTIONAL_CODEC, steamTank);
        output.store("WaterTank", FluidStack.OPTIONAL_CODEC, waterTank);
    }

    private class InternalTank extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        private final boolean isSteam;
        InternalTank(boolean steam) { this.isSteam = steam; }

        @Override protected FluidStack createSnapshot() { return isSteam ? steamTank.copy() : waterTank.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) {
            if(isSteam) steamTank = s; else waterTank = s;
        }

        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) {
            FluidStack s = isSteam ? steamTank : waterTank;
            return s.isEmpty() ? FluidResource.EMPTY : FluidResource.of(s);
        }
        @Override public long getAmountAsLong(int index) { return isSteam ? steamTank.getAmount() : waterTank.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return MAX_FLUID; }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return isSteam ? resource.is(OmniTechFluids.STEAM.get()) : resource.is(Fluids.WATER);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (!isValid(index, resource)) return 0;
            int current = isSteam ? steamTank.getAmount() : waterTank.getAmount();
            int space = MAX_FLUID - current;
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;
            updateSnapshots(tx);
            if (isSteam) {
                steamTank = steamTank.isEmpty() ? resource.toStack(toInsert) : steamTank.copyWithAmount(current + toInsert);
            } else {
                waterTank = waterTank.isEmpty() ? resource.toStack(toInsert) : waterTank.copyWithAmount(current + toInsert);
            }
            return toInsert;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            FluidStack tank = isSteam ? steamTank : waterTank;
            if (tank.isEmpty() || !resource.matches(tank)) return 0;
            int toExt = Math.min(amount, tank.getAmount());
            updateSnapshots(tx);
            if (isSteam) {
                steamTank = steamTank.copyWithAmount(steamTank.getAmount() - toExt);
                if (steamTank.getAmount() <= 0) steamTank = FluidStack.EMPTY;
            } else {
                waterTank = waterTank.copyWithAmount(waterTank.getAmount() - toExt);
                if (waterTank.getAmount() <= 0) waterTank = FluidStack.EMPTY;
            }
            return toExt;
        }
    }
}