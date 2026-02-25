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
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.artificer.mixer.*;
import net.seep.odd.abilities.artificer.mixer.brew.BrewEffects;

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

        final Identifier mixId = id("potion_mixer");
        final Identifier beId  = id("potion_mixer_be");
        final Identifier shId  = id("potion_mixer");        // fine to share id with block
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

        // Block Entity (FIXED ID!)
        POTION_MIXER_BE = Registries.BLOCK_ENTITY_TYPE.containsId(beId)
                ? (BlockEntityType<PotionMixerBlockEntity>) Registries.BLOCK_ENTITY_TYPE.get(beId)
                : Registry.register(Registries.BLOCK_ENTITY_TYPE, beId,
                FabricBlockEntityTypeBuilder.create(PotionMixerBlockEntity::new, POTION_MIXER).build());

        // Fluid exposure for Create pipes (controller + forwarding from parts)
        MixerFluidStorage.register();

        // Brew defaults

        BrewEffects.registerDefaults();


        // ScreenHandler (FIXED: never touch be.getPos() when be may be null)
        POTION_MIXER_SH = Registries.SCREEN_HANDLER.containsId(shId)
                ? (ScreenHandlerType<PotionMixerScreenHandler>) Registries.SCREEN_HANDLER.get(shId)
                : Registry.register(Registries.SCREEN_HANDLER, shId,
                new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
                    BlockPos pos = buf.readBlockPos();
                    return new PotionMixerScreenHandler(syncId, inv, pos);
                }));

        // IMPORTANT: assign TYPE so ScreenHandler super(TYPE, id) never gets null
        PotionMixerScreenHandler.TYPE = POTION_MIXER_SH;

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

        // Networking
        MixerNet.registerServer();

        REGISTERED_COMMON = true;
    }

    private static Identifier id(String p) { return new Identifier("odd", p); }

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean REGISTERED_CLIENT = false;

        public static void register() {
            if (REGISTERED_CLIENT) return;

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
            net.seep.odd.abilities.artificer.item.client.VacuumSoundController.init();

            if (BREW_DRINKABLE != null && BREW_THROWABLE != null) {
                net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
                    // ✅ tint only the liquid overlay (layer0)
                    if (tintIndex != 0) return -1; // -1 = no tint

                    if (stack.hasNbt() && stack.getNbt().contains("odd_brew_color")) {
                        int c = stack.getNbt().getInt("odd_brew_color");
                        return c & 0x00FFFFFF; // ✅ make sure it's RGB (ignore alpha if you stored ARGB)
                    }
                    return 0xFFFFFF; // default white
                }, BREW_DRINKABLE, BREW_THROWABLE);
            }

            MixerNet.registerClient();
            REGISTERED_CLIENT = true;
        }
    }
}
