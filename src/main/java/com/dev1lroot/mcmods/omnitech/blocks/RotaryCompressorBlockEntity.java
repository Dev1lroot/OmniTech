package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.RotaryCompressorMenu;
import com.dev1lroot.mcmods.omnitech.recipes.RotaryCompressionRecipe;
import com.dev1lroot.mcmods.omnitech.recipes.RotaryCompressionRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Optional;

public class RotaryCompressorBlockEntity extends BlockEntity implements IKineticReceiver, MenuProvider {

    public static final int INPUT_TANK_CAPACITY  = 8_000;
    public static final int OUTPUT_TANK_CAPACITY = 8_000;

    private FluidStack inputFluid  = FluidStack.EMPTY;
    private FluidStack outputFluid = FluidStack.EMPTY;

    private float kineticForce         = 0f;
    private float requiredKineticForce = 0f;
    private RotaryCompressionRecipe currentRecipe   = null;
    private String                  currentRecipeId = null;

    public final ResourceHandler<FluidResource> inputFluidHandler  = new InputTankHandler();
    public final ResourceHandler<FluidResource> outputFluidHandler = new OutputTankHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(kineticForce * 100f);
                case 1 -> (int)(requiredKineticForce * 100f);
                case 2 -> inputFluid.getAmount();
                case 3 -> INPUT_TANK_CAPACITY;
                case 4 -> outputFluid.getAmount();
                case 5 -> OUTPUT_TANK_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> kineticForce = value / 100f;
                case 1 -> requiredKineticForce = value / 100f;
            }
        }
        @Override public int getCount() { return 6; }
    };

    public RotaryCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ROTARY_COMPRESSOR.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.rotary_compressor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new RotaryCompressorMenu(containerId, inv, this, dataAccess);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var reporter = new ProblemReporter.ScopedCollector(this.problemPath(), com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    @Override
    public float getKfDemand() {
        return (currentRecipe != null && canProcess()) ? 0.1f : 0f;
    }

    @Override
    public boolean addKineticForce(float amount) {
        if (currentRecipe == null || !canProcess()) return false;
        kineticForce += amount;
        setChanged();
        if (kineticForce >= requiredKineticForce) {
            process();
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RotaryCompressorBlockEntity be) {
        boolean changed = false;
        Direction facing = state.getValue(RotaryCompressorBlock.FACING);

        // 1. Pull fluid into input tank from the front face
        if (be.inputFluid.getAmount() < INPUT_TANK_CAPACITY) {
            var inputSource = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(facing), facing.getOpposite());
            if (inputSource != null) {
                changed |= tryPullFluid(inputSource, be.inputFluidHandler);
            }
        }

        // 2. Match recipe
        Optional<RotaryCompressionRecipe> found = RotaryCompressionRecipeManager.findRecipe(be.inputFluid);
        if (found.isPresent()) {
            RotaryCompressionRecipe recipe = found.get();
            if (!recipe.getId().equals(be.currentRecipeId)) {
                be.currentRecipe = recipe;
                be.currentRecipeId = recipe.getId();
                be.kineticForce = 0f;
                be.requiredKineticForce = recipe.getRequiredKineticForce();
                changed = true;
            }
        } else {
            if (be.currentRecipe != null) {
                be.currentRecipe = null;
                be.currentRecipeId = null;
                be.kineticForce = 0f;
                be.requiredKineticForce = 0f;
                changed = true;
            }
        }

        // 3. LIT state
        boolean shouldBeLit = be.currentRecipe != null && be.canProcess();
        if (state.getValue(RotaryCompressorBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(RotaryCompressorBlock.LIT, shouldBeLit), 3);
            changed = true;
        }

        // 4. Push output fluid out the back face
        if (!be.outputFluid.isEmpty()) {
            Direction back = facing.getOpposite();
            var neighbor = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(back), back.getOpposite());
            if (neighbor != null) {
                changed |= tryPushFluid(be.outputFluidHandler, neighbor);
            }
        }

        if (changed) {
            be.setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    private boolean canProcess() {
        if (currentRecipe == null) return false;
        if (inputFluid.isEmpty() || !inputFluid.is(currentRecipe.getInputFluid().getFluid())) return false;
        if (inputFluid.getAmount() < currentRecipe.getInputFluidAmount()) return false;

        FluidStack out = currentRecipe.getOutputFluid();
        if (outputFluid.isEmpty()) return true;
        if (!outputFluid.is(out.getFluid())) return false;
        return (OUTPUT_TANK_CAPACITY - outputFluid.getAmount()) >= out.getAmount();
    }

    private void process() {
        if (currentRecipe == null) return;
        int toConsume = currentRecipe.getInputFluidAmount();
        inputFluid.shrink(toConsume);
        if (inputFluid.getAmount() <= 0) inputFluid = FluidStack.EMPTY;

        FluidStack out = currentRecipe.getOutputFluid();
        if (!out.isEmpty()) {
            if (outputFluid.isEmpty()) {
                outputFluid = out.copy();
            } else {
                outputFluid.grow(out.getAmount());
            }
        }

        kineticForce = 0f;
        setChanged();
    }

    private static boolean tryPullFluid(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to) {
        try (var tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (!res.isEmpty()) {
                    int available = Math.min(1000, (int) from.getAmountAsLong(i));
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
        try (var tx = Transaction.openRoot()) {
            FluidResource res = from.getResource(0);
            if (res.isEmpty()) return false;
            int available = Math.min(1000, (int) from.getAmountAsLong(0));
            int accepted = to.insert(res, available, tx);
            if (accepted > 0) {
                from.extract(res, accepted, tx);
                tx.commit();
                return true;
            }
        }
        return false;
    }

    private class InputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot() { return inputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { inputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return inputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inputFluid); }
        @Override public long getAmountAsLong(int index) { return inputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return INPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource) { return true; }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (resource.isEmpty() || (!inputFluid.isEmpty() && !resource.matches(inputFluid))) return 0;
            int toFill = Math.min(amount, INPUT_TANK_CAPACITY - inputFluid.getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            inputFluid = inputFluid.isEmpty() ? resource.toStack(toFill) : inputFluid.copyWithAmount(inputFluid.getAmount() + toFill);
            return toFill;
        }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
    }

    private class OutputTankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        @Override protected FluidStack createSnapshot() { return outputFluid.copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { outputFluid = s; }
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return outputFluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(outputFluid); }
        @Override public long getAmountAsLong(int index) { return outputFluid.getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource res) { return OUTPUT_TANK_CAPACITY; }
        @Override public boolean isValid(int index, FluidResource resource) { return false; }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return 0; }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (outputFluid.isEmpty() || !resource.matches(outputFluid)) return 0;
            int toExt = Math.min(amount, outputFluid.getAmount());
            updateSnapshots(tx);
            outputFluid = outputFluid.copyWithAmount(outputFluid.getAmount() - toExt);
            if (outputFluid.getAmount() <= 0) outputFluid = FluidStack.EMPTY;
            return toExt;
        }
    }

    public FluidStack getInputFluid()  { return inputFluid; }
    public FluidStack getOutputFluid() { return outputFluid; }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inputFluid  = input.read("InputFluid",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        outputFluid = input.read("OutputFluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        kineticForce         = input.getFloatOr("KineticForce",         0f);
        requiredKineticForce = input.getFloatOr("RequiredKineticForce", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("InputFluid",  FluidStack.OPTIONAL_CODEC, inputFluid);
        output.store("OutputFluid", FluidStack.OPTIONAL_CODEC, outputFluid);
        output.putFloat("KineticForce",         kineticForce);
        output.putFloat("RequiredKineticForce", requiredKineticForce);
    }
}
