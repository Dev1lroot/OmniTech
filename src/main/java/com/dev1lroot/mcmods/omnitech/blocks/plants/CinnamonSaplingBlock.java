/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plants;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Sapling for the cinnamon tree. {@link SaplingBlock}'s constructor is
 * protected, so this subclass exists purely to expose it; growth is driven
 * entirely by {@link #CINNAMON_GROWER}, which points at the
 * {@code omnitech:cinnamon_tree} configured feature (see
 * {@code data/omnitech/worldgen/feature/cinnamon_tree.json}).
 */
public class CinnamonSaplingBlock extends SaplingBlock {

    public static final ResourceKey<Feature> CINNAMON_TREE_KEY = ResourceKey.create(
            Registries.FEATURE, Identifier.fromNamespaceAndPath(OmniTech.MODID, "cinnamon_tree"));

    private static final TreeGrower CINNAMON_GROWER = new TreeGrower(
            "omnitech_cinnamon",
            WeightedList.of(CINNAMON_TREE_KEY),
            WeightedList.of(),
            WeightedList.of(),
            CINNAMON_TREE_KEY
    );

    public CinnamonSaplingBlock(BlockBehaviour.Properties properties) {
        super(CINNAMON_GROWER, properties);
    }
}
