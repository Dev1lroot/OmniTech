/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One heavy (non-hydrogen) atom placed on the structure canvas. Hydrogen is never placed
 * explicitly — every heavy atom's implicit hydrogen count is derived from its standard valence
 * minus the bond orders touching it (the same "skeletal formula" convention chemists actually
 * draw with).
 *
 * <p>{@code id} is a stable key independent of list position, so deleting an atom mid-list
 * doesn't renumber the ones after it (bonds reference atoms by this id).
 */
public record Atom(int id, String element, float x, float y) {

    public static final Codec<Atom> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Atom::id),
            Codec.STRING.fieldOf("element").forGetter(Atom::element),
            Codec.FLOAT.fieldOf("x").forGetter(Atom::x),
            Codec.FLOAT.fieldOf("y").forGetter(Atom::y)
    ).apply(i, Atom::new));

    public static final StreamCodec<ByteBuf, Atom> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Atom::id,
            ByteBufCodecs.STRING_UTF8, Atom::element,
            ByteBufCodecs.FLOAT, Atom::x,
            ByteBufCodecs.FLOAT, Atom::y,
            Atom::new);

    /** Standard (default) valence used for implicit-hydrogen filling. -1 if unknown/unsupported. */
    public static int standardValence(String element) {
        return switch (element) {
            case "C" -> 4;
            case "N" -> 3;
            case "O" -> 2;
            case "S" -> 2;
            case "P" -> 3;
            case "F", "Cl", "Br", "I" -> 1;
            default -> -1;
        };
    }
}
