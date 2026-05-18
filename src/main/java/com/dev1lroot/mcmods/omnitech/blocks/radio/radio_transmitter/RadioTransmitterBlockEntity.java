/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.radio.radio_transmitter;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioManager;
import com.dev1lroot.mcmods.omnitech.gui.RadioTransmitterMenu;
import com.dev1lroot.mcmods.omnitech.io.IAnalogInput;
import com.dev1lroot.mcmods.omnitech.io.IAudioInput;
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
 * <p>ContainerData layout:
 * <ul>
 *   <li>0 – band ordinal (0–8)</li>
 *   <li>1 – channel index within band</li>
 *   <li>2 – currentSignal × 100 (0–1500)</li>
 * </ul>
 *
 * <p>Button IDs 0–5: frequency ±1/±10/±100 within current band.
 * Button IDs 6–14: switch to band N (N = id − 6).
 */
public class RadioTransmitterBlockEntity extends BaseContainerBlockEntity implements IAnalogInput, IAudioInput {

    public static final int BTN_FREQ_MINUS_100 = 0;
    public static final int BTN_FREQ_MINUS_10  = 1;
    public static final int BTN_FREQ_MINUS_1   = 2;
    public static final int BTN_FREQ_PLUS_1    = 3;
    public static final int BTN_FREQ_PLUS_10   = 4;
    public static final int BTN_FREQ_PLUS_100  = 5;
    public static final int BTN_BAND_BASE      = 6;

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);

    private FrequencyBand band       = FrequencyBand.VHF;
    private int           channelIdx = 0;
    private float         currentSignal = 0f;

    private float  analogSignal   = 0f;
    private long   lastAnalogTick = Long.MIN_VALUE;
    private byte[] audioBuffer    = new byte[0];
    private long   lastAudioTick  = Long.MIN_VALUE;

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

    public RadioTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.RADIO_TRANSMITTER.get(), pos, state);
    }

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.radio_transmitter");
    }
    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new RadioTransmitterMenu(containerId, inv, this, dataAccess);
    }

    @Override
    public void receiveAnalogSignal(float signal) {
        analogSignal = signal;
        if (level != null) lastAnalogTick = level.getGameTime();
    }

    @Override
    public void receiveAudio(byte[] samples) {
        if (samples == null || samples.length == 0) return;
        audioBuffer   = samples;
        if (level != null) lastAudioTick = level.getGameTime();
    }

    // ── Button / frequency control ────────────────────────────────────────────

    public boolean handleButton(int id) {
        FrequencyBand[] bands = FrequencyBand.values();
        if (id >= BTN_BAND_BASE && id < BTN_BAND_BASE + bands.length) {
            FrequencyBand newBand = bands[id - BTN_BAND_BASE];
            if (newBand != band && level != null && !level.isClientSide()) {
                int oldKey = globalKey();
                RadioManager.clear(level.dimension(), oldKey, band.interdimensional());
                RadioManager.clearAudio(level.dimension(), oldKey, band.interdimensional());
                RadioManager.unregisterTransmitter(level.dimension(), oldKey, worldPosition);
            }
            band = newBand;
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
        if (level != null && !level.isClientSide()) {
            RadioManager.clear(level.dimension(), globalKey(), band.interdimensional());
            RadioManager.unregisterTransmitter(level.dimension(), globalKey(), worldPosition);
        }
        channelIdx = Math.clamp(channelIdx + delta, 0, band.channels() - 1);
        setChanged();
        return true;
    }

    /** Set via direct-entry packet: globalKey encodes band + channel. */
    public void setFrequency(int globalKey) {
        FrequencyBand newBand = FrequencyBand.fromGlobalKey(globalKey);
        int newCh = Math.clamp(FrequencyBand.channelOf(globalKey), 0, newBand.channels() - 1);
        if (newBand == band && newCh == channelIdx) return;
        if (level != null && !level.isClientSide()) {
            RadioManager.clear(level.dimension(), globalKey(), band.interdimensional());
            RadioManager.clearAudio(level.dimension(), globalKey(), band.interdimensional());
            RadioManager.unregisterTransmitter(level.dimension(), globalKey(), worldPosition);
        }
        band       = newBand;
        channelIdx = newCh;
        setChanged();
    }

    private int globalKey() { return band.globalKey(channelIdx); }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            RadioTransmitterBlockEntity be) {
        int redstone = level.getBestNeighborSignal(pos);
        float effective = (level.getGameTime() - be.lastAnalogTick <= 12) ? be.analogSignal : 0f;
        be.currentSignal = Math.max(redstone, effective);

        int key = be.globalKey();
        boolean interdim = be.band.interdimensional();

        RadioManager.set(level.dimension(), key, be.currentSignal, interdim);
        RadioManager.registerTransmitter(level.dimension(), key, pos);

        if (be.audioBuffer.length > 0 && level.getGameTime() - be.lastAudioTick <= 12) {
            RadioManager.setAudio(level.dimension(), key, be.audioBuffer, be.lastAudioTick, interdim);
        } else {
            RadioManager.clearAudio(level.dimension(), key, interdim);
        }

        be.setChanged();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            int key = globalKey();
            boolean interdim = band.interdimensional();
            RadioManager.clear(level.dimension(), key, interdim);
            RadioManager.clearAudio(level.dimension(), key, interdim);
            RadioManager.unregisterTransmitter(level.dimension(), key, worldPosition);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FrequencyBand getBand()        { return band; }
    public int           getChannelIdx()  { return channelIdx; }
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
