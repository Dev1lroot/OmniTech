/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sets every control rod in the open reactor menu to 100% insertion. */
public record ScramReactorPacket(int containerId) implements CustomPacketPayload {

    public static final Type<ScramReactorPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "scram_reactor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScramReactorPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeInt(pkt.containerId),
                    buf -> new ScramReactorPacket(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScramReactorPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.containerMenu.containerId != pkt.containerId()) return;

            for (Slot slot : sp.containerMenu.slots) {
                ItemStack stack = slot.getItem();
                if (stack.getItem() instanceof ReactorControlRodItem) {
                    ReactorControlRodItem.setControl(stack, 100);
                    slot.setChanged();
                }
            }
        });
    }
}
