package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.items.RadioLocatorItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: frequency change for the held Radio Locator item.
 * {@code globalKey} encodes band × 1000 + channel index.
 */
public record SetRadioLocatorFreqPacket(int globalKey, int hand) implements CustomPacketPayload {

    public static final Type<SetRadioLocatorFreqPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_radio_locator_freq"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRadioLocatorFreqPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeInt(pkt.globalKey); buf.writeInt(pkt.hand); },
                    buf -> new SetRadioLocatorFreqPacket(buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetRadioLocatorFreqPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            InteractionHand hand = pkt.hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = sp.getItemInHand(hand);
            if (stack.getItem() instanceof RadioLocatorItem) {
                // Validate that the key maps to a real band + channel
                FrequencyBand band = FrequencyBand.fromGlobalKey(pkt.globalKey);
                int ch = Math.clamp(FrequencyBand.channelOf(pkt.globalKey), 0, band.channels() - 1);
                stack.set(OmniTechDataComponents.RADIO_LOCATOR_FREQ.get(), band.globalKey(ch));
            }
        });
    }
}
