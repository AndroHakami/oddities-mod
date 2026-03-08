// FILE: src/main/java/net/seep/odd/status/effect/MistVeilStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public final class MistVeilStatusEffect extends StatusEffect {
    public MistVeilStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x2A4B7C);
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);

        // One-time cleanup: mobs already targeting you drop target immediately.
        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        Box box = entity.getBoundingBox().expand(64);
        for (HostileEntity mob : sw.getEntitiesByClass(HostileEntity.class, box, m -> m.isAlive())) {
            if (mob.getTarget() == entity) {
                mob.setTarget(null);
                mob.setAttacking(false);
            }
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}