package com.chaosthedude.explorerscompass.recipe;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.google.gson.JsonParser;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

public final class ClassicCompassRecipe {
    private static final Identifier ID = new Identifier(ExplorersCompass.MODID, "explorers_compass");
    private static final String RECIPE_PATH = "recipes/explorers_compass.json";
    private static final String BUNDLED_RECIPE = "/data/" + ExplorersCompass.MODID + "/" + RECIPE_PATH;
    private static final String CLASSIC_RECIPE = "/assets/" + ExplorersCompass.MODID + "/classic_recipe.json";

    private ClassicCompassRecipe() {}

    public static void apply(MinecraftServer server) {
        if (!ExplorersCompassConfig.useClassicCompass) return;

        var manager = server.getRecipeManager();
        var resource = server.getResourceManager().getResource(new Identifier(ExplorersCompass.MODID, RECIPE_PATH));
        if (manager.get(ID).isEmpty() || resource.isEmpty()) return;

        try (var activeReader = resource.get().getReader();
             var bundledReader = openBundled(BUNDLED_RECIPE);
             var classicReader = openBundled(CLASSIC_RECIPE)) {
            // A datapack can supply its own recipe for this ID.
            if (!JsonParser.parseReader(activeReader).equals(JsonParser.parseReader(bundledReader))) return;

            Recipe<?> classic = RecipeManager.deserialize(ID, JsonParser.parseReader(classicReader).getAsJsonObject());
            var recipes = new ArrayList<Recipe<?>>(manager.values());
            recipes.replaceAll(recipe -> ID.equals(recipe.getId()) ? classic : recipe);
            manager.setRecipes(recipes);
        } catch (IOException | RuntimeException e) {
            ExplorersCompass.LOGGER.error("Could not load the classic compass recipe", e);
        }
    }

    private static InputStreamReader openBundled(String path) {
        return new InputStreamReader(Objects.requireNonNull(ClassicCompassRecipe.class.getResourceAsStream(path)),
                StandardCharsets.UTF_8);
    }
}
