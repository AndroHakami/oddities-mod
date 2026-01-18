package net.seep.odd.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import net.seep.odd.abilities.conquer.item.WinterScytheItem;
import net.seep.odd.abilities.firesword.item.FireSwordItem;
import net.seep.odd.abilities.firesword.item.FireSwordToolMaterial;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import net.seep.odd.abilities.tamer.item.TameBallItem;
import net.seep.odd.entity.seal.item.SealSpawnEggItem;
import net.seep.odd.item.custom.CosmicKatanaItem;
import net.seep.odd.item.custom.MetalDetectorItem;
import net.seep.odd.item.ghost.GhostHandItem;

public class ModItems {
    public static final Item RUBY = registerItem("ruby", new Item(new FabricItemSettings()));
    public static final Item RAW_RUBY = registerItem("raw_ruby", new Item(new FabricItemSettings()));
    public static final Item TOMATO = registerItem("tomato", new Item(new FabricItemSettings().food(ModFoodComponents.TOMATO)));
    public static final Item COAL_BRIQUETTE = registerItem("coal_briquette", new Item(new FabricItemSettings()));
    public static final Item METAL_DETECTOR = registerItem("metal_detector", new MetalDetectorItem(new FabricItemSettings().maxDamage(64)));
    public static final Item RUBY_STAFF = registerItem("ruby_staff", new Item(new FabricItemSettings().maxCount(1)));
    public static final Item TAME_BALL = registerItem("tame_ball", new TameBallItem(new FabricItemSettings().maxCount(16)));
    public static final Item GHOST_HAND = registerItem("ghost_hand", new GhostHandItem(new FabricItemSettings().maxCount(1)));
    public static final Item EMERALD_SHURIKEN = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "emerald_shuriken"), new Item(new Item.Settings()));
    public static final Item ICE_PROJECTILE = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "ice_projectile"), new Item(new Item.Settings()));
    public static final Item VACUUM = registerItem("vacuum", new ArtificerVacuumItem(new FabricItemSettings().maxCount(1)));
    public static final Item GAMBLE_REVOLVER = registerItem("gamble_revolver", new GambleRevolverItem(new FabricItemSettings().maxCount(1)));
    public static final Item ALIEN_PEARL = registerItem("alien_pearl", new Item(new FabricItemSettings().food(ModFoodComponents.ALIEN_PEARL)));
    public static final Item COSMIC_KATANA = registerItem("cosmic_katana", new CosmicKatanaItem(new FabricItemSettings().maxCount(1).maxDamage(1561)));
    public static final Item LUNAR_DRILL =  Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "lunar_drill"), new LunarDrillItem(new Item.Settings().maxCount(1)));
    public static final Item FIRE_SWORD = Registry.register(Registries.ITEM, new Identifier("odd", "fire_sword"), new FireSwordItem(FireSwordToolMaterial.INSTANCE, 6, -2.2f, new FabricItemSettings().maxCount(1)));
    public static final Item WINTER_SCYTHE = registerItem("winter_scythe", new WinterScytheItem(new FabricItemSettings().maxCount(1)));
    public static final Item METALIC_FROST_SPAWN = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "metallic_frost_spawn"), new net.seep.odd.abilities.conquer.item.MetalicFrostSpawnItem(new Item.Settings().maxCount(1)));
    public static final Item BIG_METALIC_FROST_SPAWN = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "big_metallic_frost_spawn"), new net.seep.odd.abilities.conquer.item.BigMetalicFrostSpawnItem(new Item.Settings().maxCount(1)));
    public static final Item DABLOON = registerItem("dabloon", new Item(new FabricItemSettings().maxCount(64)));
    public static final Item SEAL_SPAWN_EGG = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "seal_spawn_egg"), new SealSpawnEggItem(new Item.Settings().maxCount(1)));





    private static void addItemsToIngredientItemGroup(FabricItemGroupEntries entries) {
        entries.add(RUBY);
        entries.add(RAW_RUBY);
    }



    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, name), item);
    }

    public static void registerModItems() {
        Oddities.LOGGER.info("Registering Mod Items FOR " + Oddities.MOD_ID);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(ModItems::addItemsToIngredientItemGroup);
    }
}
