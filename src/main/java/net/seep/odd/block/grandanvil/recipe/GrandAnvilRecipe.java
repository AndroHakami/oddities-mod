package net.seep.odd.block.grandanvil.recipe;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.List;

public final class GrandAnvilRecipe implements Recipe<Inventory> {
    private final Identifier id;
    private final GearKind gearKind;
    private final Ingredient material;
    private final int difficulty; // 1..7
    private final Identifier enchantId;
    private final int level;

    public GrandAnvilRecipe(Identifier id, GearKind kind, Ingredient material,
                            int difficulty, Identifier enchantId, int level) {
        this.id = id;
        this.gearKind = kind;
        this.material = material;
        this.difficulty = Math.max(1, Math.min(7, difficulty));
        this.enchantId = enchantId;
        this.level = Math.max(1, level);
    }

    public GearKind gearKind() { return gearKind; }
    public Ingredient material() { return material; }
    public int difficulty() { return difficulty; }
    public Identifier enchantId() { return enchantId; }
    public int level() { return level; }

    /** Custom matching used by your block entity. */
    public boolean matches(ItemStack gear, ItemStack catalyst) {
        return !gear.isEmpty() && gearKind.matches(gear) && material.test(catalyst);
    }

    /** Apply the outcome (currently: add enchant) */
    public void apply(ItemStack gear, World world) {
        Enchantment ench = Registries.ENCHANTMENT.get(this.enchantId);
        if (ench != null) gear.addEnchantment(ench, this.level);
    }

    // ---- Recipe<Inventory> implementation (mostly stubs; the anvil never crafts an output stack) ----
    @Override public boolean matches(Inventory inv, World world) {
        if (inv.size() < 2) return false;
        return matches(inv.getStack(0), inv.getStack(1));
    }
    @Override public ItemStack craft(Inventory inv, DynamicRegistryManager drm) { return ItemStack.EMPTY; }
    @Override public boolean fits(int width, int height) { return true; }
    @Override public ItemStack getOutput(DynamicRegistryManager drm) { return ItemStack.EMPTY; }
    @Override public Identifier getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModGrandAnvilRecipes.SERIALIZER; }
    @Override public RecipeType<?> getType() { return ModGrandAnvilRecipes.TYPE; }
    @Override public ItemStack createIcon() { return ItemStack.EMPTY; }
    @Override public DefaultedList<Ingredient> getIngredients() { return (DefaultedList<Ingredient>) List.of(material); }

    // -------------- serializer --------------
    public static final class Serializer implements RecipeSerializer<GrandAnvilRecipe> {
        @Override public GrandAnvilRecipe read(Identifier id, JsonObject json) {
            // All of these must exist and be JSON OBJECTS where noted
            String kindStr = JsonHelper.getString(json, "gear_kind");
            GearKind kind = GearKind.valueOf(kindStr.toUpperCase());

            JsonObject matObj = JsonHelper.getObject(json, "material");
            if (matObj == null) throw new IllegalArgumentException(id + ": missing 'material' object");
            Ingredient mat = Ingredient.fromJson(matObj);

            int diff = JsonHelper.getInt(json, "difficulty", 1);

            JsonObject res = JsonHelper.getObject(json, "result");
            if (res == null) throw new IllegalArgumentException(id + ": missing 'result' object");
            String resType = JsonHelper.getString(res, "type", "enchant");
            if (!"enchant".equals(resType))
                throw new IllegalArgumentException(id + ": result.type must be 'enchant'");

            Identifier enchId = new Identifier(JsonHelper.getString(res, "id"));
            int lvl = JsonHelper.getInt(res, "level", 1);

            return new GrandAnvilRecipe(id, kind, mat, diff, enchId, lvl);
        }

        @Override public GrandAnvilRecipe read(Identifier id, PacketByteBuf buf) {
            GearKind kind = buf.readEnumConstant(GearKind.class);
            Ingredient mat = Ingredient.fromPacket(buf);
            int diff = buf.readVarInt();
            Identifier ench = buf.readIdentifier();
            int lvl = buf.readVarInt();
            return new GrandAnvilRecipe(id, kind, mat, diff, ench, lvl);
        }

        @Override public void write(PacketByteBuf buf, GrandAnvilRecipe rec) {
            buf.writeEnumConstant(rec.gearKind);
            rec.material.write(buf);
            buf.writeVarInt(rec.difficulty);
            buf.writeIdentifier(rec.enchantId);
            buf.writeVarInt(rec.level);
        }
    }
}
