package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.gui.SpaceNavigationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server → client when the rocket reaches orbit altitude (Y ≥ 400)
 * and freezes in the ORBIT state.
 *
 * <p>The client handler opens {@link SpaceNavigationScreen} so the player
 * can select a travel destination.
 */
public record RocketOrbitPacket() implements CustomPacketPayload {

    public static final Type<RocketOrbitPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "rocket_orbit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RocketOrbitPacket> CODEC =
            StreamCodec.unit(new RocketOrbitPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler: opens the space navigation screen.
     * Runs on the main client thread via {@code enqueueWork}.
     */
    public static void handle(RocketOrbitPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new SpaceNavigationScreen());
            }
        });
    }
}
