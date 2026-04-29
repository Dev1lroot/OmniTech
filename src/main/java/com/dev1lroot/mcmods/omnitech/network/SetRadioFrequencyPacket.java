package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_receiver.RadioReceiverBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_transmitter.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent client → server when the player types a frequency directly into the
 * EditBox and confirms with Enter.  Handles both transmitter and receiver GUIs.
 */
public record SetRadioFrequencyPacket(BlockPos pos, int freqX10) implements CustomPacketPayload {

    public static final Type<SetRadioFrequencyPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_radio_frequency"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRadioFrequencyPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeInt(pkt.freqX10);
                    },
                    buf -> new SetRadioFrequencyPacket(buf.readBlockPos(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetRadioFrequencyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockEntity be = sp.level().getBlockEntity(pkt.pos);
            if (be instanceof RadioTransmitterBlockEntity tx) {
                tx.setFrequency(pkt.freqX10);
            } else if (be instanceof RadioReceiverBlockEntity rx) {
                rx.setFrequency(pkt.freqX10);
            }
        });
    }
}
