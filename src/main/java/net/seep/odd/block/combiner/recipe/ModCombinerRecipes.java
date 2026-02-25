// src/main/java/net/seep/odd/block/combiner/recipe/ModCombinerRecipes.java
package net.seep.odd.block.combiner.recipe;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModCombinerRecipes {
    private ModCombinerRecipes() {}

    public static final Identifier ID = new Identifier(Oddities.MOD_ID, "combiner_forging");
    public static RecipeType<CombinerRecipe> TYPE;
    public static RecipeSerializer<CombinerRecipe> SERIALIZER;

    public static void register() {
        TYPE = Registry.register(Registries.RECIPE_TYPE, ID, new RecipeType<>() {
            public String toString() { return ID.toString(); }
        });
        SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, ID, new CombinerRecipe.Serializer());
        Oddities.LOGGER.info("Registered recipe type {}", ID);
    }
}