package net.seep.odd.recipe;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.supercooker.recipe.SuperCookerRecipe;
import net.seep.odd.block.supercooker.recipe.SuperCookerRecipeSerializer;

public final class ModRecipes {
    private ModRecipes() {}

    public static final RecipeType<SuperCookerRecipe> SUPER_COOKER_TYPE =
            Registry.register(Registries.RECIPE_TYPE,
                    new Identifier(Oddities.MOD_ID, "super_cooker"),
                    new RecipeType<>() {});

    public static final RecipeSerializer<SuperCookerRecipe> SUPER_COOKER_SERIALIZER =
            Registry.register(Registries.RECIPE_SERIALIZER,
                    new Identifier(Oddities.MOD_ID, "super_cooker"),
                    new SuperCookerRecipeSerializer());

    public static void init() {}
}
