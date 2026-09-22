/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.Mixture;
import com.dev1lroot.mcmods.omnitech.items.MixtureDustItem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link ItemTintSource} for {@link MixtureDustItem}: the weighted average of every ingredient's
 * colour, weighted by its share of the mixture.
 *
 * <p>Fluids carry no colour of their own (it is baked into their textures), so an ingredient's
 * colour is the average opaque pixel of the first frame of its {@code *_still} texture. Results
 * are cached per resource manager, i.e. dropped whenever resources reload.
 *
 * <p>Register under {@code omnitech:mixture_dust_tint}, reference it as
 * {@code "tints": [{"type": "omnitech:mixture_dust_tint"}]} in the item definition.
 */
public final class MixtureDustTintSource implements ItemTintSource {

    public static final MixtureDustTintSource INSTANCE = new MixtureDustTintSource();
    public static final MapCodec<MixtureDustTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    private static final int FALLBACK = 0xFFB0B0B0;
    private static final int VANILLA_WATER = 0xFF3F76E4;

    private static final Map<Fluid, Integer> CACHE = new HashMap<>();
    private static ResourceManager cachedFor = null;

    private MixtureDustTintSource() {}

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Mixture mixture = MixtureDustItem.getMixture(stack);
        if (mixture.entries().isEmpty()) return FALLBACK;

        long r = 0, g = 0, b = 0, weight = 0;
        for (Mixture.Entry e : mixture.entries()) {
            int c = colorOf(e.fluid());
            r += (long) ((c >> 16) & 0xFF) * e.ppm();
            g += (long) ((c >> 8)  & 0xFF) * e.ppm();
            b += (long) ( c        & 0xFF) * e.ppm();
            weight += e.ppm();
        }
        if (weight <= 0) return FALLBACK;
        return 0xFF000000 | (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
    }

    /** The colour (opaque ARGB) of a fluid: average of its still texture, cached. */
    public static int colorOf(Fluid fluid) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        if (rm != cachedFor) { CACHE.clear(); cachedFor = rm; }
        return CACHE.computeIfAbsent(fluid, f -> sample(rm, f));
    }

    private static int sample(ResourceManager rm, Fluid fluid) {
        Identifier key = BuiltInRegistries.FLUID.getKey(fluid);
        if (key == null) return FALLBACK;
        if (key.equals(Identifier.withDefaultNamespace("water"))) return VANILLA_WATER;

        String ns = key.getNamespace(), path = key.getPath();
        Identifier[] candidates = {
                Identifier.fromNamespaceAndPath(ns, "textures/block/fluid/" + path + "_still.png"),
                Identifier.fromNamespaceAndPath(ns, "textures/block/" + path + "_still.png"),
        };
        for (Identifier tex : candidates) {
            var res = rm.getResource(tex);
            if (res.isEmpty()) continue;
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                return average(img);
            } catch (Exception ignored) {
                // unreadable texture: try the next candidate
            }
        }
        return FALLBACK;
    }

    /** Alpha-weighted average of the first frame (animated textures are a vertical strip of squares). */
    private static int average(NativeImage img) {
        int size = Math.min(img.getWidth(), img.getHeight());
        long r = 0, g = 0, b = 0, a = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int p = img.getPixel(x, y);
                int alpha = (p >>> 24) & 0xFF;
                r += (long) ((p >> 16) & 0xFF) * alpha;
                g += (long) ((p >> 8)  & 0xFF) * alpha;
                b += (long) ( p        & 0xFF) * alpha;
                a += alpha;
            }
        }
        if (a <= 0) return FALLBACK;
        return 0xFF000000 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
