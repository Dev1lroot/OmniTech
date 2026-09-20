/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: draw-amount change (1–{@value PipetteItem#MAX_AMOUNT} mB) for the held
 * Pipette Dispenser item, set from {@link com.dev1lroot.mcmods.omnitech.gui.PipetteAmountScreen}.
 */
public record SetPipetteAmountPacket(int amount, int hand) implements CustomPacketPayload {

    public static final Type<SetPipetteAmountPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_pipette_amount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPipetteAmountPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeInt(pkt.amount); buf.writeInt(pkt.hand); },
                    buf -> new SetPipetteAmountPacket(buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetPipetteAmountPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            InteractionHand hand = pkt.hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = sp.getItemInHand(hand);
            if (stack.getItem() instanceof PipetteItem) {
                PipetteItem.setTargetAmount(stack, pkt.amount());
            }
        });
    }
}
