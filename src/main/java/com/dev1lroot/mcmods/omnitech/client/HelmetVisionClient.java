/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.items.HelmetVision;
import com.dev1lroot.mcmods.omnitech.items.SpaceSuitItem;
import com.dev1lroot.mcmods.omnitech.network.HelmetVisionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * Client half of the helmet view modes: the keybind cycles the mode while the
 * space-suit helmet is worn, and {@code GameRendererHelmetVisionMixin} appends the
 * mode's post effect to the frame. Taking the helmet off drops back to DEFAULT.
 *
 * <ul>
 *   <li>NIGHT_VISION – infrared camera: B/W near-IR image with an IR illuminator, VHS tape noise
 *       (+ real Night Vision from the server)</li>
 *   <li>SONAR – every surface coloured by depth-buffer distance</li>
 *   <li>LIDAR – world fogged to black, {@link LidarScanner} paints glowing world-anchored points</li>
 *   <li>X_RAY – {@link XrayVision} ray-marches block density through walls into a B/W film</li>
 *   <li>THERMAL – {@link ThermalVision}: first-hit temperature from light level, living entities red hot</li>
 *   <li>GAMMA – {@link GammaVision}: radioactive sources glow through walls, attenuated by shielding; Geiger clicks</li>
 * </ul>
 */
public final class HelmetVisionClient {

    private static final Identifier NIGHT_VISION = Identifier.fromNamespaceAndPath(OmniTech.MODID, "helmet_night_vision");
    private static final Identifier SONAR = Identifier.fromNamespaceAndPath(OmniTech.MODID, "helmet_sonar");
    private static final Identifier LIDAR = Identifier.fromNamespaceAndPath(OmniTech.MODID, "helmet_lidar");

    private static HelmetVision.Mode mode = HelmetVision.Mode.DEFAULT;

    private HelmetVisionClient() {}

    /** Called every client tick (outside screens). */
    public static void tick(Minecraft mc, KeyMapping cycleKey) {
        if (mc.player == null) return;
        boolean helmet = SpaceSuitItem.isWearingHelmet(mc.player);
        while (cycleKey != null && cycleKey.consumeClick()) {
            if (!helmet) {
                mc.gui.hud.setOverlayMessage(Component.translatable("message.omnitech.helmet_vision.no_helmet")
                        .withStyle(ChatFormatting.GRAY), false);
                continue;
            }
            setMode(mc, mode.next());
            mc.gui.hud.setOverlayMessage(Component.translatable("message.omnitech.helmet_vision",
                    Component.translatable("message.omnitech.helmet_vision." + mode.name().toLowerCase()))
                    .withStyle(ChatFormatting.AQUA), false);
        }
        if (!helmet && mode != HelmetVision.Mode.DEFAULT) setMode(mc, HelmetVision.Mode.DEFAULT);
        if (mode == HelmetVision.Mode.LIDAR) LidarScanner.tick(mc);
    }

    /** Lidar: everything but the return points disappears into black fog. */
    public static void onRenderFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        if (mode != HelmetVision.Mode.LIDAR) return;
        var fog = event.getFogData();
        fog.environmentalStart = 0.0F;
        fog.environmentalEnd = 0.5F;
        fog.renderDistanceStart = 0.0F;
        fog.renderDistanceEnd = 0.5F;
        fog.skyEnd = 0.0F;
        fog.cloudEnd = 0.0F;
    }

    public static void onComputeFogColor(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        if (mode != HelmetVision.Mode.LIDAR) return;
        event.setRed(0.0F);
        event.setGreen(0.0F);
        event.setBlue(0.0F);
    }

    /** Forget the mode on disconnect / world change. */
    public static void reset() {
        mode = HelmetVision.Mode.DEFAULT;
        XrayVision.reset();
        ThermalVision.reset();
        GammaVision.reset();
    }

    /** X-ray, heat and gamma cover the view with their CPU-rendered image, under the rest of the HUD. */
    public static void onRenderGuiPre(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (mode == HelmetVision.Mode.X_RAY) XrayVision.render(event.getGuiGraphics());
        else if (mode == HelmetVision.Mode.THERMAL) ThermalVision.render(event.getGuiGraphics());
        else if (mode == HelmetVision.Mode.GAMMA) GammaVision.render(event.getGuiGraphics());
    }

    private static void setMode(Minecraft mc, HelmetVision.Mode next) {
        mode = next;
        if (mc.getConnection() != null) ClientPacketDistributor.sendToServer(new HelmetVisionPacket(next.ordinal()));
    }

    /** Appends the active mode's post effect to this frame's request list. */
    public static void addPostEffect(List<Identifier> requested) {
        switch (mode) {
            case NIGHT_VISION -> requested.add(NIGHT_VISION);
            case SONAR -> requested.add(SONAR);
            case LIDAR -> requested.add(LIDAR);
            default -> { }
        }
    }
}
