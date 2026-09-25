/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.GravityUtil;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a player's model is turned: their gravity frame, about a pivot height above the feet (the
 * body centre in a gravity field, the head while floating free — see
 * {@link GravityUtil#pivotHeight}). Attached to each player's render state every frame and applied
 * by {@code EntityGravityRenderMixin}.
 *
 * <p>Other players' frames arrive over the network a few times a second; the drawn frame eases
 * toward the latest one so their rolls look smooth instead of stepping.
 */
public record GravityPose(Quaternionf frame, float pivot) {

    public static final ContextKey<GravityPose> KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "gravity_pose"));

    private static final Map<Integer, Quaternionf> DRAWN = new ConcurrentHashMap<>();

    /** Render-state modifier for every player-like renderer. */
    public static void extract(Player player, AvatarRenderState state) {
        Quaternionf target = GravityUtil.frameOf(player);
        boolean local = player.getId() == com.dev1lroot.mcmods.omnitech.util.PlayerFrames.localPlayerId();
        if (target == null) {
            DRAWN.remove(player.getId());
            state.setRenderData(KEY, null);
            return;
        }
        // The local frame is already smooth (it is the camera's); smooth only relayed ones
        Quaternionf drawn = local ? target
                : DRAWN.computeIfAbsent(player.getId(), id -> new Quaternionf(target)).slerp(target, 0.3f).normalize();
        float pivot = GravityUtil.pivotHeight(player, player.getEyeHeight(), GravityUtil.fieldStrength(player));
        state.setRenderData(KEY, new GravityPose(new Quaternionf(drawn), pivot));
    }

    public static void clear() {
        DRAWN.clear();
    }
}
