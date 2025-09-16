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
import net.seep.odd.abilities.artificer.mixer.*;

public final class ArtificerMixerRegistry {
    private ArtificerMixerRegistry() {}

    // NOTE: all are lazy; nothing is constructed until we actually register/adopt
    public static Block POTION_MIXER;
    public static BlockEntityType<PotionMixerBlockEntity> POTION_MIXER_BE;
    public static ScreenHandlerType<PotionMixerScreenHandler> POTION_MIXER_SH;
    public static RecipeType<PotionMixingRecipe> POTION_MIXING_TYPE;
    public static RecipeSerializer<PotionMixingRecipe> POTION_MIXING_SERIALIZER;
    public static Item BREW_DRINKABLE;
    public static Item BREW_THROWABLE;

    private static boolean REGISTERED = false;

    public static void registerAll() {
        if (REGISTERED) return;
        REGISTERED = true;

        final Identifier mixId = id("potion_mixer");
        final Identifier mixTypeId = id("potion_mixing");

        // --- Block ---
        if (Registries.BLOCK.containsId(mixId)) {
            POTION_MIXER = Registries.BLOCK.get(mixId);
        } else {
            POTION_MIXER = Registry.register(
                    Registries.BLOCK, mixId,
                    new PotionMixerBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).nonOpaque().strength(3.0f))
            );
        }

        // --- Block Item ---
        if (!Registries.ITEM.containsId(mixId)) {
            Registry.register(Registries.ITEM, mixId, new BlockItem(POTION_MIXER, new Item.Settings()));
        }

        // --- Block Entity ---
        if (Registries.BLOCK_ENTITY_TYPE.containsId(mixId)) {
            @SuppressWarnings("unchecked")
            var existing = (BlockEntityType<PotionMixerBlockEntity>) Registries.BLOCK_ENTITY_TYPE.get(mixId);
            POTION_MIXER_BE = existing;
        } else {
            POTION_MIXER_BE = Registry.register(
                    Registries.BLOCK_ENTITY_TYPE, mixId,
                    FabricBlockEntityTypeBuilder.create(PotionMixerBlockEntity::new, POTION_MIXER).build()
            );
        }

        // --- Fabric Transfer exposure for pipes (only once) ---
        FluidStorage.SIDED.registerForBlockEntity((be, dir) -> be.externalCombinedStorage(), POTION_MIXER_BE);
        net.seep.odd.abilities.artificer.mixer.brew.BrewEffects.registerDefaults();

        // --- Screen Handler ---
        if (Registries.SCREEN_HANDLER.containsId(mixId)) {
            @SuppressWarnings("unchecked")
            var existing = (ScreenHandlerType<PotionMixerScreenHandler>) Registries.SCREEN_HANDLER.get(mixId);
            POTION_MIXER_SH = existing;
        } else {
            POTION_MIXER_SH = Registry.register(
                    Registries.SCREEN_HANDLER, mixId,
                    new ExtendedScreenHandlerType<>(PotionMixerScreenHandler.factory())
            );
        }
        PotionMixerScreenHandler.TYPE = POTION_MIXER_SH;

        // --- Recipe Type + Serializer ---
        if (Registries.RECIPE_TYPE.containsId(mixTypeId)) {
            @SuppressWarnings("unchecked")
            var existing = (RecipeType<PotionMixingRecipe>) Registries.RECIPE_TYPE.get(mixTypeId);
            POTION_MIXING_TYPE = existing;
        } else {
            POTION_MIXING_TYPE = Registry.register(
                    Registries.RECIPE_TYPE, mixTypeId,
                    new RecipeType<>() { public String toString() { return "odd:potion_mixing"; } }
            );
        }

        if (Registries.RECIPE_SERIALIZER.containsId(mixTypeId)) {
            var existing = Registries.RECIPE_SERIALIZER.get(mixTypeId);
            // keep the existing one; no assignment needed unless you want a handle
        } else {
            POTION_MIXING_SERIALIZER = Registry.register(
                    Registries.RECIPE_SERIALIZER, mixTypeId,
                    new PotionMixingRecipeSerializer()
            );
        }

        // --- Items (lazy-construct only if missing) ---
        Identifier drinkId = id("brew_drinkable");
        if (Registries.ITEM.containsId(drinkId)) {
            BREW_DRINKABLE = Registries.ITEM.get(drinkId);
        } else {
            BREW_DRINKABLE = Registry.register(
                    Registries.ITEM, drinkId,
                    new ArtificerBrewItem(new Item.Settings(), ArtificerBrewItem.Kind.DRINK)
            );
        }

        Identifier throwId = id("brew_throwable");
        if (Registries.ITEM.containsId(throwId)) {
            BREW_THROWABLE = Registries.ITEM.get(throwId);
        } else {
            BREW_THROWABLE = Registry.register(
                    Registries.ITEM, throwId,
                    new ArtificerBrewItem(new Item.Settings().maxCount(16), ArtificerBrewItem.Kind.THROW)
            );
        }

        // Packets
        MixerNet.register();
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                POTION_MIXER_SH,
                new net.minecraft.client.gui.screen.ingame.HandledScreens.Provider<
                        net.seep.odd.abilities.artificer.mixer.PotionMixerScreenHandler,
                        net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen
                        >() {
                    @Override
                    public net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen create(
                            net.seep.odd.abilities.artificer.mixer.PotionMixerScreenHandler handler,
                            net.minecraft.entity.player.PlayerInventory inv,
                            net.minecraft.text.Text title) {
                        return new net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen(handler, inv, title);
                    }
                }
        );
    }

    private static Identifier id(String p) { return new Identifier("odd", p); }
}
