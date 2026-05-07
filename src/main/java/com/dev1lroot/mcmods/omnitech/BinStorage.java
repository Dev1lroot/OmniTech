package com.dev1lroot.mcmods.omnitech;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores large binary blobs (kernel images, floppy disks, RAM cards) as
 * {@code <world>/omnitech/<UUID>.bin} files on disk.
 *
 * Items carry only the 16-byte UUID in their NBT data component;
 * actual bytes never travel through NBT or the network.
 */
public final class BinStorage {

    private static final Logger LOGGER = LogManager.getLogger();

    private BinStorage() {}

    /** Returns a fresh UUID to assign to a new device. */
    public static UUID allocate() {
        return UUID.randomUUID();
    }

    /** Resolves the {@code omnitech/} directory inside the world save folder. */
    public static Path dir(MinecraftServer server) {
        Path p = server.getWorldPath(LevelResource.ROOT).resolve("omnitech");
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            LOGGER.error("BinStorage: cannot create data directory", e);
        }
        return p;
    }

    /** Reads the file for {@code id}, or returns {@code null} if not found / unreadable. */
    public static byte[] read(MinecraftServer server, UUID id) {
        try {
            return Files.readAllBytes(dir(server).resolve(id + ".bin"));
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes {@code data} to the file for {@code id}, creating or replacing it. */
    public static void write(MinecraftServer server, UUID id, byte[] data) {
        try {
            Files.write(dir(server).resolve(id + ".bin"), data);
        } catch (IOException e) {
            LOGGER.error("BinStorage: failed to write {}.bin", id, e);
        }
    }

    /** Deletes the file for {@code id} if it exists. */
    public static void delete(MinecraftServer server, UUID id) {
        try {
            Files.deleteIfExists(dir(server).resolve(id + ".bin"));
        } catch (IOException ignored) {}
    }
}
