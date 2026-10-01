/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.util.ConductorMetals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the overhead spans of a {@link SuspensionInsulatorBlock}. Every span is
 * stored at both ends; the end being removed drops the coils and erases the
 * record at the other end. Synced to the client for the line renderer.
 */
public class SuspensionInsulatorBlockEntity extends BlockEntity {

    /** Longest span, in blocks between clamp points. */
    public static final int MAX_SPAN = 32;
    /** Spans per insulator. */
    public static final int MAX_LINKS = 4;
    /** Blocks of line one coil covers. */
    public static final int BLOCKS_PER_COIL = 16;

    /** One overhead span to {@code other}, strung from {@code metal} coils. */
    public record Link(BlockPos other, String metal, float length) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("other").forGetter(Link::other),
                Codec.STRING.fieldOf("metal").forGetter(Link::metal),
                Codec.FLOAT.fieldOf("length").forGetter(Link::length)
        ).apply(i, Link::new));

        public double resistance() {
            return ConductorMetals.spanResistance(metal, length);
        }

        public int coils() {
            return coilsFor(length);
        }
    }

    public static int coilsFor(double length) {
        return Math.max(1, (int) Math.ceil(length / BLOCKS_PER_COIL));
    }

    private final List<Link> links = new ArrayList<>();

    public SuspensionInsulatorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SUSPENSION_INSULATOR.get(), pos, state);
    }

    public List<Link> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public Vec3 attachPoint() {
        return SuspensionInsulatorBlock.attachPoint(worldPosition, getBlockState());
    }

    public boolean isLinkedTo(BlockPos other) {
        for (Link l : links) if (l.other.equals(other)) return true;
        return false;
    }

    public boolean hasFreeSlot() {
        return links.size() < MAX_LINKS;
    }

    void addLink(Link link) {
        links.add(link);
        changed();
    }

    private boolean removeLinkTo(BlockPos other) {
        boolean removed = links.removeIf(l -> l.other.equals(other));
        if (removed) changed();
        return removed;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    /** Takes down every span ending here and gives the coils to {@code player}. */
    public void cutAllSpans(ServerPlayer player) {
        if (links.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.omnitech.insulator.no_spans"));
            return;
        }
        int count = links.size();
        for (Link link : List.copyOf(links)) {
            detachFar(link);
            ItemStack coils = new ItemStack(ConductorMetals.coilItem(link.metal), link.coils());
            if (!player.getAbilities().instabuild) player.getInventory().placeItemBackInInventory(coils, Prediction.SERVER_ONLY);
        }
        links.clear();
        changed();
        player.sendOverlayMessage(Component.translatable("message.omnitech.insulator.cut", count));
    }

    /** Erases the record of {@code link} at its far end. */
    private void detachFar(Link link) {
        if (level == null || !level.isLoaded(link.other)) return;
        if (level.getBlockEntity(link.other) instanceof SuspensionInsulatorBlockEntity far) {
            far.removeLinkTo(worldPosition);
        }
    }

    /** Broken or replaced: drop the coils of every span and detach the far ends. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        for (Link link : links) {
            detachFar(link);
            Block.popResource(level, pos, new ItemStack(ConductorMetals.coilItem(link.metal), link.coils()));
        }
        links.clear();
    }

    /** {@code true} if the insulator at {@code other} still holds the matching end of this span. */
    public static boolean isReciprocal(Level level, BlockPos self, BlockPos other) {
        return level.getBlockEntity(other) instanceof SuspensionInsulatorBlockEntity far
                && far.isLinkedTo(self);
    }

    // ── Sync / persistence ───────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        links.clear();
        input.read("Links", Link.CODEC.listOf()).ifPresent(links::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Links", Link.CODEC.listOf(), links);
    }
}
