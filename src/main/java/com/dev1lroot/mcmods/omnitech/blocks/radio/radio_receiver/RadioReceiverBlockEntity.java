package com.dev1lroot.mcmods.omnitech.blocks.radio.radio_receiver;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioManager;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioManager.AudioFrame;
import com.dev1lroot.mcmods.omnitech.gui.RadioReceiverMenu;
import com.dev1lroot.mcmods.omnitech.io.IAnalogOutput;
import com.dev1lroot.mcmods.omnitech.util.AnalogNetworkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Block entity for {@link RadioReceiverBlock}.
 *
 * <p>Band behaviour rules applied each tick:
 * <ul>
 *   <li>ELF/VLF — redstone signal only; audio is suppressed.</li>
 *   <li>LF/MF   — audio is relayed but mixed 50 % with white noise.</li>
 *   <li>HF      — full audio, unlimited range, same dimension.</li>
 *   <li>VHF/UHF — full audio; requires a transmitter within {@code maxRange} blocks.</li>
 *   <li>SHF/EHF — full audio; signal is shared across all dimensions (interdimensional).</li>
 * </ul>
 *
 * <p>ContainerData layout:
 * <ul>
 *   <li>0 – band ordinal (0–8)</li>
 *   <li>1 – channel index within band</li>
 *   <li>2 – currentSignal × 100 (0–1500)</li>
 * </ul>
 */
public class RadioReceiverBlockEntity extends BaseContainerBlockEntity implements IAnalogOutput {

    public static final int BTN_FREQ_MINUS_100 = 0;
    public static final int BTN_FREQ_MINUS_10  = 1;
    public static final int BTN_FREQ_MINUS_1   = 2;
    public static final int BTN_FREQ_PLUS_1    = 3;
    public static final int BTN_FREQ_PLUS_10   = 4;
    public static final int BTN_FREQ_PLUS_100  = 5;
    public static final int BTN_BAND_BASE      = 6;

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);

    private FrequencyBand band             = FrequencyBand.VHF;
    private int           channelIdx       = 0;
    private float         currentSignal    = 0f;
    private float         lastPushedSignal = -1f;
    private long          lastAudioFrameTime = Long.MIN_VALUE;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> band.ordinal();
                case 1 -> channelIdx;
                case 2 -> (int)(currentSignal * 100f);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> {
                    FrequencyBand[] vals = FrequencyBand.values();
                    band = (value >= 0 && value < vals.length) ? vals[value] : FrequencyBand.VHF;
                }
                case 1 -> channelIdx = Math.clamp(value, 0, band.channels() - 1);
                case 2 -> currentSignal = value / 100f;
            }
        }
        @Override public int getCount() { return 3; }
    };

    public RadioReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.RADIO_RECEIVER.get(), pos, state);
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.radio_receiver");
    }
    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new RadioReceiverMenu(containerId, inv, this, dataAccess);
    }

    @Override public float getAnalogSignal() { return currentSignal; }

    // ── Button / frequency control ────────────────────────────────────────────

    public boolean handleButton(int id) {
        FrequencyBand[] bands = FrequencyBand.values();
        if (id >= BTN_BAND_BASE && id < BTN_BAND_BASE + bands.length) {
            band = bands[id - BTN_BAND_BASE];
            channelIdx = 0;
            setChanged();
            return true;
        }
        int delta = switch (id) {
            case BTN_FREQ_MINUS_100 -> -100;
            case BTN_FREQ_MINUS_10  -> -10;
            case BTN_FREQ_MINUS_1   -> -1;
            case BTN_FREQ_PLUS_1    -> +1;
            case BTN_FREQ_PLUS_10   -> +10;
            case BTN_FREQ_PLUS_100  -> +100;
            default -> 0;
        };
        if (delta == 0) return false;
        channelIdx = Math.clamp(channelIdx + delta, 0, band.channels() - 1);
        setChanged();
        return true;
    }

    public void setFrequency(int globalKey) {
        band       = FrequencyBand.fromGlobalKey(globalKey);
        channelIdx = Math.clamp(FrequencyBand.channelOf(globalKey), 0, band.channels() - 1);
        setChanged();
    }

    private int globalKey() { return band.globalKey(channelIdx); }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            RadioReceiverBlockEntity be) {
        FrequencyBand band = be.band;
        int key = be.globalKey();
        boolean interdim = band.interdimensional();

        float signal = RadioManager.get(level.dimension(), key, interdim);

        // VHF/UHF: zero signal if no transmitter within range in same dimension
        if (band.maxRange() > 0 && signal > 0f) {
            Set<BlockPos> txPositions = RadioManager.getTransmitterPositions(level.dimension(), key);
            long maxRangeSq = (long) band.maxRange() * band.maxRange();
            boolean inRange = txPositions.stream()
                    .anyMatch(txPos -> pos.distSqr(txPos) <= maxRangeSq);
            if (!inRange) signal = 0f;
        }

        be.currentSignal = signal;

        int newPower = Math.clamp((int) signal, 0, 15);
        int oldPower = state.getValue(RadioReceiverBlock.POWER);
        if (newPower != oldPower) {
            level.setBlock(pos, state.setValue(RadioReceiverBlock.POWER, newPower), 3);
        }

        if (Math.abs(be.currentSignal - be.lastPushedSignal) >= 0.1f
                || level.getGameTime() % 8 == 0) {
            be.lastPushedSignal = be.currentSignal;
            AnalogNetworkUtil.pushSignal(level, pos, be.currentSignal, Direction.values());
        }

        if (band.audioAllowed()) {
            AudioFrame frame = RadioManager.getAudio(level.dimension(), key, interdim);
            if (frame != null && frame.frameTime() != be.lastAudioFrameTime) {
                be.lastAudioFrameTime = frame.frameTime();
                byte[] audio = band.noisy() ? mixNoise(frame.samples()) : frame.samples();
                AnalogNetworkUtil.pushAudio(level, pos, audio, Direction.values());
            }
        }

        be.setChanged();
    }

    /** Mix audio 50 % with white noise to simulate LF/MF propagation degradation. */
    private static byte[] mixNoise(byte[] samples) {
        byte[] out = new byte[samples.length];
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < samples.length; i++) {
            int voice = samples[i] & 0xFF;
            int noise = rng.nextInt(256);
            out[i] = (byte)((voice / 2 + noise / 2) & 0xFF);
        }
        return out;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FrequencyBand getBand()          { return band; }
    public int           getChannelIdx()    { return channelIdx; }
    public float         getCurrentSignal() { return currentSignal; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int bandOrd = input.getIntOr("Band", FrequencyBand.VHF.ordinal());
        FrequencyBand[] vals = FrequencyBand.values();
        band = (bandOrd >= 0 && bandOrd < vals.length) ? vals[bandOrd] : FrequencyBand.VHF;
        channelIdx    = Math.clamp(input.getIntOr("ChannelIdx", 0), 0, band.channels() - 1);
        currentSignal = input.getFloatOr("CurrentSignal", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt  ("Band",          band.ordinal());
        output.putInt  ("ChannelIdx",    channelIdx);
        output.putFloat("CurrentSignal", currentSignal);
    }
}
