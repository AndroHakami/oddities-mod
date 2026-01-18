// src/main/java/net/seep/odd/status/effect/FroggyTimeStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

import java.util.UUID;

public final class FroggyTimeStatusEffect extends StatusEffect {

    private static final UUID DMG_UUID = UUID.fromString("b40b5214-4e65-4ef9-92da-7b8c3e271a6e");
    private static final UUID KB_UUID  = UUID.fromString("d5c1a6fa-8c33-4ee4-a9c0-2e9a1d3f0d11");

    public FroggyTimeStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x7CFFB1);

        this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, DMG_UUID.toString(),
                3.5D, EntityAttributeModifier.Operation.ADDITION);

        this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, KB_UUID.toString(),
                1.0D, EntityAttributeModifier.Operation.ADDITION);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;

        // stronger combo:
        // Speed 3, Jump 3, Regen 2, Resist 2 (hidden vanilla) + stronger low gravity
        FrogStatusUtil.hiddenSpeedJump(entity, 25, 2, 2);
        FrogStatusUtil.hiddenRegenResist(entity, 25, 1, 1);
        FrogStatusUtil.lowGravity(entity, 0.80);
    }
}
