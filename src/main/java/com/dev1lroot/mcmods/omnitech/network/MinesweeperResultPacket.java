package com.dev1lroot.mcmods.omnitech.network;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.logic.research_table.ResearchTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → Server: reports the result of a minesweeper game (win or lose). */
public record MinesweeperResultPacket(BlockPos pos, boolean won) implements CustomPacketPayload {

    public static final Type<MinesweeperResultPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(OmniTech.MODID, "minesweeper_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MinesweeperResultPacket> CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeBlockPos(pkt.pos); buf.writeBoolean(pkt.won); },
                    buf -> new MinesweeperResultPacket(buf.readBlockPos(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MinesweeperResultPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.level().getBlockEntity(pkt.pos) instanceof ResearchTableBlockEntity rt) {
                if (pkt.won()) rt.onMinesweeperWin();
                else           rt.onMinesweeperLose();
            }
        });
    }
}
