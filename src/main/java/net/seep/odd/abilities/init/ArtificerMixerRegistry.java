package net.seep.odd.abilities.init;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.seep.odd.abilities.artificer.mixer.*;

public final class ArtificerMixerRegistry {
    private ArtificerMixerRegistry() {}

    public static final Block POTION_MIXER =
            new PotionMixerBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(3.5f).requiresTool());

    public static final BlockItem POTION_MIXER_ITEM =
            new BlockItem(POTION_MIXER, new Item.Settings());

    public static BlockEntityType<PotionMixerBlockEntity> POTION_MIXER_BE;

    // Recipe plumbing
    public static final RecipeType<PotionMixingRecipe> POTION_MIXING_TYPE =
            Registry.register(Registries.RECIPE_TYPE, id("potion_mixing"), new RecipeType<>(){ public String toString(){ return "odd:potion_mixing"; }});

    public static final RecipeSerializer<PotionMixingRecipe> POTION_MIXING_SERIALIZER =
            Registry.register(Registries.RECIPE_SERIALIZER, id("potion_mixing"), new PotionMixingRecipeSerializer());

    // Brew items (basic now; extend later)
    public static final Item BREW_DRINKABLE =
            Registry.register(Registries.ITEM, id("brew_drinkable"), new ArtificerBrewItem(new Item.Settings().maxCount(16), ArtificerBrewItem.Kind.DRINK));

    public static final Item BREW_THROWABLE =
            Registry.register(Registries.ITEM, id("brew_throwable"), new ArtificerBrewItem(new Item.Settings().maxCount(16), ArtificerBrewItem.Kind.THROW));

    public static void registerAll() {
        Registry.register(Registries.BLOCK, id("potion_mixer"), POTION_MIXER);
        Registry.register(Registries.ITEM,  id("potion_mixer"), POTION_MIXER_ITEM);

        POTION_MIXER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE, id("potion_mixer"),
                FabricBlockEntityTypeBuilder.create(PotionMixerBlockEntity::new, POTION_MIXER).build()
        );

        // Fabric fluids I/O exposure (Create pipes talk to this)
        FluidStorage.SIDED.registerForBlockEntity(
                (PotionMixerBlockEntity be, Direction dir) -> be.getFluidStorage(),
                POTION_MIXER_BE
        );
    }

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        // (no GUI yet)
    }

    private static Identifier id(String p) { return new Identifier("odd", p); }
}
