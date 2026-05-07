package com.dev1lroot.mcmods.omnitech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.UUIDUtil;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
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

    /** Execution speed of a Microcontroller in Hz (instructions per second). Default 20 = 1 per tick. */
    public static final Supplier<DataComponentType<Integer>> MCU_SPEED =
            REGISTRY.register("mcu_speed", () ->
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

    /** UUID identifying the kernel binary for this Microcontroller.
     *  Actual bytes live in {@code <world>/omnitech/<uuid>.bin}. */
    public static final Supplier<DataComponentType<UUID>> PROGRAM_BINARY =
            REGISTRY.register("program_binary", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC)
                            .build());

    /** UUID identifying the data image for this Floppy Disk.
     *  Actual bytes live in {@code <world>/omnitech/<uuid>.bin}. */
    public static final Supplier<DataComponentType<UUID>> FLOPPY_DATA =
            REGISTRY.register("floppy_data", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC)
                            .build());

    /** Capacity of a RAM card in bytes (default 1024). Network-synced for display. */
    public static final Supplier<DataComponentType<Integer>> RAM_CAPACITY =
            REGISTRY.register("ram_capacity", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build());

    /** UUID identifying the saved contents of this RAM card.
     *  Actual bytes live in {@code <world>/omnitech/<uuid>.bin}. */
    public static final Supplier<DataComponentType<UUID>> RAM_DATA =
            REGISTRY.register("ram_data", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC)
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

    /** Console output buffer for a running Microcontroller (up to 128 KB). Network-synced for client display. */
    public static final Supplier<DataComponentType<String>> CONSOLE_OUTPUT =
            REGISTRY.register("console_output", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.stringUtf8(131_072))
                            .build());

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
