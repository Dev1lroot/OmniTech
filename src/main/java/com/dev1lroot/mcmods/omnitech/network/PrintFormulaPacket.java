/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.items.ChemicalFormulaItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: "Print" was pressed on the Structure Table editor. Consumes 1 paper from the
 * player's inventory (waived in creative) and hands back a finished {@link ChemicalFormulaItem}
 * carrying the sketched molecule and chosen name. The whole editing session is client-local —
 * this single packet is the only network traffic the editor generates.
 */
public record PrintFormulaPacket(Molecule molecule, String name) implements CustomPacketPayload {

    public static final Type<PrintFormulaPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "print_formula"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrintFormulaPacket> CODEC = StreamCodec.composite(
            Molecule.STREAM_CODEC, PrintFormulaPacket::molecule,
            net.minecraft.network.codec.ByteBufCodecs.stringUtf8(256), PrintFormulaPacket::name,
            PrintFormulaPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PrintFormulaPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (pkt.molecule() == null || pkt.molecule().isEmpty()) return;

            if (!sp.isCreative()) {
                int slot = sp.getInventory().findSlotMatchingItem(new ItemStack(Items.PAPER));
                if (slot < 0) {
                    sp.sendSystemMessage(Component.literal("Needs 1 paper.").withStyle(ChatFormatting.RED));
                    return;
                }
                sp.getInventory().getItem(slot).shrink(1);
            }

            ItemStack result = new ItemStack(OmniTechItems.CHEMICAL_FORMULA.get());
            ChemicalFormulaItem.setMolecule(result, pkt.molecule());
            ChemicalFormulaItem.setCustomName(result, pkt.name());

            if (!sp.getInventory().add(result)) {
                sp.drop(result, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        });
    }
}
