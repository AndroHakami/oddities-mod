package net.seep.odd.abilities.artificer.condenser;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.seep.odd.abilities.artificer.EssenceType;

public class EssenceBucketItem extends Item {
    private final String essenceKey; // "gaia", "hot", etc.
    public EssenceBucketItem(Settings s, String essenceKey) {
        super(s);
        this.essenceKey = essenceKey;
    }
    @Override
    public Text getName(ItemStack stack) {
        String nice = essenceKey.substring(0,1).toUpperCase() + essenceKey.substring(1);
        return Text.literal("Bucket of " + nice + " Essence").formatted(Formatting.AQUA);
    }
    public String essenceKey() { return essenceKey; }
}
