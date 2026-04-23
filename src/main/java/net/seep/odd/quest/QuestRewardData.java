
package net.seep.odd.quest;

import com.google.gson.annotations.SerializedName;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class QuestRewardData {
    public enum Type {
        @SerializedName("item")
        ITEM
    }

    public Type type = Type.ITEM;
    public String item = "minecraft:stone";
    public int count = 1;

    public void grant(ServerPlayerEntity player) {
        if (type != Type.ITEM) {
            return;
        }

        ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier(item)), Math.max(1, count));
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
