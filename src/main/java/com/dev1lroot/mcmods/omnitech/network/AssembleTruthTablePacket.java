package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.items.TruthTableItem;
import com.dev1lroot.mcmods.omnitech.util.LogicGate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: sent when the player interacts with the Truth Table GUI.
 * Always saves the current bit state to the item. If {@code doAssemble} is
 * true, also attempts to convert the item into a Logic Gate Template.
 */
public record AssembleTruthTablePacket(int bits, boolean doAssemble) implements CustomPacketPayload {

    public static final Type<AssembleTruthTablePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "assemble_truth_table"));

    public static final StreamCodec<ByteBuf, AssembleTruthTablePacket> CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeShort(pkt.bits & 0xFFF);
                buf.writeBoolean(pkt.doAssemble);
            },
            buf -> new AssembleTruthTablePacket(buf.readShort() & 0xFFF, buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AssembleTruthTablePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            ItemStack stack = findTruthTable(sp);
            if (stack == null || stack.isEmpty()) return;

            // Always persist the current truth table state
            stack.set(OmniTechDataComponents.TRUTH_TABLE_BITS.get(), pkt.bits);

            if (!pkt.doAssemble) return;

            LogicGate gate = LogicGate.fromBits(pkt.bits);
            if (gate == null) {
                sp.sendSystemMessage(Component.translatable("gui.omnitech.truth_table.no_match"));
                return;
            }

            ItemStack template = OmniTechItems.gateTemplateFor(gate);
            if (template.isEmpty()) return;

            for (InteractionHand hand : InteractionHand.values()) {
                if (sp.getItemInHand(hand).getItem() instanceof TruthTableItem) {
                    sp.setItemInHand(hand, template);
                    sp.sendSystemMessage(Component.translatable("gui.omnitech.truth_table.assembled",
                            template.getHoverName()));
                    return;
                }
            }
        });
    }

    private static ItemStack findTruthTable(ServerPlayer sp) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = sp.getItemInHand(hand);
            if (s.getItem() instanceof TruthTableItem) return s;
        }
        return null;
    }
}
