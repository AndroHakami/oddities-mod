// src/main/java/net/seep/odd/abilities/conquer/recipe/BigMetalicFrostSpawnRecipe.java
package net.seep.odd.abilities.conquer.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.abilities.conquer.item.BigMetalicFrostSpawnItem;
import net.seep.odd.abilities.conquer.item.MetalicFrostSpawnItem;
import net.seep.odd.item.ModItems;
import net.seep.odd.recipe.ModRecipeSerializers;


public final class BigMetalicFrostSpawnRecipe extends SpecialCraftingRecipe {

    public BigMetalicFrostSpawnRecipe(Identifier id, CraftingRecipeCategory cat) {
        super(id, cat);
    }

    @Override
    public boolean matches(RecipeInputInventory inv, World world) {
        int count = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;

            if (!s.isOf(ModItems.METALIC_FROST_SPAWN)) return false;
            if (s.getNbt() == null || !s.getNbt().contains(MetalicFrostSpawnItem.GOLEM_KEY, 10)) return false;

            count++;
        }

        return count == 4;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inv, DynamicRegistryManager drm) {
        NbtList golems = new NbtList();

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;

            NbtCompound root = s.getNbt();
            if (root == null) continue;

            NbtCompound golem = root.getCompound(MetalicFrostSpawnItem.GOLEM_KEY).copy();
            golems.add(golem);
        }

        ItemStack out = new ItemStack(ModItems.BIG_METALIC_FROST_SPAWN);
        out.getOrCreateNbt().put(BigMetalicFrostSpawnItem.GOLEMS_KEY, golems);
        return out;
    }

    @Override public boolean fits(int width, int height) { return width * height >= 4; }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.BIG_METALIC_FROST_SPAWN;
    }
}
