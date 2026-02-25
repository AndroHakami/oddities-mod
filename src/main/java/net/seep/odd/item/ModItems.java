// src/main/java/net/seep/odd/item/ModItems.java
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
import net.seep.odd.abilities.chef.ChefFoodHooks;
import net.seep.odd.abilities.chef.item.CrappyBurgerItem;
import net.seep.odd.abilities.firesword.item.FireSwordItem;
import net.seep.odd.abilities.firesword.item.FireSwordToolMaterial;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import net.seep.odd.abilities.sniper.item.SniperItem;
import net.seep.odd.abilities.tamer.item.TameBallItem;
import net.seep.odd.entity.booklet.item.BookletSpawnEggItem;
import net.seep.odd.entity.seal.item.SealSpawnEggItem;
import net.seep.odd.item.custom.CosmicKatanaItem;
import net.seep.odd.item.custom.MetalDetectorItem;
import net.seep.odd.item.custom.TooltipItem;
import net.seep.odd.item.ghost.GhostHandItem;
import net.seep.odd.item.necromancer.NecromancerStaffItem;
import net.seep.odd.item.wizard.WalkingStickItem;

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

    public static final Item LUNAR_DRILL = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "lunar_drill"), new LunarDrillItem(new Item.Settings().maxCount(1)));
    public static final Item FIRE_SWORD = Registry.register(Registries.ITEM, new Identifier("odd", "fire_sword"),
            new FireSwordItem(FireSwordToolMaterial.INSTANCE, 6, -2.2f, new FabricItemSettings().maxCount(1).fireproof()));

    public static final Item WINTER_SCYTHE = registerItem("winter_scythe", new WinterScytheItem(new FabricItemSettings().maxCount(1)));

    public static final Item METALIC_FROST_SPAWN = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "metallic_frost_spawn"),
            new net.seep.odd.abilities.conquer.item.MetalicFrostSpawnItem(new Item.Settings().maxCount(1)));
    public static final Item BIG_METALIC_FROST_SPAWN = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "big_metallic_frost_spawn"),
            new net.seep.odd.abilities.conquer.item.BigMetalicFrostSpawnItem(new Item.Settings().maxCount(1)));

    public static final Item DABLOON = registerItem("dabloon", new Item(new FabricItemSettings().maxCount(64)));
    public static final Item SEAL_SPAWN_EGG = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "seal_spawn_egg"),
            new SealSpawnEggItem(new Item.Settings().maxCount(1)));
    public static final Item NECROMANCER_STAFF = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "necromancer_staff"),
            new NecromancerStaffItem(new Item.Settings().maxCount(1)));
    public static final Item NECRO_BOLT = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "necro_bolt"), new Item(new Item.Settings()));
    public static final Item BLOOD_CRYSTAL_PROJECTILE = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "blood_crystal_projectile"),
            new Item(new Item.Settings().maxCount(1)));
    public static final Item SNIPER = Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, "sniper"),
            new SniperItem(new Item.Settings().maxCount(1)));
    public static final Item BOOKLET_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(Oddities.MOD_ID, "booklet_spawn_egg"),
            new BookletSpawnEggItem(new FabricItemSettings().maxCount(1))
    );
    public static final Item WALKING_STICK = registerItem("walking_stick", new WalkingStickItem(new FabricItemSettings().maxCount(1)));
    public static final Item WIZARD_FIRE_PROJECTILE_ITEM = registerItem("wizard_fire_projectile", new Item(new FabricItemSettings()));
    public static final Item WIZARD_WATER_PROJECTILE_ITEM = registerItem("wizard_water_projectile", new Item(new FabricItemSettings()));
    public static final Item WIZARD_EARTH_PROJECTILE_ITEM = registerItem("wizard_earth_projectile", new Item(new FabricItemSettings()));

    /* =========================
       ✅ CHEF FOODS (from your PNGs)
       ========================= */

    public static final Item MUSH = registerItem("mush",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.MUSH)));

    // ✅ Crappy burger now TELEPORTS forward on eat (8 blocks)
    public static final Item CRAPPY_BURGER = registerItem("crappy_burger",
            new CrappyBurgerItem(new FabricItemSettings().food(ModFoodComponents.CRAPPY_BURGER)));

    public static final Item AMETHYST_KEBAB = registerItem("amethyst_kebab",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.AMETHYST_KEBAB)));
    public static final Item CALAMARI = registerItem("calamari",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.CALAMARI)));
    public static final Item CHICKEN_BALLS = registerItem("chicken_balls",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.CHICKEN_BALLS)));
    public static final Item CREEPER_KEBAB = registerItem("creeper_kebab",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.CREEPER_KEBAB)));
    public static final Item DEEPDARK_FRIES = registerItem("deepdark_fries",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.DEEPDARK_FRIES)));
    public static final Item DRAGON_BURRITO = registerItem("dragon_burrito",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.DRAGON_BURRITO)));
    public static final Item EGG_SANDWICH = registerItem("egg_sandwich",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.EGG_SANDWICH)));
    public static final Item EMERALD_PIE = registerItem("emerald_pie",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.EMERALD_PIE)));
    public static final Item FRIES = registerItem("fries",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.FRIES)));
    public static final Item GHAST_FRIES = registerItem("ghast_fries",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.GHAST_FRIES)));
    public static final Item HELLISH_BURGER = registerItem("hellish_burger",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.HELLISH_BURGER)));
    public static final Item MAGMA_ICECREAM = registerItem("magma_icecream",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.MAGMA_ICECREAM)));
    public static final Item MASOUB = registerItem("masoub",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.MASOUB)));
    public static final Item MINER_BERRIES = registerItem("miner_berries",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.MINER_BERRIES)));
    public static final Item OUTER_ICECREAM = registerItem("outer_icecream",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.OUTER_ICECREAM)));
    public static final Item PUFFER_SUSHI = registerItem("puffer_sushi",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.PUFFER_SUSHI)));
    public static final Item RADICAL_BURGER = registerItem("radical_burger",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.RADICAL_BURGER)));
    public static final Item RAMEN = registerItem("ramen",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.RAMEN)));
    public static final Item SHULKER_ICECREAM = registerItem("shulker_icecream",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.SHULKER_ICECREAM)));
    public static final Item SQUID_SQUASH = registerItem("squid_squash",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.SQUID_SQUASH)));
    public static final Item SUSHI = registerItem("sushi",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.SUSHI)));
    public static final Item YANBU_ICECREAM = registerItem("yanbu_icecream",
            new TooltipItem(new FabricItemSettings().food(ModFoodComponents.YANBU_ICECREAM)));

    /* ========================= */

    private static void addItemsToIngredientItemGroup(FabricItemGroupEntries entries) {
        entries.add(RUBY);
        entries.add(RAW_RUBY);

        // ✅ CHEF foods in creative for testing
        entries.add(MUSH);
        entries.add(CRAPPY_BURGER);
        entries.add(AMETHYST_KEBAB);
        entries.add(CALAMARI);
        entries.add(CHICKEN_BALLS);
        entries.add(CREEPER_KEBAB);
        entries.add(DEEPDARK_FRIES);
        entries.add(DRAGON_BURRITO);
        entries.add(EGG_SANDWICH);
        entries.add(EMERALD_PIE);
        entries.add(FRIES);
        entries.add(GHAST_FRIES);
        entries.add(HELLISH_BURGER);
        entries.add(MAGMA_ICECREAM);
        entries.add(MASOUB);
        entries.add(MINER_BERRIES);
        entries.add(OUTER_ICECREAM);
        entries.add(PUFFER_SUSHI);
        entries.add(RADICAL_BURGER);
        entries.add(RAMEN);
        entries.add(SHULKER_ICECREAM);
        entries.add(SQUID_SQUASH);
        entries.add(SUSHI);
        entries.add(YANBU_ICECREAM);
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Oddities.MOD_ID, name), item);
    }

    public static void registerModItems() {
        Oddities.LOGGER.info("Registering Mod Items FOR " + Oddities.MOD_ID);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(ModItems::addItemsToIngredientItemGroup);

        // ✅ registers Chef food special logic (sonic swings, levitation-on-hit, triple jump net)
        ChefFoodHooks.init();
    }
}