package net.seep.odd.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;

public class ModFoodComponents {
    public static final FoodComponent TOMATO = new FoodComponent.Builder().hunger(2).saturationModifier(0.6f).alwaysEdible().statusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 200, 77), 1).build();
}
