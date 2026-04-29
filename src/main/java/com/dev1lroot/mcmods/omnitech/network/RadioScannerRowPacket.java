package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.dev1lroot.mcmods.omnitech.gui.RadioScannerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server → client every {@code SEND_INTERVAL} ticks while the player has
 * the Radio Scanner GUI open.  Carries one row of FM-band signal strengths
 * (300 channels, 87.5–117.4 MHz) encoded as unsigned bytes (0=no signal, 255=max).
 */
public record RadioScannerRowPacket(float[] row) implements CustomPacketPayload {

    public static final Type<RadioScannerRowPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "radio_scanner_row"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioScannerRowPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        for (float v : pkt.row) {
                            buf.writeByte((int) Math.clamp(v / 15f * 255f, 0, 255));
                        }
                    },
                    buf -> {
                        float[] row = new float[RadioConstants.CHANNELS];
                        for (int i = 0; i < RadioConstants.CHANNELS; i++) {
                            row[i] = (buf.readUnsignedByte() / 255f) * 15f;
                        }
                        return new RadioScannerRowPacket(row);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Client-side handler: pushes the row into the currently open scanner screen. */
    public static void handle(RadioScannerRowPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RadioScannerScreen scanner) {
                scanner.receiveRow(pkt.row());
            }
        });
    }
}
