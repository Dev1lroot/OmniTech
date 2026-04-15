package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
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
public class OmniTechFluids {
    public static final DeferredRegister<Fluid> REGISTRY =
            DeferredRegister.create(Registries.FLUID, OmniTech.MODID);
    public static final DeferredRegister<FluidType> TYPE_REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, OmniTech.MODID);

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
    static void registerFluid(String name, FluidType.Properties props) {
        if (FLUIDS.containsKey(name)) {
            OmniTech.LOGGER.warn("[OmniTechFluids] Duplicate fluid ignored: '{}'", name);
            return;
        }
        FLUIDS.put(name, new FluidObject(name, props));
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

        FluidObject(String name, FluidType.Properties typeProps) {
            this.name = name;
            this.type = TYPE_REGISTRY.register(name,
                    () -> new FluidType(typeProps.descriptionId("fluid.omnitech." + name)));
            this.source = REGISTRY.register(name,
                    () -> new BaseFlowingFluid.Source(this.makeProperties()));
            this.flowing = REGISTRY.register("flowing_" + name,
                    () -> new BaseFlowingFluid.Flowing(this.makeProperties()));
        }

        private BaseFlowingFluid.Properties makeProperties() {
            return new BaseFlowingFluid.Properties(type, source, flowing);
        }
    }

    public static void register(IEventBus modEventBus) {
        TYPE_REGISTRY.register(modEventBus);
        REGISTRY.register(modEventBus);
    }
}
