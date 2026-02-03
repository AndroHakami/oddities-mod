package net.seep.odd.block.supercooker.recipe;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.seep.odd.recipe.ModRecipes;

import java.util.ArrayList;
import java.util.List;

public class SuperCookerRecipe implements Recipe<Inventory> {
    private final Identifier id;
    private final DefaultedList<Ingredient> ingredients;
    private final ItemStack output;
    private final int cookTime;
    private final int minStirs;

    public SuperCookerRecipe(Identifier id, DefaultedList<Ingredient> ingredients, ItemStack output, int cookTime, int minStirs) {
        this.id = id;
        this.ingredients = ingredients;
        this.output = output;
        this.cookTime = cookTime;
        this.minStirs = minStirs;
    }

    public int getCookTime() { return cookTime; }
    public int getMinStirs() { return minStirs; }
    public ItemStack getOutputCopy() { return output.copy(); }

    @Override
    public boolean matches(Inventory inv, net.minecraft.world.World world) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) stacks.add(s.copy());
        }

        // Exact shapeless match (no extras)
        for (Ingredient ing : ingredients) {
            int found = -1;
            for (int i = 0; i < stacks.size(); i++) {
                if (ing.test(stacks.get(i))) { found = i; break; }
            }
            if (found == -1) return false;

            ItemStack hit = stacks.get(found);
            hit.decrement(1);
            if (hit.isEmpty()) stacks.remove(found);
        }

        return stacks.isEmpty();
    }

    @Override public ItemStack craft(Inventory inv, DynamicRegistryManager drm) { return output.copy(); }
    @Override public boolean fits(int width, int height) { return true; }

    @Override public ItemStack getOutput(DynamicRegistryManager drm) { return output; }
    @Override public Identifier getId() { return id; }

    @Override public RecipeSerializer<?> getSerializer() { return ModRecipes.SUPER_COOKER_SERIALIZER; }
    @Override public RecipeType<?> getType() { return ModRecipes.SUPER_COOKER_TYPE; }

    @Override public DefaultedList<Ingredient> getIngredients() { return ingredients; }
}
