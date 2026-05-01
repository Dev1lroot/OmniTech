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
 * Client → server: direct frequency entry confirmed with Enter.
 * {@code globalKey} encodes band ordinal × 1000 + channel index (see {@link FrequencyBand}).
 */
public record SetRadioFrequencyPacket(BlockPos pos, int globalKey) implements CustomPacketPayload {

    public static final Type<SetRadioFrequencyPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_radio_frequency"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRadioFrequencyPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos);
                        buf.writeInt(pkt.globalKey);
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
                tx.setFrequency(pkt.globalKey);
            } else if (be instanceof RadioReceiverBlockEntity rx) {
                rx.setFrequency(pkt.globalKey);
            }
        });
    }
}
