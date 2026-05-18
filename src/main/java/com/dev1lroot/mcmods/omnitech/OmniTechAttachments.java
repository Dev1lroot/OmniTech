/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class OmniTechAttachments {

    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OmniTech.MODID);

    /** Tracks whether a player has already received the starter guidebook on first join. */
    public static final Supplier<AttachmentType<Boolean>> GUIDEBOOK_GIVEN = REGISTRY.register(
            "guidebook_given", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("v"), b -> b)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
