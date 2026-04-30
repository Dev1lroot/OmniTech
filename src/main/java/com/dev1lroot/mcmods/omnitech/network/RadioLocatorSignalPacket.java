package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.client.RadioLocatorHudOverlay;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RadioLocatorSignalPacket(float signal) implements CustomPacketPayload {

    public static final Type<RadioLocatorSignalPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "radio_locator_signal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioLocatorSignalPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeFloat(pkt.signal),
                    buf -> new RadioLocatorSignalPacket(buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RadioLocatorSignalPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RadioLocatorHudOverlay.setLastSignal(pkt.signal));
    }
}
