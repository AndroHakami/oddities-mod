// src/main/java/net/seep/odd/block/combiner/recipe/CombinerRecipe.java
package net.seep.odd.block.combiner.recipe;

import com.google.gson.JsonObject;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import net.seep.odd.block.combiner.enchant.TrimEnchantMap;

import java.util.List;

public final class CombinerRecipe implements Recipe<Inventory> {
    private final Identifier id;
    private final net.seep.odd.block.grandanvil.recipe.GearKind gearKind;
    private final Ingredient trim;
    private final int difficulty; // 1..7
    private final int level;      // 1+

    public CombinerRecipe(Identifier id,
                          net.seep.odd.block.grandanvil.recipe.GearKind kind,
                          Ingredient trim,
                          int difficulty,
                          int level) {
        this.id = id;
        this.gearKind = kind;
        this.trim = trim;
        this.difficulty = Math.max(1, Math.min(7, difficulty));
        this.level = Math.max(1, level);
    }

    public net.seep.odd.block.grandanvil.recipe.GearKind gearKind() { return gearKind; }
    public Ingredient trim() { return trim; }
    public int difficulty() { return difficulty; }
    public int level() { return level; }

    public boolean matches(ItemStack gear, ItemStack catalystTrim) {
        return !gear.isEmpty()
                && gearKind.matches(gear)
                && trim.test(catalystTrim)
                && TrimEnchantMap.enchantFor(catalystTrim.getItem()) != null;
    }

    public void apply(ItemStack gear, ItemStack catalystTrim, World world) {
        var ench = TrimEnchantMap.enchantFor(catalystTrim.getItem());
        if (ench != null && ench.isAcceptableItem(gear)) {
            gear.addEnchantment(ench, level);
        }
    }

    @Override public boolean matches(Inventory inv, World world) {
        if (inv.size() < 2) return false;
        return matches(inv.getStack(0), inv.getStack(1));
    }
    @Override public ItemStack craft(Inventory inv, DynamicRegistryManager drm) { return ItemStack.EMPTY; }
    @Override public boolean fits(int width, int height) { return true; }
    @Override public ItemStack getOutput(DynamicRegistryManager drm) { return ItemStack.EMPTY; }
    @Override public Identifier getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModCombinerRecipes.SERIALIZER; }
    @Override public RecipeType<?> getType() { return ModCombinerRecipes.TYPE; }
    @Override public ItemStack createIcon() { return ItemStack.EMPTY; }
    @Override public DefaultedList<Ingredient> getIngredients() { return (DefaultedList<Ingredient>) List.of(trim); }

    public static final class Serializer implements RecipeSerializer<CombinerRecipe> {
        @Override public CombinerRecipe read(Identifier id, JsonObject json) {
            String kindStr = JsonHelper.getString(json, "gear_kind");
            var kind = net.seep.odd.block.grandanvil.recipe.GearKind.valueOf(kindStr.toUpperCase());

            JsonObject trimObj = JsonHelper.getObject(json, "trim");
            if (trimObj == null) throw new IllegalArgumentException(id + ": missing 'trim' object");
            Ingredient trim = Ingredient.fromJson(trimObj);

            int diff = JsonHelper.getInt(json, "difficulty", 1);
            int lvl  = JsonHelper.getInt(json, "level", 1);

            return new CombinerRecipe(id, kind, trim, diff, lvl);
        }

        @Override public CombinerRecipe read(Identifier id, PacketByteBuf buf) {
            var kind = buf.readEnumConstant(net.seep.odd.block.grandanvil.recipe.GearKind.class);
            Ingredient trim = Ingredient.fromPacket(buf);
            int diff = buf.readVarInt();
            int lvl = buf.readVarInt();
            return new CombinerRecipe(id, kind, trim, diff, lvl);
        }

        @Override public void write(PacketByteBuf buf, CombinerRecipe rec) {
            buf.writeEnumConstant(rec.gearKind);
            rec.trim.write(buf);
            buf.writeVarInt(rec.difficulty);
            buf.writeVarInt(rec.level);
        }
    }
}