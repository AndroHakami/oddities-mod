package net.seep.odd.abilities.artificer.condenser;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.PacketByteBuf;
import net.seep.odd.Oddities;

public final class ArtificerCreateInit {
    public static final String MODID = Oddities.MOD_ID; // "odd"

    public static Block CONDENSER_BLOCK;
    public static BlockEntityType<CondenserBlockEntity> CONDENSER_BE;
    public static ScreenHandlerType<CondenserScreenHandler> CONDENSER_SH;

    // Placeholder bucket items (6 essences)
    public static Item BUCKET_LIGHT, BUCKET_GAIA, BUCKET_HOT, BUCKET_COLD, BUCKET_DEATH, BUCKET_LIFE;

    public static void register() {
        // ---- Block + item ----
        CONDENSER_BLOCK = Registry.register(Registries.BLOCK, id("condenser"),
                new CondenserBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.0f).nonOpaque()));

        Registry.register(Registries.ITEM, id("condenser"),
                new BlockItem(CONDENSER_BLOCK, new Item.Settings()));

        // ---- Block entity ----
        CONDENSER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("condenser_be"),
                FabricBlockEntityTypeBuilder.create(CondenserBlockEntity::new, CONDENSER_BLOCK).build());

        // ---- ScreenHandler (COMMON/SERVER-SAFE) ----
        // We send the BE BlockPos in the open packet and look it up client-side.
        CONDENSER_SH = Registry.register(Registries.SCREEN_HANDLER, id("condenser"),
                new ExtendedScreenHandlerType<>((int syncId, PlayerInventory inv, PacketByteBuf buf) -> {
                    BlockPos pos = buf.readBlockPos();
                    BlockEntity be = inv.player.getWorld().getBlockEntity(pos);
                    return new CondenserScreenHandler(syncId, inv, (CondenserBlockEntity) be);
                }));

        // ---- Placeholder essence buckets (simple items for now) ----
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

    /* ---------------- client-only bits in an inner class (never loaded on server) ---------------- */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private Client() {}

        public static void register() {
            // Use vanilla HandledScreens (client API). This reference stays client-side only.
            net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                    ArtificerCreateInit.CONDENSER_SH,
                    CondenserScreen::new
            );
            CondenserNet.registerClient();
        }
    }
}
