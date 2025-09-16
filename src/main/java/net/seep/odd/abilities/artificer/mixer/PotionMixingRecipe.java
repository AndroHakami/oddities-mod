package net.seep.odd.abilities.artificer.mixer;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Machine-only recipe: choose exactly 3 distinct essences (orderless) to brew a potion.
 * JSON should provide the "essences" array (size 3), perEssenceMb, output kind, count, brewId, color (ARGB).
 */
public record PotionMixingRecipe(
        Identifier id,
        List<EssenceType> essences, // size 3, sorted by key
        int perEssenceMb,           // cost per essence (mB)
        Kind outputKind,            // DRINKABLE or THROWABLE
        int count,                  // items produced
        String brewId,              // freeform id for your effects later
        int color                   // ARGB tint for item render
) implements Recipe<Inventory> {

    public enum Kind { DRINKABLE, THROWABLE }

    /** Create a canonical, sorted list of 3 essences (by key). */
    public static List<EssenceType> canonical(EssenceType a, EssenceType b, EssenceType c) {
        EssenceType[] arr = new EssenceType[] { a, b, c };
        Arrays.sort(arr, (x, y) -> x.key.compareTo(y.key));
        return List.of(arr);
    }

    /* ---------------- Machine-only Recipe<Inventory> plumbing ---------------- */

    @Override public boolean matches(Inventory inv, World world) { return false; } // not used
    @Override public ItemStack craft(Inventory inv, DynamicRegistryManager drm) { return ItemStack.EMPTY; }
    @Override public boolean fits(int w, int h) { return false; }
    @Override public ItemStack getOutput(DynamicRegistryManager drm) { return ItemStack.EMPTY; }

    @Override public Identifier getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ArtificerMixerRegistry.POTION_MIXING_SERIALIZER; }
    @Override public RecipeType<?> getType() { return ArtificerMixerRegistry.POTION_MIXING_TYPE; }

    /* ---------------- Helpers used by the mixer block entity ---------------- */

    /** Orderless set of required essences for easy comparison. */
    public Set<EssenceType> requiredEssences() {
        EnumSet<EssenceType> set = EnumSet.noneOf(EssenceType.class);
        set.addAll(essences);
        return set;
    }

    /** Orderless equality check against a chosen set of 3 essences. */
    public boolean matches(Set<EssenceType> chosen) {
        return chosen != null && chosen.size() == 3 && Objects.equals(requiredEssences(), chosen);
    }

    /**
     * Build the resulting potion ItemStack.
     * - DRINKABLE -> Items.POTION
     * - THROWABLE -> Items.SPLASH_POTION
     * Adds:
     *   - CustomPotionColor (ARGB -> RGB used; alpha ignored by vanilla but kept in tag)
     *   - "odd:brew_id" string tag so you can map effects client/server later
     */
    public ItemStack assembleItem() {
        ItemStack stack = new ItemStack(outputKind == Kind.THROWABLE ? Items.SPLASH_POTION : Items.POTION, Math.max(1, count));

        // vanilla uses RGB; carry ARGB in a separate tag
        int rgb = color & 0x00FFFFFF;
        stack.getOrCreateNbt().putInt("CustomPotionColor", rgb);
        stack.getOrCreateNbt().putInt("odd:brew_argb", color);
        stack.getOrCreateNbt().putString("odd:brew_id", brewId == null ? "" : brewId);

        return stack;
    }
}
