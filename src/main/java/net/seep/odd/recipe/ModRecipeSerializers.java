// src/main/java/net/seep/odd/recipe/ModRecipeSerializers.java
package net.seep.odd.recipe;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.conquer.recipe.BigMetalicFrostSpawnRecipe;

public final class ModRecipeSerializers {
    private ModRecipeSerializers() {}

    public static final RecipeSerializer<BigMetalicFrostSpawnRecipe> BIG_METALIC_FROST_SPAWN =
            Registry.register(
                    Registries.RECIPE_SERIALIZER,
                    new Identifier(Oddities.MOD_ID, "big_metalic_frost_spawn"),
                    new SpecialRecipeSerializer<>(BigMetalicFrostSpawnRecipe::new)
            );

    public static void register() {
        // touch class to ensure static init in some setups
    }
}
