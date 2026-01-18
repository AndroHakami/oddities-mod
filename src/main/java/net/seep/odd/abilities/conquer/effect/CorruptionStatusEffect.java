package net.seep.odd.abilities.conquer.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;
import net.seep.odd.abilities.conquer.CorruptionConversion;

public final class CorruptionStatusEffect extends StatusEffect {
    public CorruptionStatusEffect() {
        super(StatusEffectCategory.NEUTRAL, 0x0B0B0B);
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);

        if (entity.getWorld() instanceof ServerWorld sw) {
            // schedule to keep it safe during effect add callstack
            sw.getServer().execute(() -> CorruptionConversion.ensureCorrupted(entity));
        }
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);

        if (entity.getWorld() instanceof ServerWorld sw) {
            sw.getServer().execute(() -> CorruptionConversion.ensureNormal(entity));
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }
}
