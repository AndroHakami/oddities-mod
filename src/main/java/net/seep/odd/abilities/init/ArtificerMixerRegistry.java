package net.seep.odd.abilities.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.artificer.mixer.*;

@SuppressWarnings("unused")
public final class ArtificerMixerRegistry {
    private ArtificerMixerRegistry() {}

    public static Block POTION_MIXER;
    public static BlockEntityType<PotionMixerBlockEntity> POTION_MIXER_BE;
    public static ScreenHandlerType<PotionMixerScreenHandler> POTION_MIXER_SH;
    public static RecipeType<PotionMixingRecipe> POTION_MIXING_TYPE;
    public static RecipeSerializer<PotionMixingRecipe> POTION_MIXING_SERIALIZER;
    public static Item BREW_DRINKABLE;
    public static Item BREW_THROWABLE;

    private static boolean REGISTERED_COMMON = false;

    public static void registerCommon() {
        if (REGISTERED_COMMON) return;

        final Identifier mixId     = id("potion_mixer");
        final Identifier mixTypeId = id("potion_mixing");

        // Block
        POTION_MIXER = Registries.BLOCK.containsId(mixId)
                ? Registries.BLOCK.get(mixId)
                : Registry.register(Registries.BLOCK, mixId,
                new PotionMixerMegaBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK)
                        .nonOpaque().strength(3.0f)));

        // Block Item
        if (!Registries.ITEM.containsId(mixId)) {
            Registry.register(Registries.ITEM, mixId, new BlockItem(POTION_MIXER, new Item.Settings()));
        }

        // Block Entity
        POTION_MIXER_BE = Registries.BLOCK_ENTITY_TYPE.containsId(mixId)
                ? (BlockEntityType<PotionMixerBlockEntity>) Registries.BLOCK_ENTITY_TYPE.get(mixId)
                : Registry.register(Registries.BLOCK_ENTITY_TYPE, mixId,
                FabricBlockEntityTypeBuilder.create(PotionMixerBlockEntity::new, POTION_MIXER).build());

        // Fluid exposure for pipes
        FluidStorage.SIDED.registerForBlockEntity((be, dir) -> be.externalCombinedStorage(), POTION_MIXER_BE);

        // Brew defaults
        net.seep.odd.abilities.artificer.mixer.brew.BrewEffects.registerDefaults();

        // ScreenHandler — inline factory so we don't need a static TYPE/factory() on the handler
        POTION_MIXER_SH = Registries.SCREEN_HANDLER.containsId(mixId)
                ? (ScreenHandlerType<PotionMixerScreenHandler>) Registries.SCREEN_HANDLER.get(mixId)
                : Registry.register(Registries.SCREEN_HANDLER, mixId,
                new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
                    BlockPos pos = buf.readBlockPos();
                    PotionMixerBlockEntity be = null;
                    if (inv.player != null && inv.player.getWorld() != null) {
                        var w = inv.player.getWorld();
                        var e = w.getBlockEntity(pos);
                        if (e instanceof PotionMixerBlockEntity pbe) be = pbe;
                    }
                    return new PotionMixerScreenHandler(syncId, inv, be.getPos());
                }));

        // Recipe Type + Serializer
        POTION_MIXING_TYPE = Registries.RECIPE_TYPE.containsId(mixTypeId)
                ? (RecipeType<PotionMixingRecipe>) Registries.RECIPE_TYPE.get(mixTypeId)
                : Registry.register(Registries.RECIPE_TYPE, mixTypeId, new RecipeType<>() {
            public String toString() { return "odd:potion_mixing"; }
        });

        POTION_MIXING_SERIALIZER = Registries.RECIPE_SERIALIZER.containsId(mixTypeId)
                ? (RecipeSerializer<PotionMixingRecipe>) Registries.RECIPE_SERIALIZER.get(mixTypeId)
                : Registry.register(Registries.RECIPE_SERIALIZER, mixTypeId, new PotionMixingRecipeSerializer());

        // Items
        Identifier drinkId = id("brew_drinkable");
        Identifier throwId = id("brew_throwable");

        BREW_DRINKABLE = Registries.ITEM.containsId(drinkId)
                ? Registries.ITEM.get(drinkId)
                : Registry.register(Registries.ITEM, drinkId,
                new ArtificerBrewItem(new Item.Settings(), ArtificerBrewItem.Kind.DRINK));

        BREW_THROWABLE = Registries.ITEM.containsId(throwId)
                ? Registries.ITEM.get(throwId)
                : Registry.register(Registries.ITEM, throwId,
                new ArtificerBrewItem(new Item.Settings().maxCount(16), ArtificerBrewItem.Kind.THROW));

        // Networking (server/common)
        MixerNet.registerServer();

        REGISTERED_COMMON = true;
    }

    private static Identifier id(String p) { return new Identifier("odd", p); }

    /* ----------- client-only bits live here (never loaded on server) ----------- */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean REGISTERED_CLIENT = false;

        public static void register() {
            if (REGISTERED_CLIENT) return;

            // Be explicit with generics so type inference can't fail
            net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                    POTION_MIXER_SH,
                    new net.minecraft.client.gui.screen.ingame.HandledScreens.Provider<
                            PotionMixerScreenHandler,
                            net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen>() {
                        @Override
                        public net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen create(
                                PotionMixerScreenHandler handler,
                                net.minecraft.entity.player.PlayerInventory inv,
                                net.minecraft.text.Text title) {
                            return new net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen(handler, inv, title);
                        }
                    }
            );

            // Item tint (overlay layer index 1)
            if (BREW_DRINKABLE != null && BREW_THROWABLE != null) {
                net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
                    if (tintIndex == 1 && stack.hasNbt()) return stack.getNbt().getInt("odd_brew_color");
                    return 0xFFFFFFFF;
                }, BREW_DRINKABLE, BREW_THROWABLE);
            }

            // Client side of mixer net
            MixerNet.registerClient();

            REGISTERED_CLIENT = true;
        }
    }
}
