package net.seep.odd.abilities.artificer.mixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.EssenceType;

public class PotionMixingRecipeSerializer implements RecipeSerializer<PotionMixingRecipe> {

    @Override
    public PotionMixingRecipe read(Identifier id, JsonObject jo) {
        // "essences": ["gaia","light","hot"]
        JsonArray arr = jo.getAsJsonArray("essences");
        if (arr.size() != 3) throw new IllegalArgumentException("Potion mixing needs exactly 3 essences");
        EssenceType e1 = EssenceType.byKey(arr.get(0).getAsString());
        EssenceType e2 = EssenceType.byKey(arr.get(1).getAsString());
        EssenceType e3 = EssenceType.byKey(arr.get(2).getAsString());
        if (e1 == null || e2 == null || e3 == null) throw new IllegalArgumentException("Unknown essence key");

        int per = jo.has("per_essence_mb") ? jo.get("per_essence_mb").getAsInt() : 250;

        String kindStr = jo.has("kind") ? jo.get("kind").getAsString() : "drinkable";
        PotionMixingRecipe.Kind kind = kindStr.equalsIgnoreCase("throwable")
                ? PotionMixingRecipe.Kind.THROWABLE : PotionMixingRecipe.Kind.DRINKABLE;

        int count = jo.has("count") ? jo.get("count").getAsInt() : 1;

        String brewId = jo.has("brew_id") ? jo.get("brew_id").getAsString() : id.getPath();
        int color = jo.has("color_argb") ? (int)Long.parseLong(jo.get("color_argb").getAsString().replace("0x",""),16)
                : 0xFFB0FFB0;

        return new PotionMixingRecipe(
                id,
                PotionMixingRecipe.canonical(e1, e2, e3),
                per,
                kind,
                count,
                brewId,
                color
        );
    }

    @Override
    public void write(PacketByteBuf buf, PotionMixingRecipe r) {
        buf.writeVarInt(r.essences().size());
        for (EssenceType e : r.essences()) buf.writeString(e.key);
        buf.writeVarInt(r.perEssenceMb());
        buf.writeEnumConstant(r.outputKind());
        buf.writeVarInt(r.count());
        buf.writeString(r.brewId());
        buf.writeInt(r.color());
    }

    @Override
    public PotionMixingRecipe read(Identifier id, PacketByteBuf buf) {
        int n = buf.readVarInt();
        EssenceType[] in = new EssenceType[n];
        for (int i = 0; i < n; i++) in[i] = EssenceType.byKey(buf.readString());
        int per = buf.readVarInt();
        PotionMixingRecipe.Kind kind = buf.readEnumConstant(PotionMixingRecipe.Kind.class);
        int count = buf.readVarInt();
        String brewId = buf.readString();
        int color = buf.readInt();

        return new PotionMixingRecipe(
                id,
                PotionMixingRecipe.canonical(in[0], in[1], in[2]),
                per, kind, count, brewId, color
        );
    }
}
