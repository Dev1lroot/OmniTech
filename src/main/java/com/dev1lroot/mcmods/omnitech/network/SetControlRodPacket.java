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

public record SetControlRodPacket(int containerId, int slotIndex, int delta) implements CustomPacketPayload {

    public static final Type<SetControlRodPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "set_control_rod"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetControlRodPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeInt(pkt.containerId);
                        buf.writeInt(pkt.slotIndex);
                        buf.writeInt(pkt.delta);
                    },
                    buf -> new SetControlRodPacket(buf.readInt(), buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetControlRodPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.containerMenu.containerId != pkt.containerId()) return;

            var slots = sp.containerMenu.slots;
            if (pkt.slotIndex() < 0 || pkt.slotIndex() >= slots.size()) return;

            Slot slot = slots.get(pkt.slotIndex());
            ItemStack stack = slot.getItem();
            if (!(stack.getItem() instanceof ReactorControlRodItem)) return;

            int current = ReactorControlRodItem.getControl(stack);
            ReactorControlRodItem.setControl(stack, current + pkt.delta());
            slot.setChanged();
        });
    }
}
