/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.radiation;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RadiationSavedData extends SavedData {

    public static final int BLOCKS_PER_TICK = 14_000;

    private static final Codec<RadiationSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.listOf().fieldOf("centers").forGetter(d -> d.centers),
                    Codec.unboundedMap(
                            Codec.STRING,
                            PlayerRadData.CODEC
                    ).fieldOf("playerData").forGetter(d -> {
                        Map<String, PlayerRadData> out = new HashMap<>();
                        d.playerData.forEach((uuid, v) -> out.put(uuid.toString(), v));
                        return out;
                    })
            ).apply(instance, (centers, map) -> {
                RadiationSavedData data = new RadiationSavedData();
                data.centers.addAll(centers);
                map.forEach((s, v) -> data.playerData.put(UUID.fromString(s), v));
                return data;
            })
    );

    public static final SavedDataType<RadiationSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "radiation"),
            ignored -> new RadiationSavedData(),
            ignored -> CODEC,
            null
    );

    final List<BlockPos>            centers      = new ArrayList<>();
    final Map<UUID, PlayerRadData>  playerData   = new HashMap<>();
    private final Deque<BlockPos>   pending      = new ArrayDeque<>();
    // Not persisted — max lifetime is 220 ticks (11s); server restarts in that window are acceptable
    private final List<PendingZoneEvent> pendingZones = new ArrayList<>();

    record PendingZoneEvent(long fireTick, int zone, BlockPos center) {}

    public static RadiationSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<BlockPos> getCenters() {
        return centers;
    }

    public void addCenter(BlockPos center) {
        centers.add(center.immutable());
        setDirty();
    }

    public void scheduleZone(int zone, BlockPos center, long fireTick) {
        pendingZones.add(new PendingZoneEvent(fireTick, zone, center));
    }

    /** Queue block positions for gradual destruction (zone 2). */
    public void queueExplosion(List<BlockPos> blocks) {
        pending.addAll(blocks);
    }

    /** Called from RadiationTick every server tick. Returns true if work was done. */
    public boolean tick(ServerLevel level) {
        long now = level.getGameTime();
        pendingZones.removeIf(event -> {
            if (now < event.fireTick()) return false;
            switch (event.zone()) {
                case 1 -> NuclearExplosion.executeZone1(level, event.center());
                case 2 -> NuclearExplosion.executeZone2(level, event.center(), this);
                case 3 -> NuclearExplosion.spawnZone3Tnt(level, event.center());
                case 4 -> NuclearExplosion.applyBiomeChange(level, event.center());
                case 5 -> NuclearExplosion.executeLeafStrip(level, event.center(), this);
            }
            return true;
        });

        if (pending.isEmpty()) return false;
        int processed = 0;
        while (!pending.isEmpty() && processed < BLOCKS_PER_TICK) {
            BlockPos pos = pending.poll();
            if (!level.getBlockState(pos).isAir()
                    && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                        | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS);
            }
            processed++;
        }
        setDirty();
        return true;
    }

    // ── Player health reduction data ──────────────────────────────────────────

    public PlayerRadData getOrCreate(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> new PlayerRadData());
    }

    public void removePlayer(UUID uuid) {
        if (playerData.remove(uuid) != null) setDirty();
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    public static class PlayerRadData {
        public static final Codec<PlayerRadData> CODEC = RecordCodecBuilder.create(i ->
                i.group(
                        Codec.LONG.fieldOf("lastDmgTick").forGetter(d -> d.lastHealthDmgTick),
                        Codec.INT.fieldOf("totalReduction").forGetter(d -> d.totalReduction)
                ).apply(i, (tick, red) -> {
                    PlayerRadData d = new PlayerRadData();
                    d.lastHealthDmgTick = tick;
                    d.totalReduction    = red;
                    return d;
                })
        );

        public long lastHealthDmgTick = -1;
        public int  totalReduction    = 0;
    }
}
