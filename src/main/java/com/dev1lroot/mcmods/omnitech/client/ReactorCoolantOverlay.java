/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorStructure;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Full-screen tinted overlay rendered when the player's eyes are inside the
 * reactor's coolant volume.  Tint colour matches the coolant fluid.
 */
public class ReactorCoolantOverlay implements GuiLayer {

    /** ARGB tint of the current coolant, or 0 if the player is not submerged. */
    private static volatile int cachedColor = 0;
    private static int checkCounter = 0;

    /** Called from {@link com.dev1lroot.mcmods.omnitech.OmniTechClient#onClientTick} every tick. */
    public static void tick(Minecraft mc) {
        if (++checkCounter < 10) return;
        checkCounter = 0;
        cachedColor = detect(mc);
    }

    private static int detect(Minecraft mc) {
        if (mc.player == null || mc.level == null) return 0;

        Vec3  eye = mc.player.getEyePosition();
        BlockPos bp = mc.player.blockPosition();

        for (int dx = -(ReactorStructure.MAX_SIZE - 1); dx <= 0; dx++) {
            for (int dy = -(ReactorStructure.HEIGHT - 1); dy <= 0; dy++) {
                for (int dz = -(ReactorStructure.MAX_SIZE - 1); dz <= 0; dz++) {
                    var be = mc.level.getBlockEntity(bp.offset(dx, dy, dz));
                    if (!(be instanceof ReactorBlockEntity rbe)) continue;
                    if (!rbe.isFormed() || rbe.getCoolantTank().isEmpty()) continue;

                    ReactorStructure s = rbe.getStructure();
                    if (s == null) continue;

                    BlockPos o    = s.origin;
                    int      cap  = rbe.getTankCapacity();
                    double   yTop = o.getY() + 1.0
                            + (cap > 0 ? (double) rbe.getCoolantAmount() / cap : 0.0)
                            * (ReactorStructure.HEIGHT - 2);

                    if (eye.x > o.getX() + 1 && eye.x < o.getX() + s.width  - 1 &&
                        eye.z > o.getZ() + 1 && eye.z < o.getZ() + s.depth - 1 &&
                        eye.y > o.getY() + 1 && eye.y < yTop) {
                        return tintFor(rbe.getCoolantTank());
                    }
                }
            }
        }
        return 0;
    }

    private static int tintFor(FluidStack fluid) {
        try {
            FluidStateModelSet models =
                    Minecraft.getInstance().getModelManager().getFluidStateModelSet();
            FluidModel fm = models.get(fluid.getFluid().defaultFluidState());
            if (fm.fluidTintSource() != null) {
                int t = fm.fluidTintSource().colorAsStack(fluid);
                return ARGB.color(255, ARGB.red(t), ARGB.green(t), ARGB.blue(t));
            }
        } catch (Exception ignored) {}
        return 0x4499FF;
    }

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (cachedColor == 0) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int overlay = ARGB.color(100, ARGB.red(cachedColor),
                                       ARGB.green(cachedColor),
                                       ARGB.blue(cachedColor));
        g.fill(0, 0, w, h, overlay);
    }
}
