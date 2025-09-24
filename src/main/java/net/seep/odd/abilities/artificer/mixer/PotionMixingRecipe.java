package net.seep.odd.abilities.artificer.mixer;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;

import java.util.Arrays;

public record PotionMixingRecipe(
        Identifier id,
        String inKey,          // canonical "cold+gaia+hot"
        int     per,           // mB per essence
        Kind    kind,          // DRINK or THROW
        int     count,         // result count
        String  brewId,        // effect id
        int     color          // ARGB tint
) implements Recipe<Inventory> {

    public enum Kind { DRINK, THROW }

    public static String canonical(String a, String b, String c) {
        String[] arr = new String[]{a.toLowerCase(), b.toLowerCase(), c.toLowerCase()};
        Arrays.sort(arr);
        return arr[0] + "+" + arr[1] + "+" + arr[2];
    }

    // Unused by crafting table; machine-only recipe
    @Override public boolean matches(Inventory inv, World world) { return false; }
    @Override public ItemStack craft(Inventory inv, DynamicRegistryManager drm) { return ItemStack.EMPTY; }
    @Override public boolean fits(int w, int h) { return false; }
    @Override public ItemStack getOutput(DynamicRegistryManager drm) { return ItemStack.EMPTY; }

    @Override
    public Identifier getId() { return id(); }

    @Override public RecipeSerializer<?> getSerializer() { return ArtificerMixerRegistry.POTION_MIXING_SERIALIZER; }
    @Override public RecipeType<?> getType() { return ArtificerMixerRegistry.POTION_MIXING_TYPE; }
}
