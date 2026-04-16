package com.dev1lroot.mcmods.omnitech;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;

/**
 * Reads every {@code data/omnitech/creative_tab/<tab_id>.json} from the mod JAR at startup
 * and registers a creative tab into {@link OmniTechGUI#REGISTRY} for each one.
 *
 * <p>Must be called before {@link OmniTechGUI#REGISTRY}'s {@code register(IEventBus)} so that
 * all DeferredRegister entries are queued before the RegisterEvent fires.
 *
 * <p>JSON schema:
 * <pre>{@code
 * {
 *   "icon":  "omnitech:tungsten_ingot",
 *   "data":  ["omnitech:boiler", "$material_sets", ...],
 *   "after": "minecraft:spawn_eggs"
 * }
 * }</pre>
 *
 * <p>The registry name (and therefore the ResourceKey namespace+path) is taken from the
 * filename without extension, e.g. {@code omnitech.materials.json} registers the tab
 * {@code omnitech:omnitech.materials}. The translation key is derived as
 * {@code "itemGroup." + tabId}.
 *
 * <p>Special sentinel values in {@code data}:
 * <ul>
 *   <li>{@code "$material_sets"} – calls {@link MaterialSet#addAllToTab}</li>
 *   <li>{@code "$armor_sets"}   – calls {@link ArmorSet#addAllToTab}</li>
 *   <li>{@code "$tool_sets"}    – calls {@link ToolSet#addAllToTab}</li>
 * </ul>
 */
public class CreativeTabLoader {
    private static final Gson GSON = new Gson();
    static final String TAB_DATA_PATH = "data/omnitech/creative_tab";

    public static void loadAll() {
        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        contents.visitContent(TAB_DATA_PATH, (relativePath, resource) -> {
            if (!relativePath.endsWith(".json")) return;
            String remainder = relativePath.substring(TAB_DATA_PATH.length() + 1);
            if (remainder.contains("/")) return; // skip sub-directories

            String tabId = remainder.substring(0, remainder.length() - 5); // strip .json

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                String icon = json.get("icon").getAsString();
                String after = json.has("after") ? json.get("after").getAsString() : null;

                JsonArray dataArray = json.getAsJsonArray("data");
                String[] data = new String[dataArray.size()];
                for (int i = 0; i < dataArray.size(); i++) {
                    data[i] = dataArray.get(i).getAsString();
                }

                ResourceKey<CreativeModeTab> afterKey = after != null
                        ? ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.parse(after))
                        : null;

                OmniTechGUI.REGISTRY.register(tabId, () -> {
                    CreativeModeTab.Builder builder = CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup." + tabId))
                            .icon(() -> BuiltInRegistries.ITEM
                                    .getOptional(Identifier.parse(icon))
                                    .map(item -> item.getDefaultInstance())
                                    .orElseGet(() -> net.minecraft.world.item.Items.BARRIER.getDefaultInstance()))
                            .displayItems((parameters, output) -> {
                                for (String entry : data) {
                                    switch (entry) {
                                        case "$material_sets" -> MaterialSet.addAllToTab(output);
                                        case "$armor_sets"    -> ArmorSet.addAllToTab(output);
                                        case "$tool_sets"     -> ToolSet.addAllToTab(output);
                                        default -> BuiltInRegistries.ITEM
                                                .getOptional(Identifier.parse(entry))
                                                .ifPresentOrElse(
                                                        output::accept,
                                                        () -> OmniTech.LOGGER.warn(
                                                                "[CreativeTabLoader] Unknown item '{}' in tab '{}'",
                                                                entry, tabId));
                                    }
                                }
                            });

                    if (afterKey != null) builder.withTabsBefore(afterKey);
                    return builder.build();
                });

                OmniTech.LOGGER.debug("[CreativeTabLoader] Queued tab: {}", tabId);

            } catch (Exception e) {
                OmniTech.LOGGER.error("[CreativeTabLoader] Failed to parse tab '{}': {}", tabId, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[CreativeTabLoader] Creative tabs loaded from JSON");
    }
}
