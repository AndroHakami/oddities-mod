package net.seep.odd.abilities.artificer.condenser;

import net.minecraft.item.ItemStack;
import net.seep.odd.abilities.artificer.EssenceType;

public final class EssenceBuckets {
    private EssenceBuckets() {}

    public static ItemStack createFilled(EssenceType t) {
        return switch (t) {
            case LIGHT -> new ItemStack(ArtificerCreateInit.BUCKET_LIGHT);
            case GAIA  -> new ItemStack(ArtificerCreateInit.BUCKET_GAIA);
            case HOT   -> new ItemStack(ArtificerCreateInit.BUCKET_HOT);
            case COLD  -> new ItemStack(ArtificerCreateInit.BUCKET_COLD);
            case DEATH -> new ItemStack(ArtificerCreateInit.BUCKET_DEATH);
            case LIFE  -> new ItemStack(ArtificerCreateInit.BUCKET_LIFE);
        };
    }
}
