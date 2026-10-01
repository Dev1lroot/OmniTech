/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Block entities that report their own electrical readings to the
 * {@link com.dev1lroot.mcmods.omnitech.items.MultimeterItem}. Called server side.
 */
public interface IMultimeterReadable {
    void appendMultimeterReadout(Consumer<Component> out);
}
