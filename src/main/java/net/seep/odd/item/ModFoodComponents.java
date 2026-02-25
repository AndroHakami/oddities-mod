// src/main/java/net/seep/odd/item/ModFoodComponents.java
package net.seep.odd.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;

import net.seep.odd.status.ModStatusEffects;

public class ModFoodComponents {

    public static final FoodComponent TOMATO =
            new FoodComponent.Builder()
                    .hunger(2).saturationModifier(0.6f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 200, 77), 1.0f)
                    .build();

    public static final FoodComponent ALIEN_PEARL =
            new FoodComponent.Builder()
                    .hunger(3).saturationModifier(0.6f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 2), 0.5f)
                    .build();

    /** “Bad cook” result from the Super Cooker. */
    public static final FoodComponent MUSH =
            new FoodComponent.Builder()
                    .hunger(2).saturationModifier(0.1f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20 * 6, 0), 0.8f)
                    .build();

    /** Crappy burger: TELEPORT is handled by the item class (not a status effect). */
    public static final FoodComponent CRAPPY_BURGER =
            new FoodComponent.Builder()
                    .hunger(6).saturationModifier(0.6f).alwaysEdible()
                    .build();

    // =========================
    // ✅ CHEF FOODS
    // =========================

    // Amethyst_kebab: glowing + night vision 5 mins
    public static final FoodComponent AMETHYST_KEBAB =
            new FoodComponent.Builder()
                    .hunger(10).saturationModifier(0.9f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 5, 0), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 20 * 60 * 5, 0), 1.0f)
                    .build();

    // calamari: water breathing + dolphin grace 5 mins
    public static final FoodComponent CALAMARI =
            new FoodComponent.Builder()
                    .hunger(7).saturationModifier(0.75f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 20 * 60 * 5, 0), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 20 * 60 * 5, 0), 1.0f)
                    .build();

    // chicken_balls: nothing special
    public static final FoodComponent CHICKEN_BALLS =
            new FoodComponent.Builder()
                    .hunger(5).saturationModifier(0.6f).alwaysEdible()
                    .build();

    // creeper_kebab: after 30s -> non-destructive “boom”
    public static final FoodComponent CREEPER_KEBAB =
            new FoodComponent.Builder()
                    .hunger(9).saturationModifier(0.85f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(ModStatusEffects.CREEPER_KEBAB_CHARGE, 20 * 30, 0, true, true, true), 1.0f)
                    .build();

    // deepdark_fries: 1 min, swings emit sonic beam
    public static final FoodComponent DEEPDARK_FRIES =
            new FoodComponent.Builder()
                    .hunger(6).saturationModifier(0.7f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(ModStatusEffects.DEEPDARK_SONIC, 20 * 60, 0, true, true, true), 1.0f)
                    .build();

    // dragon_burrito: 5 mins Strength 2 + no-fall + triple jump (handled by custom effect + net)
    public static final FoodComponent DRAGON_BURRITO =
            new FoodComponent.Builder()
                    .hunger(12).saturationModifier(1.0f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60 * 5, 1, true, true, true), 1.0f)
                    .statusEffect(new StatusEffectInstance(ModStatusEffects.DRAGON_BURRITO, 20 * 60 * 5, 0, true, true, true), 1.0f)
                    .build();

    // egg_sandwich: nothing special
    public static final FoodComponent EGG_SANDWICH =
            new FoodComponent.Builder()
                    .hunger(6).saturationModifier(0.75f).alwaysEdible()
                    .build();

    // emerald_pie: luck 30 mins
    public static final FoodComponent EMERALD_PIE =
            new FoodComponent.Builder()
                    .hunger(8).saturationModifier(0.8f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.LUCK, 20 * 60 * 30, 0, true, true, true), 1.0f)
                    .build();

    // fries: speed 5 1 min, then slowness 1 for 2 mins after
    public static final FoodComponent FRIES =
            new FoodComponent.Builder()
                    .hunger(6).saturationModifier(0.7f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60, 4, true, true, true), 1.0f) // Speed V
                    .statusEffect(new StatusEffectInstance(ModStatusEffects.FRIES_TIMER, 20 * 60, 0, true, false, true), 1.0f)
                    .build();

    // ghast_fries: invisibility + speed 2 for 3 mins
    public static final FoodComponent GHAST_FRIES =
            new FoodComponent.Builder()
                    .hunger(6).saturationModifier(0.75f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 20 * 60 * 3, 0, true, true, true), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60 * 3, 1, true, true, true), 1.0f)
                    .build();

    // hellish_burger: fire resistance + resistance 1 for 10 mins
    public static final FoodComponent HELLISH_BURGER =
            new FoodComponent.Builder()
                    .hunger(11).saturationModifier(0.95f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 60 * 10, 0, true, true, true), 1.0f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 60 * 10, 0, true, true, true), 1.0f)
                    .build();

    // magma_icecream: fire resistance 10 mins
    public static final FoodComponent MAGMA_ICECREAM =
            new FoodComponent.Builder()
                    .hunger(5).saturationModifier(0.65f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 60 * 10, 0, true, true, true), 1.0f)
                    .build();

    // masoub: jump boost 3 + resistance 2 for 3 mins
    public static final FoodComponent MASOUB =
            new FoodComponent.Builder()
                    .hunger(7).saturationModifier(0.85f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20 * 60 * 3, 2, true, true, true), 1.0f) // Jump III
                    .statusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 60 * 3, 1, true, true, true), 1.0f) // Resist II
                    .build();

    // miner_berries: haste 2 for 10 mins
    public static final FoodComponent MINER_BERRIES =
            new FoodComponent.Builder()
                    .hunger(4).saturationModifier(0.35f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20 * 60 * 10, 1, true, true, true), 1.0f)
                    .build();

    // outer_icecream: 1 min, every mob you hit gets levitation 3 for 1 sec (hooked)
    public static final FoodComponent OUTER_ICECREAM =
            new FoodComponent.Builder()
                    .hunger(5).saturationModifier(0.65f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(ModStatusEffects.OUTER_ICECREAM, 20 * 60, 0, true, true, true), 1.0f)
                    .build();

    // puffer_sushi: poison 2 for 10 seconds
    public static final FoodComponent PUFFER_SUSHI =
            new FoodComponent.Builder()
                    .hunger(6).saturationModifier(0.7f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.POISON, 20 * 10, 1, true, true, true), 1.0f)
                    .build();

    // radical_burger: does nothing
    public static final FoodComponent RADICAL_BURGER =
            new FoodComponent.Builder()
                    .hunger(10).saturationModifier(0.9f).alwaysEdible()
                    .build();

    // ramen: not specified -> normal
    public static final FoodComponent RAMEN =
            new FoodComponent.Builder()
                    .hunger(8).saturationModifier(0.85f).alwaysEdible()
                    .build();

    // shulker_icecream: levitation 2 + no fall damage for 1 min
    public static final FoodComponent SHULKER_ICECREAM =
            new FoodComponent.Builder()
                    .hunger(5).saturationModifier(0.65f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20 * 1, 4, true, true, true), 1.0f)
                    .statusEffect(new StatusEffectInstance(ModStatusEffects.NO_FALL_DAMAGE, 20 * 60, 0, true, false, true), 1.0f)
                    .build();

    // squid_squash: conduit power 3 mins
    public static final FoodComponent SQUID_SQUASH =
            new FoodComponent.Builder()
                    .hunger(5).saturationModifier(0.6f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.CONDUIT_POWER, 20 * 60 * 3, 0, true, true, true), 1.0f)
                    .build();

    // sushi: nothing special
    public static final FoodComponent SUSHI =
            new FoodComponent.Builder()
                    .hunger(6).saturationModifier(0.75f).alwaysEdible()
                    .build();

    // yanbu_icecream: speed 20 for 10 seconds
    public static final FoodComponent YANBU_ICECREAM =
            new FoodComponent.Builder()
                    .hunger(5).saturationModifier(0.65f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 10, 19, true, true, true), 1.0f)
                    .build();
}