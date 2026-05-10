package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.client.KeyboardCaptureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → Client: start or stop keyboard capture mode. */
public record KeyboardModePacket(boolean active, BlockPos logicMachinePos)
        implements CustomPacketPayload {

    public static final Type<KeyboardModePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "keyboard_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeyboardModePacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.active);
                        buf.writeBlockPos(pkt.logicMachinePos);
                    },
                    buf -> new KeyboardModePacket(buf.readBoolean(), buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KeyboardModePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (pkt.active()) {
                KeyboardCaptureManager.activate(pkt.logicMachinePos());
            } else {
                KeyboardCaptureManager.deactivateLocal();
            }
        });
    }
}
