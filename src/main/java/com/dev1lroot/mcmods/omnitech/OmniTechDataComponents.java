/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.Objects;


public class OmniTechDataComponents {

    /** Immutable byte-array wrapper satisfying the DataComponent equals/hashCode contract. */
    public record ByteData(byte[] data) {
        @Override public boolean equals(Object o) {
            return o instanceof ByteData b && Arrays.equals(this.data, b.data);
        }
        @Override public int hashCode() { return Arrays.hashCode(data); }
        @Override public String toString() { return "ByteData[" + data.length + " bytes]"; }
    }

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, OmniTech.MODID);

    /**
     * Codec for {@link ResourceStack}{@code <FluidResource>} — used by
     * {@link com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem} to persist fluid contents.
     *
     * <p>{@link ResourceStack} is a record, so it satisfies NeoForge's requirement that
     * DataComponent values implement {@code equals} and {@code hashCode}.
     */
    private static final Codec<ResourceStack<FluidResource>> FLUID_STACK_CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    FluidResource.OPTIONAL_CODEC.fieldOf("fluid").forGetter(ResourceStack::resource),
                    Codec.INT.fieldOf("amount").forGetter(ResourceStack::amount)
            ).apply(i, ResourceStack::new));

    private static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ResourceStack<FluidResource>> FLUID_STACK_STREAM_CODEC =
            StreamCodec.composite(
                    FluidResource.STREAM_CODEC, ResourceStack::resource,
                    ByteBufCodecs.INT,            ResourceStack::amount,
                    ResourceStack::new);

    /**
     * Stores the fluid contents of a {@link com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem}.
     * Uses an immutable {@link ResourceStack}{@code <FluidResource>} so the DataComponent
     * validation (equals + hashCode contract) is satisfied.
     * Only present when the canister contains fluid (amount > 0).
     */
    public static final Supplier<DataComponentType<ResourceStack<FluidResource>>> FLUID_CONTENTS =
            REGISTRY.register("fluid_contents", () ->
                    DataComponentType.<ResourceStack<FluidResource>>builder()
                            .persistent(FLUID_STACK_CODEC)
                            .networkSynchronized(FLUID_STACK_STREAM_CODEC)
                            .build());

    /**
     * The multi-fluid solution stored inside a {@link com.dev1lroot.mcmods.omnitech.items.FlaskItem}
     * or {@link com.dev1lroot.mcmods.omnitech.items.PipetteItem}. Only present when the
     * container holds at least one component.
     */
    /**
     * The composition (ratios only) of a {@code omnitech:solution} fluid stack — what a mixture
     * of several fluids is made of while it travels through pipes and machines.
     */
    public static final Supplier<DataComponentType<com.dev1lroot.mcmods.omnitech.items.Mixture>> MIXTURE =
            REGISTRY.register("mixture", () ->
                    DataComponentType.<com.dev1lroot.mcmods.omnitech.items.Mixture>builder()
                            .persistent(com.dev1lroot.mcmods.omnitech.items.Mixture.CODEC)
                            .networkSynchronized(com.dev1lroot.mcmods.omnitech.items.Mixture.STREAM_CODEC)
                            .build());

    public static final Supplier<DataComponentType<com.dev1lroot.mcmods.omnitech.items.Solution>> SOLUTION =
            REGISTRY.register("solution", () ->
                    DataComponentType.<com.dev1lroot.mcmods.omnitech.items.Solution>builder()
                            .persistent(com.dev1lroot.mcmods.omnitech.items.Solution.CODEC)
                            .networkSynchronized(com.dev1lroot.mcmods.omnitech.items.Solution.STREAM_CODEC)
                            .build());

    /** Target draw amount (mB, 1–20) configured on a {@link com.dev1lroot.mcmods.omnitech.items.PipetteItem}. */
    public static final Supplier<DataComponentType<Integer>> PIPETTE_AMOUNT =
            REGISTRY.register("pipette_amount", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** EU stored in a bore tool (whole EU units, 0 = empty). */
    public static final Supplier<DataComponentType<Integer>> EU_STORED =
            REGISTRY.register("eu_stored", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Chemical formula shown in item tooltip (e.g. "W", "WC", "(Hf,Ta)C"). */
    public static final Supplier<DataComponentType<String>> FORMULA =
            REGISTRY.register("formula", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    /** Assembly program stored on a Microcontroller item (up to 1 MB). */
    public static final Supplier<DataComponentType<String>> PROGRAM =
            REGISTRY.register("program", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.stringUtf8(1_048_576))
                            .build());

    /** Number of general-purpose registers available on a Microcontroller. Default 4 (R0–R3). */
    public static final Supplier<DataComponentType<Integer>> MCU_REGISTERS =
            REGISTRY.register("mcu_registers", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Selected frequency (freqX10) stored on a RadioLocator item. */
    public static final Supplier<DataComponentType<Integer>> RADIO_LOCATOR_FREQ =
            REGISTRY.register("radio_locator_freq", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /**
     * Codec for ByteData backed by NBT ByteArray. Not network-synced — floppy/RAM data stays server-side.
     * Public so block entities can use store/read on ValueOutput/ValueInput directly.
     */
    public static final Codec<ByteData> BYTE_ARRAY_CODEC =
            Codec.BYTE_BUFFER.xmap(
                    buf -> { byte[] a = new byte[buf.remaining()]; buf.duplicate().get(a); return new ByteData(a); },
                    bd -> ByteBuffer.wrap(bd.data()));

    /** Raw data stored on a Floppy Disk item (up to 1 474 560 bytes = 1.44 MB). Not network-synced. */
    public static final Supplier<DataComponentType<ByteData>> FLOPPY_DATA =
            REGISTRY.register("floppy_data", () ->
                    DataComponentType.<ByteData>builder()
                            .persistent(BYTE_ARRAY_CODEC)
                            .build());

    /** Boolean Flag to determine READ-ONLY state of any data storage component */
    public static final Supplier<DataComponentType<Integer>> READ_ONLY =
            REGISTRY.register("read_only", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Capacity of a RAM card in bytes (default 1024). Network-synced for display. */
    public static final Supplier<DataComponentType<Integer>> RAM_CAPACITY =
            REGISTRY.register("ram_capacity", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Data stored on a RAM card. Not network-synced. */
    public static final Supplier<DataComponentType<ByteData>> RAM_DATA =
            REGISTRY.register("ram_data", () ->
                    DataComponentType.<ByteData>builder()
                            .persistent(BYTE_ARRAY_CODEC)
                            .build());

    /** First byte address of this RAM card within the logic machine's address space. */
    public static final Supplier<DataComponentType<Integer>> RAM_ADDR_START =
            REGISTRY.register("ram_addr_start", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Last byte address (inclusive) of this RAM card within the logic machine's address space. */
    public static final Supplier<DataComponentType<Integer>> RAM_ADDR_END =
            REGISTRY.register("ram_addr_end", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Packed truth table state for the Truth Table item (12 bits, one per cell). */
    public static final Supplier<DataComponentType<Integer>> TRUTH_TABLE_BITS =
            REGISTRY.register("truth_table_bits", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Raw firmware binary stored on a Firmware ROM item (up to 1 MB). Not network-synced. */
    public static final Supplier<DataComponentType<ByteData>> ROM_DATA =
            REGISTRY.register("rom_data", () ->
                    DataComponentType.<ByteData>builder()
                            .persistent(BYTE_ARRAY_CODEC)
                            .build());

    /** Research id stored on a Blueprint item (e.g. "d_flip_flop"). Network-synced for tooltip display. */
    public static final Supplier<DataComponentType<String>> RESEARCH_NAME =
            REGISTRY.register("research_name", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    /** Temperature (°C) of a reactor rod while inserted in a reactor cell. Absent when not inserted. */
    public static final Supplier<DataComponentType<Integer>> ROD_TEMPERATURE =
            REGISTRY.register("rod_temperature", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Peak temperature (°C) a fuel rod has ever reached in a reactor. Absent = 0. */
    public static final Supplier<DataComponentType<Integer>> ROD_PEAK_TEMPERATURE =
            REGISTRY.register("rod_peak_temperature", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** Control rod insertion percentage (0–100). Present only on a control rod while inserted. */
    public static final Supplier<DataComponentType<Integer>> ROD_CONTROL =
            REGISTRY.register("rod_control", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /**
     * Temperature (°C) of a fluid stack. Absent = ambient (20 °C).
     * Set on FluidStacks extracted from heated containers (e.g. the reactor coolant tank).
     */
    public static final Supplier<DataComponentType<Integer>> FLUID_TEMPERATURE =
            REGISTRY.register("fluid_temperature", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /**
     * Absolute pressure (kPa) of a fluid stack. Absent = atmospheric (101 kPa).
     * Set by the Rotary Compressor and removed/lowered by the Decompressor.
     * Equalized across connected fluid networks like temperature.
     */
    public static final Supplier<DataComponentType<Integer>> FLUID_PRESSURE =
            REGISTRY.register("fluid_pressure", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** The structural formula sketched on a Structure Table, stored on a Chemical Formula item. */
    public static final Supplier<DataComponentType<com.dev1lroot.mcmods.omnitech.chemistry.Molecule>> MOLECULE =
            REGISTRY.register("molecule", () ->
                    DataComponentType.<com.dev1lroot.mcmods.omnitech.chemistry.Molecule>builder()
                            .persistent(com.dev1lroot.mcmods.omnitech.chemistry.Molecule.CODEC)
                            .networkSynchronized(com.dev1lroot.mcmods.omnitech.chemistry.Molecule.STREAM_CODEC)
                            .build());

    /**
     * Player-chosen name for a Chemical Formula item, overriding the auto-generated IUPAC-style
     * name. Absent = use the auto-generated name.
     */
    public static final Supplier<DataComponentType<String>> MOLECULE_NAME =
            REGISTRY.register("molecule_name", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    /**
     * SMILES structural code of a {@code omnitech:chemical_compound} fluid stack or
     * {@link com.dev1lroot.mcmods.omnitech.items.ChemicalCompoundDustItem} stack — the single
     * source of truth for that stack's identity. Molecular formula, IUPAC name and structure
     * diagram are all derived from this on demand (via {@link com.dev1lroot.mcmods.omnitech.chemistry.SmilesParser}
     * and {@link com.dev1lroot.mcmods.omnitech.chemistry.IupacNamer}), never cached, so they can
     * never go stale — see {@link com.dev1lroot.mcmods.omnitech.chemistry.ChemistryTooltip}.
     */
    public static final Supplier<DataComponentType<String>> SMILES =
            REGISTRY.register("smiles", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
