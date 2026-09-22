/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.fluid.FlammableLiquidBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluid registry for OmniTech.
 *
 * <p>Fluids are no longer declared as static fields. Instead, {@link FluidLoader}
 * reads {@code data/omnitech/fluid/<name>.json} at startup and calls
 * {@link #registerFluid} for each one. Callers that need a specific fluid
 * use {@link #get(String)}.
 *
 * <p>Call order in the mod constructor:
 * <ol>
 *   <li>{@code FluidLoader.loadAll()} — populates entries into the DeferredRegisters</li>
 *   <li>{@code OmniTechFluids.register(modEventBus)} — attaches registers to the event bus</li>
 * </ol>
 */
public class OmniTechFluids
{
    public static final DeferredRegister<Fluid> REGISTRY =
            DeferredRegister.create(Registries.FLUID, OmniTech.MODID);
    public static final DeferredRegister<FluidType> TYPE_REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, OmniTech.MODID);
    /** Companion world blocks for fluids marked {@code "world_placeable": true}. */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(OmniTech.MODID);
    /** Companion vanilla-style buckets for world-placeable fluids — see {@link FluidObject}. */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OmniTech.MODID);

    private static final Map<String, FluidObject> FLUIDS = new LinkedHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of all registered fluids, keyed by registry name.
     * Insertion order (alphabetical within JSON load order) is preserved.
     */
    public static Map<String, FluidObject> all() {
        return Collections.unmodifiableMap(FLUIDS);
    }

    /**
     * Look up a fluid by registry name. Returns {@code null} if not found.
     *
     * <p>Example: {@code OmniTechFluids.get("steam").source.get()}
     */
    public static FluidObject get(String name) {
        return FLUIDS.get(name);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Called by {@link FluidLoader} for each JSON file. Package-private — not
     * intended for use outside the loading pipeline.
     */
    static void registerFluid(String name, FluidType.Properties props, boolean worldPlaceable, boolean flammable) {
        if (FLUIDS.containsKey(name)) {
            OmniTech.LOGGER.warn("[OmniTechFluids] Duplicate fluid ignored: '{}'", name);
            return;
        }
        // A bucket only makes sense for a fluid that can also be placed in the world
        // (BucketItem.emptyContents needs the fluid's companion block to exist).
        FLUIDS.put(name, new FluidObject(name, props, worldPlaceable, worldPlaceable, flammable));
    }

    // ── FluidObject ───────────────────────────────────────────────────────────

    /**
     * Holds the three deferred holders that make up one logical fluid:
     * the {@link FluidType}, the source {@link Fluid}, and the flowing {@link Fluid}.
     */
    public static class FluidObject {
        /** Registry name (e.g. {@code "steam"}). */
        public final String name;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, Fluid> source;
        public final DeferredHolder<Fluid, Fluid> flowing;
        /** Companion world block, or {@code null} when not {@code world_placeable}. */
        public final DeferredBlock<LiquidBlock> block;
        /** Companion vanilla-style bucket ({@code <name>_bucket}), or {@code null} when not bucketable. */
        public final DeferredItem<BucketItem> bucket;

        FluidObject(String name, FluidType.Properties typeProps, boolean worldPlaceable,
                    boolean bucketable, boolean flammable) {
            this.name = name;
            // The generic chemical_compound fluid has no fixed identity — every instance's real
            // name comes from its own SMILES component instead of a static lang key — so it
            // needs the dynamic-name FluidType subclass; every other fluid keeps the plain one.
            this.type = TYPE_REGISTRY.register(name,
                    name.equals("chemical_compound")
                            ? () -> new ChemicalCompoundFluidType(typeProps.descriptionId("fluid.omnitech." + name))
                            : () -> new FluidType(typeProps.descriptionId("fluid.omnitech." + name)));
            this.source = REGISTRY.register(name,
                    () -> new BaseFlowingFluid.Source(this.makeProperties()));
            this.flowing = REGISTRY.register("flowing_" + name,
                    () -> new BaseFlowingFluid.Flowing(this.makeProperties()));
            this.block = worldPlaceable
                    ? BLOCKS.registerBlock(name,
                            p -> flammable
                                    ? new FlammableLiquidBlock((FlowingFluid) source.get(), p)
                                    : new LiquidBlock((FlowingFluid) source.get(), p),
                            () -> {
                                var p = BlockBehaviour.Properties.of()
                                        .mapColor(MapColor.COLOR_BLACK)
                                        .replaceable()
                                        .noCollision()
                                        .strength(100.0F)
                                        .pushReaction(PushReaction.POPPED)
                                        .noLootTable()
                                        .liquid()
                                        .sound(SoundType.EMPTY);
                                // Flammable fluids need random ticks to scan for nearby fire —
                                // see FlammableLiquidBlock.
                                return flammable ? p.randomTicks() : p;
                            })
                    : null;
            // Fluid registration (this class) runs before item registration in the mod
            // constructor, so `source` is resolved by the time this factory lambda fires.
            this.bucket = bucketable
                    ? ITEMS.registerItem(name + "_bucket",
                            p -> new OmniTechBucketItem(source.get(), p.stacksTo(1)))
                    : null;
        }

        private BaseFlowingFluid.Properties makeProperties() {
            BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(type, source, flowing);
            if (block != null) props = props.block(block::get);
            return props;
        }
    }

    public static void register(IEventBus modEventBus) {
        TYPE_REGISTRY.register(modEventBus);
        REGISTRY.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
