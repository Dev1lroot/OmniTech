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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: scroll-wheel draw-amount change for a {@link PipetteItem} hovered in
 * <em>any</em> open inventory slot (the player's own inventory, a chest, a machine GUI, ...) —
 * mirrors {@link SetControlRodPacket}'s slot-targeting exactly, just for the pipette instead of
 * a reactor cell's control rod.
 */
public record SetPipetteAmountSlotPacket(int containerId, int slotIndex, int delta) implements CustomPacketPayload {

    public static final Type<SetPipetteAmountSlotPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_pipette_amount_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPipetteAmountSlotPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeInt(pkt.containerId);
                        buf.writeInt(pkt.slotIndex);
                        buf.writeInt(pkt.delta);
                    },
                    buf -> new SetPipetteAmountSlotPacket(buf.readInt(), buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetPipetteAmountSlotPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.containerMenu.containerId != pkt.containerId()) return;

            var slots = sp.containerMenu.slots;
            if (pkt.slotIndex() < 0 || pkt.slotIndex() >= slots.size()) return;

            Slot slot = slots.get(pkt.slotIndex());
            ItemStack stack = slot.getItem();
            if (!(stack.getItem() instanceof PipetteItem)) return;

            int current = PipetteItem.getTargetAmount(stack);
            PipetteItem.setTargetAmount(stack, current + pkt.delta());
            slot.setChanged();
        });
    }
}
