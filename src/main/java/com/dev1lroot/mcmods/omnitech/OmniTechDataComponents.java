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

import java.util.function.Supplier;

public class OmniTechDataComponents {

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

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
