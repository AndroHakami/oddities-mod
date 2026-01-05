package net.seep.odd.abilities.firesword.item;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

public enum FireSwordToolMaterial implements ToolMaterial {
    INSTANCE;

    @Override public int getDurability() { return 6; } // super low
    @Override public float getMiningSpeedMultiplier() { return 1.0f; }
    @Override public float getAttackDamage() { return 5.0f; } // base

    @Override public int getMiningLevel() { return 0; }
    @Override public int getEnchantability() { return 10; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
}
