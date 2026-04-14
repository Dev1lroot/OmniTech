package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent client → server when the player confirms a space travel destination from
 * the orbit-altitude navigation screen.
 *
 * <p>Carries the target dimension ID and the fuel cost pre-calculated by the
 * client.  The server validates that:
 * <ul>
 *   <li>The player is riding a rocket that is in the {@link RocketEntity.RocketState#ORBIT} state.</li>
 *   <li>The rocket has at least {@code requiredFuel} mB available.</li>
 * </ul>
 *
 * <p>On success the server:
 * <ol>
 *   <li>Deducts {@code requiredFuel} mB from the rocket.</li>
 *   <li>Sets the rocket state to {@link RocketEntity.RocketState#DESCENDING}.</li>
 *   <li>Teleports the rocket (and the riding player) to {@code (x, 300, z)} in the
 *       target dimension — the player then slow-falls to the surface.</li>
 * </ol>
 *
 * <p><b>Note on fuel trust:</b> the fuel cost is calculated client-side from
 * {@code space_map.json} (an asset, not a data file, so the server cannot read it).
 * A future improvement is to move {@code space_map.json} to {@code data/} so the
 * server can validate the cost independently.
 */
public record SpaceTravelPacket(String dimensionId, int requiredFuel) implements CustomPacketPayload {

    public static final Type<SpaceTravelPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "space_travel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceTravelPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeUtf(pkt.dimensionId);
                        buf.writeInt(pkt.requiredFuel);
                    },
                    buf -> new SpaceTravelPacket(buf.readUtf(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpaceTravelPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            // Must be riding a rocket.
            if (!(sp.getVehicle() instanceof RocketEntity rocket)) {
                sp.sendSystemMessage(Component.literal("[OmniTech] Must be riding a rocket to travel."));
                return;
            }

            // Rocket must be in orbit.
            if (rocket.getState() != RocketEntity.RocketState.ORBIT) {
                sp.sendSystemMessage(Component.literal("[OmniTech] Rocket must reach orbit altitude first."));
                return;
            }

            // Resolve target dimension.
            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, Identifier.parse(pkt.dimensionId));
            ServerLevel targetLevel = sp.level().getServer().getLevel(dimKey);

            if (targetLevel == null) {
                sp.sendSystemMessage(Component.literal("[OmniTech] Destination dimension is not yet available."));
                return;
            }

            if (sp.level().dimension().equals(dimKey)) {
                sp.sendSystemMessage(Component.literal("[OmniTech] You are already at this destination."));
                return;
            }

            // Fuel check.
            int currentFuel = rocket.getFuelAmount();
            if (currentFuel < pkt.requiredFuel) {
                sp.sendSystemMessage(Component.literal(
                        "[OmniTech] Insufficient fuel: need " + pkt.requiredFuel
                        + " mB, have " + currentFuel + " mB."));
                return;
            }

            // Consume fuel.
            rocket.setFuelAmount(currentFuel - pkt.requiredFuel);

            // Switch to DESCENDING before teleport so the state is saved into the
            // entity NBT that TeleportTransition copies to the new level.
            rocket.setState(RocketEntity.RocketState.DESCENDING);

            // Teleport to arrival altitude in the target dimension.
            // The rocket (and riding player as passenger) arrives at Y = 300,
            // same XZ, and slow-falls to the surface.
            double x = rocket.getX();
            double z = rocket.getZ();
            rocket.teleport(new TeleportTransition(
                    targetLevel,
                    new Vec3(x, RocketEntity.ARRIVAL_ALTITUDE, z),
                    Vec3.ZERO,
                    rocket.getYRot(),
                    rocket.getXRot(),
                    TeleportTransition.DO_NOTHING
            ));
        });
    }
}
