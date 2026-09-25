/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every player's gravity frame — the rotation their whole body (and view) is turned by, relative to
 * the world: tilted onto an asteroid, rolled in zero-g. The local client owns its own frame
 * ({@code OmniTechClient.cameraGravityQ}) and reports it with {@code PlayerFramePacket}; the
 * server keeps it here so server-side aiming (item raycasts, projectiles) matches what the player
 * sees, and relays it to other clients so they render the player turned the same way.
 *
 * <p>No entry = upright (identity).
 */
public final class PlayerFrames {

    /** |w| above this (a rotation under ~0.1°) counts as upright; q and −q are the same rotation. */
    private static final float UPRIGHT_MIN_W = 0.9999995f;

    private static final Map<UUID, Quaternionf> SERVER = new ConcurrentHashMap<>();
    private static final Map<Integer, Quaternionf> CLIENT_REMOTE = new ConcurrentHashMap<>();

    private PlayerFrames() {}

    public static boolean isUpright(Quaternionf q) {
        return Math.abs(q.w) > UPRIGHT_MIN_W;
    }

    // ── Server side ───────────────────────────────────────────────────────────

    public static void setServer(UUID player, @Nullable Quaternionf frame) {
        if (frame == null || isUpright(frame)) SERVER.remove(player);
        else SERVER.put(player, new Quaternionf(frame));
    }

    public static @Nullable Quaternionf getServer(UUID player) {
        Quaternionf q = SERVER.get(player);
        return q == null ? null : new Quaternionf(q);
    }

    // ── Client side: the local player's own frame ─────────────────────────────

    private static volatile int localPlayerId = Integer.MIN_VALUE;
    private static final Quaternionf LOCAL = new Quaternionf();

    /** Published every frame by the client's camera code. */
    public static synchronized void setLocal(int entityId, Quaternionf frame) {
        localPlayerId = entityId;
        LOCAL.set(frame);
    }

    public static int localPlayerId() { return localPlayerId; }

    public static synchronized @Nullable Quaternionf getLocal() {
        return isUpright(LOCAL) ? null : new Quaternionf(LOCAL);
    }

    // ── Client side: other players, as relayed by the server ──────────────────

    public static void setRemote(int entityId, @Nullable Quaternionf frame) {
        if (frame == null || isUpright(frame)) CLIENT_REMOTE.remove(entityId);
        else CLIENT_REMOTE.put(entityId, new Quaternionf(frame));
    }

    public static @Nullable Quaternionf getRemote(int entityId) {
        Quaternionf q = CLIENT_REMOTE.get(entityId);
        return q == null ? null : new Quaternionf(q);
    }

    public static synchronized void clearClient() {
        CLIENT_REMOTE.clear();
        LOCAL.identity();
        localPlayerId = Integer.MIN_VALUE;
    }
}
