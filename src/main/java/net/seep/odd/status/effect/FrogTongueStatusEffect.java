// src/main/java/net/seep/odd/status/effect/FrogTongueStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

import java.util.UUID;

public final class FrogTongueStatusEffect extends StatusEffect {

    private static final UUID DMG_UUID = UUID.fromString("2b40b4d8-93a2-4a50-a10d-c6cf6d3e9b5a");
    private static final UUID KB_UUID  = UUID.fromString("7c8bff52-6f78-470d-9f61-7d267f1a8b58");

    public FrogTongueStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x42F5F5);

        // “Sharpness 2 vibe” + knockback
        this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, DMG_UUID.toString(),
                2.0D, EntityAttributeModifier.Operation.ADDITION);

        this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, KB_UUID.toString(),
                0.6D, EntityAttributeModifier.Operation.ADDITION);
    }
}
