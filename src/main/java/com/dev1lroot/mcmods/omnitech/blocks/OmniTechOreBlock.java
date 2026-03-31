package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class OmniTechOreBlock extends DropExperienceBlock {
    private final OreSpawnConfig spawnConfig;

    /**
     * @param props       block behaviour properties (strength, sound, map color, etc.)
     * @param spawnConfig world generation parameters — passed here so adding a new ore
     *                    is a single-class change; run {@code ./gradlew runData} after
     *                    changing values to regenerate the worldgen JSON files.
     */
    public OmniTechOreBlock(BlockBehaviour.Properties props, OreSpawnConfig spawnConfig) {
        super(UniformInt.of(0, 2), props);
        this.spawnConfig = spawnConfig;
    }

    public OreSpawnConfig getSpawnConfig() {
        return spawnConfig;
    }
}
