/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.OmniTechEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Thrown by {@link com.dev1lroot.mcmods.omnitech.items.TomatoItem} — a plain
 * right-click (as opposed to shift-right-click, which eats). Mirrors vanilla
 * {@link net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball}
 * exactly: harmless splat on hit (0 damage, unlike snowball's blaze case),
 * item-particle burst, then discards itself.
 */
public class ThrownTomatoEntity extends ThrowableItemProjectile {

    public ThrownTomatoEntity(EntityType<? extends ThrownTomatoEntity> type, Level level) {
        super(type, level);
    }

    public ThrownTomatoEntity(Level level, LivingEntity owner, ItemStack itemStack) {
        super(OmniTechEntities.THROWN_TOMATO.get(), owner, level, itemStack);
    }

    public ThrownTomatoEntity(Level level, double x, double y, double z, ItemStack itemStack) {
        super(OmniTechEntities.THROWN_TOMATO.get(), x, y, z, level, itemStack);
    }

    @Override
    protected Item getDefaultItem() {
        return OmniTechItems.TOMATO.get();
    }

    @Override
    public void handleEntityEvent(@EntityEvent.Value byte id) {
        if (id == 3) {
            ItemStack item = this.getItem();
            ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(item));
            for (int i = 0; i < 8; i++) {
                this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity entity = hitResult.getEntity();
        entity.hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }
}
