/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;

/**
 * Replacement for vanilla's SoundOptionsScreen that adds a "Microphone & Record" footer button.
 * Injected via ScreenEvent.Opening in OmniTechClient.
 */
public class MicrophoneSoundOptionsScreen extends OptionsSubScreen {

    public MicrophoneSoundOptionsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("options.sounds.title"));
    }

    @Override
    protected void addOptions() {
        this.list.addBig(this.options.getSoundSourceOptionInstance(SoundSource.MASTER));
        this.list.addSmall(getAllSoundOptionsExceptMaster());
        this.list.addBig(this.options.soundDevice());
        this.list.addSmall(this.options.showSubtitles(), this.options.directionalAudio());
        this.list.addSmall(this.options.musicFrequency(), this.options.musicToast());
    }

    private OptionInstance<?>[] getAllSoundOptionsExceptMaster() {
        return Arrays.stream(SoundSource.values())
                .filter(s -> s != SoundSource.MASTER)
                .map(this.options::getSoundSourceOptionInstance)
                .toArray(OptionInstance[]::new);
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(
                Component.translatable("options.omnitech.microphone"),
                btn -> this.minecraft.setScreen(new MicrophoneOptionsScreen(this, this.options))
        ).width(150).build());
        footer.addChild(Button.builder(
                CommonComponents.GUI_DONE,
                btn -> this.onClose()
        ).width(150).build());
    }
}
