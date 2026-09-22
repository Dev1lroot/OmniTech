/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import com.dev1lroot.mcmods.omnitech.chemistry.SmilesGenerator;
import com.dev1lroot.mcmods.omnitech.items.ChemicalCompoundDustItem;
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
 * Client → server: "Print as Compound" was pressed on the Structure Table editor. Consumes 1
 * paper (waived in creative), same as {@link PrintFormulaPacket}, but hands back a
 * {@link ChemicalCompoundDustItem} stamped with the sketch's SMILES code
 * ({@link SmilesGenerator#generate}) instead of a paper printout — a generic compound the mod
 * never hand-registered, named/rendered entirely from that SMILES.
 */
public record PrintCompoundPacket(Molecule molecule) implements CustomPacketPayload {

    public static final Type<PrintCompoundPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "print_compound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrintCompoundPacket> CODEC = StreamCodec.composite(
            Molecule.STREAM_CODEC, PrintCompoundPacket::molecule,
            PrintCompoundPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PrintCompoundPacket pkt, IPayloadContext ctx) {
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

            String smiles = SmilesGenerator.generate(pkt.molecule());
            ItemStack result = new ItemStack(OmniTechItems.CHEMICAL_COMPOUND_DUST.get());
            result.set(OmniTechDataComponents.SMILES.get(), smiles);

            if (!sp.getInventory().add(result)) {
                sp.drop(result, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        });
    }
}
