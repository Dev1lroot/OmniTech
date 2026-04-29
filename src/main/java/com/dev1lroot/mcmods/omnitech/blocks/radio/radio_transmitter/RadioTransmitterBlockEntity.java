package com.dev1lroot.mcmods.omnitech.blocks.radio.radio_transmitter;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioManager;
import com.dev1lroot.mcmods.omnitech.gui.RadioTransmitterMenu;
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
 * Block entity for {@link RadioTransmitterBlock}.
 *
 * <p>Each server tick it reads the strongest incoming redstone signal (0–15),
 * converts it to a float, and writes it to {@link RadioManager} at the tuned frequency.
 *
 * <p>ContainerData layout:
 * <ul>
 *   <li>0 – frequencyX10 (875–1174)</li>
 *   <li>1 – currentSignal × 100 (0–1500)</li>
 * </ul>
 */
public class RadioTransmitterBlockEntity extends BaseContainerBlockEntity {

    // ── Button IDs ────────────────────────────────────────────────────────────

    public static final int BTN_FREQ_MINUS_100 = 0;
    public static final int BTN_FREQ_MINUS_10  = 1;
    public static final int BTN_FREQ_MINUS_1   = 2;
    public static final int BTN_FREQ_PLUS_1    = 3;
    public static final int BTN_FREQ_PLUS_10   = 4;
    public static final int BTN_FREQ_PLUS_100  = 5;

    // ── State ─────────────────────────────────────────────────────────────────

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private int   frequencyX10   = RadioConstants.FREQ_MIN_X10; // 87.5 MHz default
    private float currentSignal  = 0f;

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

    public RadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.RADIO_TRANSMITTER.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.radio_transmitter");
    }

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new RadioTransmitterMenu(containerId, inv, this, dataAccess);
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
        int old = frequencyX10;
        frequencyX10 = Math.clamp(newFreqX10, RadioConstants.FREQ_MIN_X10, RadioConstants.FREQ_MAX_X10);
        if (old != frequencyX10 && level != null && !level.isClientSide()) {
            RadioManager.clear(old);
        }
        setChanged();
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            RadioTransmitterBlockEntity be) {
        int redstone = level.getBestNeighborSignal(pos);
        be.currentSignal = redstone;
        RadioManager.set(be.frequencyX10, be.currentSignal);
        be.setChanged();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            RadioManager.clear(frequencyX10);
        }
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
