package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import java.util.ArrayList;
import java.util.List;

public class ReactorBERRenderState extends BlockEntityRenderState {

    /** One entry per control rod currently inserted in the reactor. */
    public record RodEntry(int dx, int dy, int dz, int control, int light) {}

    public final List<RodEntry> rods = new ArrayList<>();
}
