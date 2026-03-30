# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OmniTech is a NeoForge Minecraft mod targeting Minecraft 26.1, focused on technological progression from stone-age tools to advanced EUV microchips.

**Key Identifiers:**
- Mod ID: `omnitech`
- Package: `com.dev1lroot.mcmods.omnitech`
- Java Version: 25 (required)
- NeoForge Version: 26.1.0.17-beta

## Build Commands

```bash
# Build the mod JAR
./gradlew build

# Run client for testing
./gradlew runClient

# Run dedicated server
./gradlew runServer

# Run data generation (models, lang files, etc.)
./gradlew runData

# Run game tests
./gradlew runGameTestServer

# Refresh dependencies
./gradlew --refresh-dependencies

# Clean build artifacts
./gradlew clean
```

## Architecture

### Entry Points

- **OmniTech.java**: Main mod class, handles common setup and registration (blocks, items, creative tabs)
- **OmniTechClient.java**: Client-only class (`@Mod(dist = Dist.CLIENT)`), handles client-specific setup like config screens
- **Config.java**: Common configuration using NeoForge's `ModConfigSpec`

### Registration Pattern

Uses NeoForge's Deferred Registration system:
```java
public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
```

Registers are attached to the mod event bus in the constructor.

### Event System

- Constructor receives `IEventBus` (mod bus) and `ModContainer`
- `commonSetup(FMLCommonSetupEvent)`: Runs during FML common setup phase
- `addCreative(BuildCreativeModeTabContentsEvent)`: Adds items to creative tabs
- `onServerStarting(ServerStartingEvent)`: Handles server startup (registered on `NeoForge.EVENT_BUS`)

### Mixins

Configured in `omnitech.mixins.json` with package `com.dev1lroot.mcmods.omnitech.mixin`. Currently empty but ready for use.

## Key Paths

- Source: `src/main/java/com/dev1lroot/mcmods/omnitech/`
- Resources: `src/main/resources/`
- Assets: `src/main/resources/assets/omnitech/`
- Generated resources: `src/generated/resources/` (datagen output)
- Mod metadata template: `src/main/templates/META-INF/neoforge.mods.toml`

## Configuration

Mod version, Minecraft version, and NeoForge version are defined in `gradle.properties`. The `neoforge.mods.toml` is generated from templates during build.

## Resources

- NeoForge Docs: https://docs.neoforged.net/
- NeoForge Discord: https://discord.neoforged.net/
