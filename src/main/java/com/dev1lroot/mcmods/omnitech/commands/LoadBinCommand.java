package com.dev1lroot.mcmods.omnitech.commands;

import com.dev1lroot.mcmods.omnitech.BinStorage;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class LoadBinCommand {

    private LoadBinCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("loadbin")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("file", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                        if (stack.isEmpty()) {
                            ctx.getSource().sendFailure(Component.literal("Nothing in main hand."));
                            return 0;
                        }

                        String filename = StringArgumentType.getString(ctx, "file");
                        Path file = Path.of(filename);

                        byte[] data;
                        try {
                            data = Files.readAllBytes(file);
                        } catch (IOException e) {
                            ctx.getSource().sendFailure(
                                Component.literal("Cannot read file: " + e.getMessage()));
                            return 0;
                        }

                        // Reuse existing UUID if the item already has one, otherwise assign a new one
                        UUID id = stack.get(OmniTechDataComponents.PROGRAM_BINARY.get());
                        if (id == null) id = BinStorage.allocate();

                        BinStorage.write(ctx.getSource().getServer(), id, data);
                        stack.set(OmniTechDataComponents.PROGRAM_BINARY.get(), id);

                        // Clear any existing source/console so the GUI reflects the new binary
                        stack.remove(OmniTechDataComponents.PROGRAM.get());
                        stack.remove(OmniTechDataComponents.CONSOLE_OUTPUT.get());

                        final int len = data.length;
                        final UUID fid = id;
                        ctx.getSource().sendSuccess(
                            () -> Component.literal(
                                "Loaded " + len + " bytes from " + file
                                + " into MCU (id=" + fid + ")."),
                            false);
                        return len;
                    })
                )
        );
    }
}
