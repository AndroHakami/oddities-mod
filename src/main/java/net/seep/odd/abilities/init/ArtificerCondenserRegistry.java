package net.seep.odd.abilities.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.artificer.condenser.*;

@SuppressWarnings("unused")
public final class ArtificerCondenserRegistry {
    private ArtificerCondenserRegistry() {}

    // ==== instances (common-safe) ====
    public static final Block CONDENSER_BLOCK = new CondenserBlock(
            FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(3.0f).requiresTool().nonOpaque()
    );
    public static final BlockItem CONDENSER_ITEM = new BlockItem(CONDENSER_BLOCK, new Item.Settings());

    public static final BlockEntityType<CondenserBlockEntity> CONDENSER_BE =
            FabricBlockEntityTypeBuilder.create(CondenserBlockEntity::new, CONDENSER_BLOCK).build();

    public static final ScreenHandlerType<CondenserScreenHandler> CONDENSER_SH =
            new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
                BlockPos pos = buf.readBlockPos();
                CondenserBlockEntity be = null;
                if (inv.player != null && inv.player.getWorld() != null) {
                    var w = inv.player.getWorld();
                    var e = w.getBlockEntity(pos);
                    if (e instanceof CondenserBlockEntity cbe) be = cbe;
                }
                // be may be null client-side for a tick; handler copes because it only reads slots
                return new CondenserScreenHandler(syncId, inv, be);
            });

    private static boolean REGISTERED_COMMON = false;
    private static boolean REGISTERED_CLIENT = false;

    /** Server & client (common) registration only. */
    public static void registerCommon() {
        if (REGISTERED_COMMON) return;

        if (!Registries.BLOCK.containsId(id("condenser")))
            Registry.register(Registries.BLOCK, id("condenser"), CONDENSER_BLOCK);

        if (!Registries.ITEM.containsId(id("condenser")))
            Registry.register(Registries.ITEM,  id("condenser"), CONDENSER_ITEM);

        if (!Registries.BLOCK_ENTITY_TYPE.containsId(id("condenser_be")))
            Registry.register(Registries.BLOCK_ENTITY_TYPE, id("condenser_be"), CONDENSER_BE);

        if (!Registries.SCREEN_HANDLER.containsId(id("condenser")))
            Registry.register(Registries.SCREEN_HANDLER, id("condenser"), CONDENSER_SH);

        // C2S networking (common)
        CondenserNet.registerServer();

        REGISTERED_COMMON = true;
    }

    /** Client-only hookups (screen + client packets). */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        if (REGISTERED_CLIENT) return;

        // Be explicit with generics to avoid inference failures
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                CONDENSER_SH,
                new net.minecraft.client.gui.screen.ingame.HandledScreens.Provider<
                        CondenserScreenHandler,
                        CondenserScreen>() {
                    @Override
                    public CondenserScreen create(CondenserScreenHandler handler,
                                                  net.minecraft.entity.player.PlayerInventory inv,
                                                  net.minecraft.text.Text title) {
                        return new CondenserScreen(handler, inv, title);
                    }
                }
        );

        CondenserNet.registerClient();
        REGISTERED_CLIENT = true;
    }

    private static Identifier id(String path) { return new Identifier("odd", path); }
}
