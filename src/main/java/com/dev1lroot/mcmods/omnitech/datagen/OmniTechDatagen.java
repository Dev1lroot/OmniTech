package com.dev1lroot.mcmods.omnitech.datagen;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.blocks.OmniTechOreBlock;
import com.dev1lroot.mcmods.omnitech.worldgen.OmniTechWorldGen;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

/**
 * Datagen entry point. Reads each ore's {@link OreSpawnConfig} from its block
 * constructor and generates all required worldgen JSON files automatically.
 *
 * <p>Run {@code ./gradlew runData} after changing any spawn parameters.
 *
 * <p><b>Generation structure per ore:</b>
 * <ul>
 *   <li>One (or more) {@code configured_feature} — ore shape and target block</li>
 *   <li>One {@code placed_feature} for the default config</li>
 *   <li>One {@code placed_feature} per biome override</li>
 *   <li>One {@code BiomeModifier} for the default (applies to all matching biomes)</li>
 *   <li>One {@code BiomeModifier} per biome override (additive on top of default)</li>
 * </ul>
 */
public class OmniTechDatagen {

    public static void gatherData(GatherDataEvent.Client event) {
        event.createDatapackRegistryObjects(new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, OmniTechDatagen::bootstrapCF)
            .add(Registries.PLACED_FEATURE,      OmniTechDatagen::bootstrapPF)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, OmniTechDatagen::bootstrapBiomeModifiers));
    }

    // ── ConfiguredFeature ──────────────────────────────────────────────────

    private static void bootstrapCF(BootstrapContext<ConfiguredFeature<?, ?>> ctx) {
        for (DeferredBlock<OmniTechOreBlock> oreBlock : OmniTechBlocks.ALL_ORES) {
            OmniTechOreBlock block = oreBlock.get();
            OreSpawnConfig cfg = block.getSpawnConfig();
            String name = oreBlock.getId().getPath();

            ctx.register(OmniTechWorldGen.cfKey(name), makeCF(block, cfg.defaultConfig().veinSize()));

            for (int i = 0; i < cfg.biomeOverrides().size(); i++) {
                OreSpawnConfig.BiomeOverride override = cfg.biomeOverrides().get(i);
                if (override.veinSize() != cfg.defaultConfig().veinSize()) {
                    ctx.register(OmniTechWorldGen.cfOverrideKey(name, i), makeCF(block, override.veinSize()));
                }
            }
        }
    }

    private static ConfiguredFeature<?, ?> makeCF(OmniTechOreBlock block, int veinSize) {
        return new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(
            List.of(OreConfiguration.target(
                new BlockMatchTest(Blocks.STONE),
                block.defaultBlockState()
            )),
            veinSize
        ));
    }

    // ── PlacedFeature ──────────────────────────────────────────────────────

    private static void bootstrapPF(BootstrapContext<PlacedFeature> ctx) {
        HolderGetter<ConfiguredFeature<?, ?>> cfGetter = ctx.lookup(Registries.CONFIGURED_FEATURE);

        for (DeferredBlock<OmniTechOreBlock> oreBlock : OmniTechBlocks.ALL_ORES) {
            OmniTechOreBlock block = oreBlock.get();
            OreSpawnConfig cfg = block.getSpawnConfig();
            String name = oreBlock.getId().getPath();

            ctx.register(OmniTechWorldGen.pfKey(name),
                makePF(cfGetter.getOrThrow(OmniTechWorldGen.cfKey(name)), cfg.defaultConfig()));

            for (int i = 0; i < cfg.biomeOverrides().size(); i++) {
                OreSpawnConfig.BiomeOverride override = cfg.biomeOverrides().get(i);
                boolean ownCF = override.veinSize() != cfg.defaultConfig().veinSize();
                ResourceKey<ConfiguredFeature<?, ?>> cfKey = ownCF
                    ? OmniTechWorldGen.cfOverrideKey(name, i)
                    : OmniTechWorldGen.cfKey(name);
                ctx.register(OmniTechWorldGen.pfOverrideKey(name, i),
                    makePF(cfGetter.getOrThrow(cfKey), override));
            }
        }
    }

    private static PlacedFeature makePF(net.minecraft.core.Holder<ConfiguredFeature<?, ?>> cfHolder,
                                        OreSpawnConfig.SpawnParams params) {
        return new PlacedFeature(cfHolder, List.of(
            CountPlacement.of(UniformInt.of(params.minCount(), params.maxCount())),
            InSquarePlacement.spread(),
            HeightRangePlacement.uniform(
                VerticalAnchor.absolute(params.minY()),
                VerticalAnchor.absolute(params.maxY())
            ),
            BiomeFilter.biome()
        ));
    }

    // ── BiomeModifiers ─────────────────────────────────────────────────────

    private static void bootstrapBiomeModifiers(BootstrapContext<BiomeModifier> ctx) {
        HolderGetter<Biome>        biomeGetter   = ctx.lookup(Registries.BIOME);
        HolderGetter<PlacedFeature> featureGetter = ctx.lookup(Registries.PLACED_FEATURE);

        for (DeferredBlock<OmniTechOreBlock> oreBlock : OmniTechBlocks.ALL_ORES) {
            OreSpawnConfig cfg  = oreBlock.get().getSpawnConfig();
            String         name = oreBlock.getId().getPath();

            ctx.register(OmniTechWorldGen.bmKey(name),
                addFeatures(biomeGetter, featureGetter, cfg.defaultConfig(),
                    OmniTechWorldGen.pfKey(name)));

            for (int i = 0; i < cfg.biomeOverrides().size(); i++) {
                ctx.register(OmniTechWorldGen.bmOverrideKey(name, i),
                    addFeatures(biomeGetter, featureGetter, cfg.biomeOverrides().get(i),
                        OmniTechWorldGen.pfOverrideKey(name, i)));
            }
        }
    }

    private static BiomeModifiers.AddFeaturesBiomeModifier addFeatures(
            HolderGetter<Biome> biomeGetter,
            HolderGetter<PlacedFeature> featureGetter,
            OreSpawnConfig.SpawnParams params,
            ResourceKey<PlacedFeature> pfKey) {
        return new BiomeModifiers.AddFeaturesBiomeModifier(
            resolveBiomes(biomeGetter, params.biomes()),
            HolderSet.direct(featureGetter.getOrThrow(pfKey)),
            GenerationStep.Decoration.UNDERGROUND_ORES
        );
    }

    /**
     * Parses a biome selector string into a {@link HolderSet}.
     * Strings starting with {@code #} are treated as biome tags; others as direct biome IDs.
     */
    private static HolderSet<Biome> resolveBiomes(HolderGetter<Biome> getter, String selector) {
        if (selector.startsWith("#")) {
            TagKey<Biome> tag = TagKey.create(Registries.BIOME,
                Identifier.parse(selector.substring(1)));
            return getter.getOrThrow(tag);
        }
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(selector));
        return HolderSet.direct(getter.getOrThrow(key));
    }
}
