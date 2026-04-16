package com.dev1lroot.mcmods.omnitech.gui.layout;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Loader for GUI layout JSON files stored in
 * {@code assets/omnitech/gui/<name>.json}.
 *
 * <p>{@link #load} reads directly from the mod JAR via the classpath — no
 * Minecraft {@code ResourceManager} required — so it works on both the
 * dedicated server and the client.  Layouts are cached after first load.
 */
public class GuiLayoutLoader {

    private static final Gson                  GSON  = new Gson();
    private static final Map<String, GuiLayout> CACHE = new HashMap<>();

    private GuiLayoutLoader() {}

    /**
     * Returns the layout for {@code assets/omnitech/gui/<name>.json},
     * loading and caching it on first call.
     * Safe to call from static initialisers, menu constructors, and
     * screen constructors on any thread.
     */
    public static GuiLayout load(String name) {
        return CACHE.computeIfAbsent(name, GuiLayoutLoader::loadFromJar);
    }

    /** Invalidates all cached layouts (call on resource-pack reload if needed). */
    public static void clearCache() {
        CACHE.clear();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static GuiLayout loadFromJar(String name) {
        String path = "/assets/" + OmniTech.MODID + "/gui/" + name + ".json";
        try (InputStream is = GuiLayoutLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                LoggerFactory.getLogger(GuiLayoutLoader.class)
                        .error("[GuiLayout] Not found on classpath: {}", path);
                return new GuiLayout();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                GuiLayout layout = GSON.fromJson(reader, GuiLayout.class);
                if (layout.elements == null) layout.elements = new ArrayList<>();
                if (layout.inventory == null) layout.inventory = new GuiLayout.InventoryLayout();
                LoggerFactory.getLogger(GuiLayoutLoader.class)
                        .info("[GuiLayout] Loaded {} ({}×{})", name, layout.width, layout.height);
                return layout;
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(GuiLayoutLoader.class)
                    .error("[GuiLayout] Failed to load: {}", path, e);
            return new GuiLayout();
        }
    }
}
