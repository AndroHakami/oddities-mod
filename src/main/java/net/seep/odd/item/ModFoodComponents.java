package net.seep.odd.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;

public class ModFoodComponents {
    public static final FoodComponent TOMATO =
            new FoodComponent.Builder()
                    .hunger(2)
                    .saturationModifier(0.6f)
                    .alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 200, 77), 1.0f)
                    .build();

    public static final FoodComponent ALIEN_PEARL =
            new FoodComponent.Builder()
                    .hunger(3)
                    .saturationModifier(0.6f)
                    .alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 2), 0.5f)
                    .build();

    /** “Bad cook” result from the Super Cooker. */
    public static final FoodComponent MUSH =
            new FoodComponent.Builder()
                    .hunger(2)
                    .saturationModifier(0.1f)
                    .alwaysEdible()
                    // mild penalty so it feels like a fail result
                    .statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20 * 6, 0), 0.8f)
                    .build();
    public static final FoodComponent CRAPPY_BURGER =
            new FoodComponent.Builder()
                    .hunger(2)
                    .saturationModifier(0.1f)
                    .alwaysEdible()
                    // mild penalty so it feels like a fail result
                    .statusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20 * 2, 3), 1f)
                    .build();
}
