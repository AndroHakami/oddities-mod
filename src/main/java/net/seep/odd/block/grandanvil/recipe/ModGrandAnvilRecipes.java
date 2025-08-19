package net.seep.odd.block.grandanvil.recipe;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ModGrandAnvilRecipes {
    private ModGrandAnvilRecipes() {}

    public static final Identifier ID = new Identifier(Oddities.MOD_ID, "grand_anvil_forging");
    public static RecipeType<GrandAnvilRecipe> TYPE;
    public static RecipeSerializer<GrandAnvilRecipe> SERIALIZER;

    public static void register() {
        TYPE = Registry.register(Registries.RECIPE_TYPE, ID, new RecipeType<>() {
            public String toString() { return ID.toString(); }
        });
        SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, ID, new GrandAnvilRecipe.Serializer());
        Oddities.LOGGER.info("Registered recipe type {}", ID);
    }
}
