package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent client → server when the player confirms a space travel destination.
 * Carries only the target dimension's resource location string.
 * The server validates the dimension exists and teleports the rocket (with
 * the riding player as passenger) to the surface at the same XZ.
 */
public record SpaceTravelPacket(String dimensionId) implements CustomPacketPayload {

    public static final Type<SpaceTravelPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "space_travel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceTravelPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.dimensionId),
                    buf -> new SpaceTravelPacket(buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpaceTravelPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            // Player must be riding a rocket
            if (!(sp.getVehicle() instanceof RocketEntity rocket)) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[OmniTech] Must be riding a rocket to travel."));
                return;
            }

            // Resolve target dimension
            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, Identifier.parse(pkt.dimensionId));
            ServerLevel targetLevel = sp.level().getServer().getLevel(dimKey);

            if (targetLevel == null) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[OmniTech] Destination dimension is not yet available."));
                return;
            }

            // Don't teleport if already there
            if (sp.level().dimension().equals(dimKey)) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[OmniTech] You are already at this destination."));
                return;
            }

            // Find safe landing position in target dimension (same XZ, surface Y)
            double x = rocket.getX();
            double z = rocket.getZ();
            int surfaceY = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
            Vec3 landingPos = new Vec3(x, surfaceY + 1.5, z);

            // Teleporting the rocket automatically teleports the riding player as a passenger.
            rocket.teleport(new TeleportTransition(
                    targetLevel,
                    landingPos,
                    Vec3.ZERO,
                    rocket.getYRot(),
                    rocket.getXRot(),
                    TeleportTransition.DO_NOTHING
            ));
        });
    }
}
