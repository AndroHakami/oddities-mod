package net.seep.odd.abilities.artificer.mixer;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.inventory.Inventory;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;

import java.util.Arrays;
import java.util.List;

public record PotionMixingRecipe(Identifier id,
                                 List<EssenceType> essences, // size 3, sorted by key
                                 int perEssenceMb,            // cost per essence (mB)
                                 Kind outputKind,             // DRINKABLE or THROWABLE
                                 int count,                   // items produced
                                 String brewId,               // freeform id for your effects later
                                 int color                     // ARGB tint for item render
) implements Recipe<Inventory> {

    public enum Kind { DRINKABLE, THROWABLE }

    public static List<EssenceType> canonical(EssenceType a, EssenceType b, EssenceType c) {
        EssenceType[] arr = new EssenceType[] { a, b, c };
        Arrays.sort(arr, (x, y) -> x.key.compareTo(y.key));
        return List.of(arr);
    }

    @Override public boolean matches(Inventory inv, World world) { return false; } // not used (machine-only)
    @Override public ItemStack craft(Inventory inv, net.minecraft.registry.DynamicRegistryManager drm) { return ItemStack.EMPTY; }
    @Override public boolean fits(int w, int h) { return false; }
    @Override public ItemStack getOutput(net.minecraft.registry.DynamicRegistryManager drm) { return ItemStack.EMPTY; }

    @Override
    public Identifier getId() {
        return null;
    }

    @Override public RecipeSerializer<?> getSerializer() { return ArtificerMixerRegistry.POTION_MIXING_SERIALIZER; }
    @Override public RecipeType<?> getType() { return ArtificerMixerRegistry.POTION_MIXING_TYPE; }
}
