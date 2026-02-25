// src/main/java/net/seep/odd/status/effect/CreeperKebabChargeStatusEffect.java
package net.seep.odd.status.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class CreeperKebabChargeStatusEffect extends StatusEffect {
    public CreeperKebabChargeStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x3AFF3A);
    }

    @Override
    public void onRemoved(LivingEntity eater, AttributeContainer attributes, int amplifier) {
        super.onRemoved(eater, attributes, amplifier);

        if (!(eater.getWorld() instanceof ServerWorld sw)) return;

        Vec3d c = eater.getPos().add(0, 0.6, 0);

        // visual + sound “boom” (NO block damage)
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0.0);
        sw.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 120, 1.4, 0.6, 1.4, 0.06);
        sw.spawnParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 80, 1.3, 0.5, 1.3, 0.04);
        sw.playSound(null, eater.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        double radius = 5.5;
        Box box = new Box(c, c).expand(radius);

        for (Entity e : sw.getOtherEntities(eater, box, ent -> ent instanceof LivingEntity le && le.isAlive() && ent != eater)) {
            LivingEntity le = (LivingEntity) e;

            // strong but not insane
            le.damage(sw.getDamageSources().magic(), 10.0f);

            Vec3d away = le.getPos().subtract(eater.getPos());
            if (away.lengthSquared() < 1.0E-6) away = new Vec3d(0, 0, 0);
            else away = away.normalize();

            le.addVelocity(away.x * 1.2, 0.35, away.z * 1.2);
            le.velocityModified = true;
        }

        // eater immune by design (we skip them)
    }
}