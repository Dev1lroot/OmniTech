package com.dev1lroot.mcmods.omnitech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads assembler recipes from {@code data/omnitech/assembler/*.json}.
 *
 * <p>JSON schema:
 * <pre>{@code
 * {
 *   "blueprint":       "d_flip_flop",
 *   "output":          {"item": "omnitech:d_flip_flop_chip", "count": 1},
 *   "components": [
 *     {"item": "minecraft:iron_ingot", "count": 4}
 *   ],
 *   "processing_time": 200
 * }
 * }</pre>
 *
 * The {@code blueprint} field matches the {@code RESEARCH_NAME} data component
 * of the blueprint item in the assembler's blueprint slot.
 */
public class AssemblerLoader {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DATA_PATH = "data/omnitech/assembler";

    private static final Map<String, AssemblerRecipe> RECIPES = new LinkedHashMap<>();

    public record Ingredient(String itemId, int count) {
        public boolean matches(ItemStack stack) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId));
            return item.isPresent() && stack.is(item.get()) && !stack.isEmpty();
        }
    }

    public record AssemblerRecipe(
            String id,
            String blueprintTag,
            String outputItemId,
            int outputCount,
            List<Ingredient> components,
            int processingTime) {

        public ItemStack createOutput() {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(outputItemId));
            return item.map(i -> new ItemStack(i, outputCount)).orElse(ItemStack.EMPTY);
        }
    }

    public static void loadAll() {
        RECIPES.clear();

        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        int[] count = {0};
        contents.visitContent(DATA_PATH, (path, resource) -> {
            if (!path.endsWith(".json")) return;
            String remainder = path.substring(DATA_PATH.length() + 1);
            if (remainder.contains("/")) return;

            String id = remainder.replace(".json", "");

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                String blueprint = json.get("blueprint").getAsString();

                JsonObject outputObj = json.getAsJsonObject("output");
                String outputItem = outputObj.get("item").getAsString();
                int outputCount = outputObj.has("count") ? outputObj.get("count").getAsInt() : 1;

                int processingTime = json.has("processing_time")
                        ? json.get("processing_time").getAsInt() : 200;

                List<Ingredient> components = new ArrayList<>();
                JsonArray arr = json.getAsJsonArray("components");
                for (var el : arr) {
                    JsonObject ing = el.getAsJsonObject();
                    String item = ing.get("item").getAsString();
                    int cnt = ing.has("count") ? ing.get("count").getAsInt() : 1;
                    components.add(new Ingredient(item, cnt));
                }

                RECIPES.put(id, new AssemblerRecipe(id, blueprint, outputItem, outputCount, components, processingTime));
                count[0]++;
                OmniTech.LOGGER.debug("[AssemblerLoader] Loaded: {}", id);

            } catch (Exception e) {
                OmniTech.LOGGER.error("[AssemblerLoader] Failed to parse '{}': {}", path, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[AssemblerLoader] {} assembler recipes loaded", count[0]);
    }

    public static Optional<AssemblerRecipe> findByBlueprint(String blueprintTag) {
        return RECIPES.values().stream()
                .filter(r -> r.blueprintTag().equals(blueprintTag))
                .findFirst();
    }

    public static Map<String, AssemblerRecipe> all() {
        return RECIPES;
    }
}
