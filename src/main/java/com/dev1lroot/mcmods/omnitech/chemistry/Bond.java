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

/** A bond between two {@link Atom}s (by id), with order 1 (single), 2 (double) or 3 (triple). */
public record Bond(int a, int b, int order) {

    public static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("a").forGetter(Bond::a),
            Codec.INT.fieldOf("b").forGetter(Bond::b),
            Codec.INT.fieldOf("order").forGetter(Bond::order)
    ).apply(i, Bond::new));

    public static final StreamCodec<ByteBuf, Bond> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Bond::a,
            ByteBufCodecs.VAR_INT, Bond::b,
            ByteBufCodecs.VAR_INT, Bond::order,
            Bond::new);

    /** Returns the id of the atom on the other end of this bond from {@code atomId}, or -1. */
    public int other(int atomId) {
        if (a == atomId) return b;
        if (b == atomId) return a;
        return -1;
    }

    public boolean touches(int atomId) { return a == atomId || b == atomId; }
}
