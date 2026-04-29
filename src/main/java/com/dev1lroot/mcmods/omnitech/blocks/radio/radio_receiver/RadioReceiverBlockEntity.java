package com.dev1lroot.mcmods.omnitech.blocks.radio.radio_receiver;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioManager;
import com.dev1lroot.mcmods.omnitech.gui.RadioReceiverMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for {@link RadioReceiverBlock}.
 *
 * <p>Each server tick it reads the current signal from {@link RadioManager} at the
 * tuned frequency, quantises it to an integer redstone level (0–15), and updates
 * the {@link RadioReceiverBlock#POWER} blockstate property to drive redstone outputs.
 *
 * <p>ContainerData layout:
 * <ul>
 *   <li>0 – frequencyX10 (875–1174)</li>
 *   <li>1 – currentSignal × 100 (0–1500)</li>
 * </ul>
 */
public class RadioReceiverBlockEntity extends BaseContainerBlockEntity {

    // ── Button IDs ────────────────────────────────────────────────────────────

    public static final int BTN_FREQ_MINUS_100 = 0;
    public static final int BTN_FREQ_MINUS_10  = 1;
    public static final int BTN_FREQ_MINUS_1   = 2;
    public static final int BTN_FREQ_PLUS_1    = 3;
    public static final int BTN_FREQ_PLUS_10   = 4;
    public static final int BTN_FREQ_PLUS_100  = 5;

    // ── State ─────────────────────────────────────────────────────────────────

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private int   frequencyX10  = RadioConstants.FREQ_MIN_X10;
    private float currentSignal = 0f;

    // ── ContainerData ─────────────────────────────────────────────────────────

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> frequencyX10;
                case 1 -> (int)(currentSignal * 100f);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> frequencyX10  = value;
                case 1 -> currentSignal = value / 100f;
            }
        }
        @Override public int getCount() { return 2; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.RADIO_RECEIVER.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.radio_receiver");
    }

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new RadioReceiverMenu(containerId, inv, this, dataAccess);
    }

    // ── Frequency control ─────────────────────────────────────────────────────

    public boolean adjustFrequency(int buttonId) {
        int delta = switch (buttonId) {
            case BTN_FREQ_MINUS_100 -> -100;
            case BTN_FREQ_MINUS_10  -> -10;
            case BTN_FREQ_MINUS_1   -> -1;
            case BTN_FREQ_PLUS_1    -> +1;
            case BTN_FREQ_PLUS_10   -> +10;
            case BTN_FREQ_PLUS_100  -> +100;
            default                 -> 0;
        };
        if (delta == 0) return false;
        setFrequency(frequencyX10 + delta);
        return true;
    }

    public void setFrequency(int newFreqX10) {
        frequencyX10 = Math.clamp(newFreqX10, RadioConstants.FREQ_MIN_X10, RadioConstants.FREQ_MAX_X10);
        setChanged();
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            RadioReceiverBlockEntity be) {
        float signal = RadioManager.get(be.frequencyX10);
        be.currentSignal = signal;

        int newPower = Math.clamp((int) signal, 0, 15);
        int oldPower = state.getValue(RadioReceiverBlock.POWER);
        if (newPower != oldPower) {
            // Flag 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS — notifies adjacent blocks
            level.setBlock(pos, state.setValue(RadioReceiverBlock.POWER, newPower), 3);
        }

        be.setChanged();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int   getFrequencyX10()  { return frequencyX10; }
    public float getCurrentSignal() { return currentSignal; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        frequencyX10  = input.getIntOr("FrequencyX10", RadioConstants.FREQ_MIN_X10);
        currentSignal = input.getFloatOr("CurrentSignal", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt  ("FrequencyX10",  frequencyX10);
        output.putFloat("CurrentSignal", currentSignal);
    }
}
