package com.dev1lroot.mcmods.omnitech.client;

import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class MicrophoneOptionsScreen extends OptionsSubScreen {

    private static final Component TITLE = Component.translatable("options.omnitech.microphone.title");

    private final OptionInstance<String> deviceOption;
    private final OptionInstance<MicrophoneMode> modeOption;

    public MicrophoneOptionsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, TITLE);

        this.deviceOption = new OptionInstance<>(
                "options.omnitech.microphone.device",
                OptionInstance.noTooltip(),
                (caption, device) -> device.isEmpty()
                        ? Component.translatable("options.omnitech.microphone.device.default")
                        : Component.literal(device),
                new OptionInstance.LazyEnum<>(
                        () -> Stream.concat(Stream.of(""), MicrophoneCapture.getAvailableInputDevices().stream()).toList(),
                        Optional::of,
                        Codec.STRING
                ),
                MicrophoneConfig.getInputDevice(),
                device -> {
                    MicrophoneConfig.INPUT_DEVICE.set(device);
                    MicrophoneCapture.setDevice(device);
                }
        );

        Codec<MicrophoneMode> modeCodec = Codec.STRING.comapFlatMap(
                s -> {
                    try { return com.mojang.serialization.DataResult.success(MicrophoneMode.valueOf(s)); }
                    catch (Exception e) { return com.mojang.serialization.DataResult.error(() -> "Unknown mode: " + s); }
                },
                MicrophoneMode::name
        );

        this.modeOption = new OptionInstance<>(
                "options.omnitech.microphone.mode",
                OptionInstance.noTooltip(),
                (caption, mode) -> Component.translatable(
                        "options.omnitech.microphone.mode." + mode.name().toLowerCase()),
                new OptionInstance.Enum<>(Arrays.asList(MicrophoneMode.values()), modeCodec),
                MicrophoneConfig.getMode(),
                mode -> MicrophoneConfig.MODE.set(mode.name())
        );
    }

    @Override
    protected void addOptions() {
        this.list.addBig(this.deviceOption);
        this.list.addBig(this.modeOption);
    }

    @Override
    public void removed() {
        MicrophoneConfig.SPEC.save();
        super.removed();
    }
}
