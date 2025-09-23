package net.seep.odd.abilities.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
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

    // Prebuilt instances
    public static final Block CONDENSER_BLOCK = new CondenserBlock(
            FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(3.0f).requiresTool()
    );
    public static final BlockItem CONDENSER_ITEM = new BlockItem(CONDENSER_BLOCK, new Item.Settings());

    public static final BlockEntityType<CondenserBlockEntity> CONDENSER_BE =
            FabricBlockEntityTypeBuilder.create(CondenserBlockEntity::new, CONDENSER_BLOCK).build();

    public static final ScreenHandlerType<CondenserScreenHandler> CONDENSER_SH =
            new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
                BlockPos pos = buf.readBlockPos();
                var be = (CondenserBlockEntity) inv.player.getWorld().getBlockEntity(pos);
                return new CondenserScreenHandler(syncId, inv, be);
            });

    private static boolean REGISTERED_COMMON = false;
    private static boolean REGISTERED_CLIENT = false;

    /** Backward-compatible, safe wrapper. */
    public static void registerAll() {
        registerCommon();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerClient();
        }
    }

    /** Server & client (common) registration only. */
    public static void registerCommon() {
        if (REGISTERED_COMMON) return;

        // Block + item
        if (!Registries.BLOCK.containsId(id("condenser"))) {
            Registry.register(Registries.BLOCK, id("condenser"), CONDENSER_BLOCK);
        }
        if (!Registries.ITEM.containsId(id("condenser"))) {
            Registry.register(Registries.ITEM,  id("condenser"), CONDENSER_ITEM);
        }

        // Block entity + screen handler type (no client screens here)
        if (!Registries.BLOCK_ENTITY_TYPE.containsId(id("condenser_be"))) {
            Registry.register(Registries.BLOCK_ENTITY_TYPE, id("condenser_be"), CONDENSER_BE);
        }
        if (!Registries.SCREEN_HANDLER.containsId(id("condenser"))) {
            Registry.register(Registries.SCREEN_HANDLER,   id("condenser"),    CONDENSER_SH);
        }

        // Server/common networking
        CondenserNet.registerServer();

        REGISTERED_COMMON = true;
    }

    /** Client-only hookups (screen + client packets). */
    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        if (REGISTERED_CLIENT) return;

        net.minecraft.client.gui.screen.ingame.HandledScreens.register(CONDENSER_SH, CondenserScreen::new);
        CondenserNet.registerClient();

        REGISTERED_CLIENT = true;
    }

    private static Identifier id(String path) { return new Identifier("odd", path); }
}
