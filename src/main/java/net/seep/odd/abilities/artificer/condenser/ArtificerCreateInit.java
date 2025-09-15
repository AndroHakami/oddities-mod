package net.seep.odd.abilities.artificer.condenser;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class ArtificerCreateInit {
    public static final String MODID = Oddities.MOD_ID; // "odd"

    public static Block CONDENSER_BLOCK;
    public static BlockEntityType<CondenserBlockEntity> CONDENSER_BE;
    public static ScreenHandlerType<CondenserScreenHandler> CONDENSER_SH;

    // Placeholder bucket items (6 essences)
    public static Item BUCKET_LIGHT, BUCKET_GAIA, BUCKET_HOT, BUCKET_COLD, BUCKET_DEATH, BUCKET_LIFE;

    public static void register() {
        CONDENSER_BLOCK = Registry.register(Registries.BLOCK, id("condenser"),
                new CondenserBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.0f).nonOpaque()));

        Registry.register(Registries.ITEM, id("condenser"),
                new BlockItem(CONDENSER_BLOCK, new Item.Settings()));

        CONDENSER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("condenser_be"),
                FabricBlockEntityTypeBuilder.create(CondenserBlockEntity::new, CONDENSER_BLOCK).build());



        // Essence bucket placeholders (simple items; will become real Fluid buckets later)
        BUCKET_LIGHT = regBucket("light");
        BUCKET_GAIA  = regBucket("gaia");
        BUCKET_HOT   = regBucket("hot");
        BUCKET_COLD  = regBucket("cold");
        BUCKET_DEATH = regBucket("death");
        BUCKET_LIFE  = regBucket("life");

        // C2S networking
        CondenserNet.registerServer();
    }

    private static Item regBucket(String essence) {
        return Registry.register(Registries.ITEM, id("essence_bucket_" + essence),
                new EssenceBucketItem(new Item.Settings().maxCount(1), essence));
    }

    public static Identifier id(String p) { return new Identifier(MODID, p); }

    /* client-only screen + packet hooks */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(CONDENSER_SH, CondenserScreen::new);
        CondenserNet.registerClient();
    }
}
