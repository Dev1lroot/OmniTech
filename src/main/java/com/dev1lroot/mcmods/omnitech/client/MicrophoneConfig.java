package com.dev1lroot.mcmods.omnitech.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MicrophoneConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> INPUT_DEVICE = BUILDER
            .comment("Input audio device name. Empty string means the system default.")
            .define("inputDevice", "");

    public static final ModConfigSpec.ConfigValue<String> MODE = BUILDER
            .comment("Microphone capture mode: ENABLED, PUSH_TO_TALK, or DISABLED")
            .define("mode", MicrophoneMode.ENABLED.name());

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static MicrophoneMode getMode() {
        try {
            return MicrophoneMode.valueOf(MODE.get());
        } catch (IllegalArgumentException e) {
            return MicrophoneMode.ENABLED;
        }
    }

    public static String getInputDevice() {
        return INPUT_DEVICE.get();
    }
}
