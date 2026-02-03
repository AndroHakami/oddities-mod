package net.seep.odd.block.supercooker.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

public class SuperCookerRecipeSerializer implements RecipeSerializer<SuperCookerRecipe> {
    @Override
    public SuperCookerRecipe read(Identifier id, JsonObject json) {
        JsonArray arr = json.getAsJsonArray("ingredients");
        DefaultedList<Ingredient> ings = DefaultedList.of();
        for (int i = 0; i < arr.size(); i++) ings.add(Ingredient.fromJson(arr.get(i)));

        ItemStack out = ShapedRecipe.outputFromJson(json.getAsJsonObject("result"));
        int cookTime = json.has("cookTime") ? json.get("cookTime").getAsInt() : 200;
        int minStirs = json.has("minStirs") ? json.get("minStirs").getAsInt() : 3;

        return new SuperCookerRecipe(id, ings, out, cookTime, minStirs);
    }

    @Override
    public SuperCookerRecipe read(Identifier id, PacketByteBuf buf) {
        int count = buf.readVarInt();
        DefaultedList<Ingredient> ings = DefaultedList.ofSize(count, Ingredient.EMPTY);
        for (int i = 0; i < count; i++) ings.set(i, Ingredient.fromPacket(buf));

        ItemStack out = buf.readItemStack();
        int cookTime = buf.readVarInt();
        int minStirs = buf.readVarInt();

        return new SuperCookerRecipe(id, ings, out, cookTime, minStirs);
    }

    @Override
    public void write(PacketByteBuf buf, SuperCookerRecipe recipe) {
        buf.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ing : recipe.getIngredients()) ing.write(buf);

        buf.writeItemStack(recipe.getOutputCopy());
        buf.writeVarInt(recipe.getCookTime());
        buf.writeVarInt(recipe.getMinStirs());
    }
}
