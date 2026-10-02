/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb.mc;

import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistence and network codecs for the (Minecraft-free) PCB model. */
public final class PcbCodecs {

    private PcbCodecs() {}

    /** One digit per cell (flags 0–7), row-major over the full grid. */
    private static String encodeCells(byte[] cells) {
        StringBuilder sb = new StringBuilder(cells.length);
        for (byte b : cells) sb.append((char) ('0' + (b & 7)));
        return sb.toString();
    }

    private static byte[] decodeCells(String s) {
        byte[] out = new byte[PcbDesign.CELLS];
        for (int i = 0; i < Math.min(s.length(), out.length); i++) out[i] = (byte) ((s.charAt(i) - '0') & 7);
        return out;
    }

    private static Map<String, String> labelsOut(Map<Integer, String> labels) {
        Map<String, String> m = new HashMap<>();
        labels.forEach((k, v) -> m.put(String.valueOf(k), v));
        return m;
    }

    private static Map<Integer, String> labelsIn(Map<String, String> labels) {
        Map<Integer, String> m = new HashMap<>();
        labels.forEach((k, v) -> {
            try { m.put(Integer.parseInt(k), v); } catch (NumberFormatException ignored) {}
        });
        return m;
    }

    public static final Codec<PcbDesign> DESIGN = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(PcbDesign::name),
            Codec.INT.fieldOf("width").forGetter(PcbDesign::width),
            Codec.INT.fieldOf("height").forGetter(PcbDesign::height),
            Codec.STRING.fieldOf("cells").forGetter(d -> encodeCells(d.cells())),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("labels", Map.of())
                    .forGetter(d -> labelsOut(d.labels()))
    ).apply(i, (name, w, h, cells, labels) -> new PcbDesign(name, w, h, decodeCells(cells), labelsIn(labels))));

    public static final StreamCodec<ByteBuf, PcbDesign> DESIGN_STREAM = new StreamCodec<>() {
        @Override
        public PcbDesign decode(ByteBuf raw) {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            String name = buf.readUtf(PcbDesign.MAX_NAME * 4);
            int w = buf.readVarInt(), h = buf.readVarInt();
            byte[] cells = new byte[PcbDesign.CELLS];
            buf.readBytes(cells);
            Map<Integer, String> labels = new HashMap<>();
            int n = buf.readVarInt();
            for (int i = 0; i < n && i < PcbDesign.CELLS; i++) labels.put(buf.readVarInt(), buf.readUtf(8));
            return new PcbDesign(name, w, h, cells, labels);
        }

        @Override
        public void encode(ByteBuf raw, PcbDesign d) {
            FriendlyByteBuf buf = new FriendlyByteBuf(raw);
            buf.writeUtf(d.name(), PcbDesign.MAX_NAME * 4);
            buf.writeVarInt(d.width());
            buf.writeVarInt(d.height());
            buf.writeBytes(d.cells());
            buf.writeVarInt(d.labels().size());
            d.labels().forEach((k, v) -> {
                buf.writeVarInt(k);
                buf.writeUtf(v, 8);
            });
        }
    };

    public static final Codec<PlacedPart> PART = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(PlacedPart::itemId),
            Codec.INT.fieldOf("x").forGetter(PlacedPart::x),
            Codec.INT.fieldOf("y").forGetter(PlacedPart::y),
            Codec.INT.optionalFieldOf("rot", 0).forGetter(PlacedPart::rot),
            Codec.INT.listOf(0, com.dev1lroot.mcmods.omnitech.pcb.ResistorCode.MAX_BANDS)
                    .optionalFieldOf("bands", List.of()).forGetter(PlacedPart::bands)
    ).apply(i, PlacedPart::new));

    public static final Codec<List<PlacedPart>> PARTS = PART.listOf();

    public static final StreamCodec<ByteBuf, PlacedPart> PART_STREAM = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlacedPart::itemId,
            ByteBufCodecs.VAR_INT, PlacedPart::x,
            ByteBufCodecs.VAR_INT, PlacedPart::y,
            ByteBufCodecs.VAR_INT, PlacedPart::rot,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(com.dev1lroot.mcmods.omnitech.pcb.ResistorCode.MAX_BANDS)), PlacedPart::bands,
            PlacedPart::new);

    /** Soldering plans are capped well below anything a board can physically hold. */
    public static final int MAX_PARTS = 64;

    public static final StreamCodec<ByteBuf, List<PlacedPart>> PARTS_STREAM =
            PART_STREAM.apply(ByteBufCodecs.list(MAX_PARTS));
}
