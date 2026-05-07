package com.dev1lroot.mcmods.omnitech.commands;

import com.dev1lroot.mcmods.omnitech.BinStorage;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class HxdCommand {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private HxdCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("hxd")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (stack.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("Nothing in main hand."));
                        return 0;
                    }

                    MinecraftServer server = ctx.getSource().getServer();
                    byte[] data = extractData(server, stack);
                    if (data == null || data.length == 0) {
                        ctx.getSource().sendFailure(
                            Component.literal("Item has no binary data (PROGRAM_BINARY / FLOPPY_DATA / RAM_DATA)."));
                        return 0;
                    }

                    String filename = LocalDateTime.now().format(FMT) + ".bin";
                    Path dir  = Path.of("hxd");
                    Path file = dir.resolve(filename);
                    try {
                        Files.createDirectories(dir);
                        Files.write(file, data);
                    } catch (IOException e) {
                        ctx.getSource().sendFailure(
                            Component.literal("Could not write file: " + e.getMessage()));
                        return 0;
                    }

                    final int len = data.length;
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("Dumped " + len + " bytes → hxd/" + filename),
                        false);
                    return len;
                })
        );
    }

    private static byte[] extractData(MinecraftServer server, ItemStack stack) {
        UUID binId = stack.get(OmniTechDataComponents.PROGRAM_BINARY.get());
        if (binId != null) {
            byte[] d = BinStorage.read(server, binId);
            if (d != null && d.length > 0) return d;
        }
        UUID floppyId = stack.get(OmniTechDataComponents.FLOPPY_DATA.get());
        if (floppyId != null) {
            byte[] d = BinStorage.read(server, floppyId);
            if (d != null && d.length > 0) return d;
        }
        UUID ramId = stack.get(OmniTechDataComponents.RAM_DATA.get());
        if (ramId != null) {
            byte[] d = BinStorage.read(server, ramId);
            if (d != null && d.length > 0) return d;
        }
        return null;
    }
}
