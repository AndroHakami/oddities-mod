package net.seep.odd.abilities.artificer.mixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.util.Identifier;

public class PotionMixingRecipeSerializer implements RecipeSerializer<PotionMixingRecipe> {

    @Override
    public PotionMixingRecipe read(Identifier id, JsonObject json) {
        // essences[3] -> canonical key
        JsonArray arr = json.getAsJsonArray("essences");
        String e0 = arr.get(0).getAsString().toLowerCase();
        String e1 = arr.get(1).getAsString().toLowerCase();
        String e2 = arr.get(2).getAsString().toLowerCase();
        String inKey = PotionMixingRecipe.canonical(e0, e1, e2);

        int per    = json.has("per")    ? json.get("per").getAsInt()          : 250;
        int count  = json.has("count")  ? json.get("count").getAsInt()        : 1;
        String kindStr = json.has("kind") ? json.get("kind").getAsString()    : "DRINK";
        PotionMixingRecipe.Kind kind = "THROW".equalsIgnoreCase(kindStr)
                ? PotionMixingRecipe.Kind.THROW
                : PotionMixingRecipe.Kind.DRINK;

        String brewId = json.has("brewId") ? json.get("brewId").getAsString() : "";
        int color = json.has("color") ? json.get("color").getAsInt() : 0xFFFFFFFF;

        return new PotionMixingRecipe(id, inKey, per, kind, count, brewId, color);
    }

    @Override
    public PotionMixingRecipe read(Identifier id, PacketByteBuf buf) {
        String inKey = buf.readString();
        int per      = buf.readVarInt();
        PotionMixingRecipe.Kind kind = buf.readEnumConstant(PotionMixingRecipe.Kind.class);
        int count    = buf.readVarInt();
        String brewId= buf.readString();
        int color    = buf.readInt();
        return new PotionMixingRecipe(id, inKey, per, kind, count, brewId, color);
    }

    @Override
    public void write(PacketByteBuf buf, PotionMixingRecipe recipe) {
        buf.writeString(recipe.inKey());
        buf.writeVarInt(recipe.per());
        buf.writeEnumConstant(recipe.kind());
        buf.writeVarInt(recipe.count());
        buf.writeString(recipe.brewId());
        buf.writeInt(recipe.color());
    }
}
