/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.gui.RadioScannerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client: one waterfall row for a specific band.
 * Signal values are encoded as unsigned bytes (0 = no signal, 255 = max).
 * Row length equals {@link FrequencyBand#channels()} for the given band.
 */
public record RadioScannerRowPacket(int bandOrdinal, float[] row) implements CustomPacketPayload {

    public static final Type<RadioScannerRowPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "radio_scanner_row"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioScannerRowPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeByte(pkt.bandOrdinal);
                        for (float v : pkt.row) {
                            buf.writeByte((int) Math.clamp(v / 15f * 255f, 0, 255));
                        }
                    },
                    buf -> {
                        int ord = buf.readUnsignedByte();
                        FrequencyBand[] vals = FrequencyBand.values();
                        FrequencyBand band = (ord >= 0 && ord < vals.length) ? vals[ord] : FrequencyBand.VHF;
                        float[] row = new float[band.channels()];
                        for (int i = 0; i < band.channels(); i++) {
                            row[i] = (buf.readUnsignedByte() / 255f) * 15f;
                        }
                        return new RadioScannerRowPacket(ord, row);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RadioScannerRowPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RadioScannerScreen scanner) {
                scanner.receiveRow(pkt.bandOrdinal(), pkt.row());
            }
        });
    }
}
