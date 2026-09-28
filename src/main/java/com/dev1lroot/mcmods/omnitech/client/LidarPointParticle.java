/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * One helmet-lidar return: a tiny glowing dot fixed where a scan ray hit, fading out
 * over its lifetime. Drawn with an additive, fog-free pipeline so it keeps glowing
 * while lidar mode fogs the world to black; depth-tested against the terrain but
 * not writing depth, so neighbouring dots blend instead of cutting each other.
 */
public class LidarPointParticle extends SingleQuadParticle {

    static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withLocation(Identifier.fromNamespaceAndPath(OmniTech.MODID, "pipeline/lidar_point"))
            .withVertexShader(Identifier.fromNamespaceAndPath(OmniTech.MODID, "core/lidar_point"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(OmniTech.MODID, "core/lidar_point"))
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .build();

    static final SingleQuadParticle.Layer LAYER =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, PIPELINE);

    LidarPointParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite,
                       float r, float g, float b, float size, int lifetime) {
        super(level, x, y, z, sprite);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = this.yd = this.zd = 0.0;
        this.quadSize = size;
        this.lifetime = lifetime;
        this.setColor(r, g, b);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float life = (float) this.age / this.lifetime;
        this.setAlpha(1.0F - life * life);   // glow, then extinguish
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return LAYER;
    }

    @Override
    protected int getLightCoords(float a) {
        return 15728880;   // full bright
    }
}
