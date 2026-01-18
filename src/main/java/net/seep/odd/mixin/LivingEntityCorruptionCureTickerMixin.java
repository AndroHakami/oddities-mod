package net.seep.odd.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.seep.odd.abilities.conquer.CorruptionCureHolder;
import net.seep.odd.status.ModStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCorruptionCureTickerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void odd$corruptionCureTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof CorruptionCureHolder holder)) return;

        int ticks = holder.odd$getCorruptionCureTicks();
        if (ticks <= 0) return;

        // If corruption got removed by anything else, stop curing
        if (!self.hasStatusEffect(ModStatusEffects.CORRUPTION)) {
            holder.odd$setCorruptionCureTicks(0);
            return;
        }

        if (self.getWorld() instanceof ServerWorld sw) {
            if ((ticks % 10) == 0) {
                // Safe particles
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        self.getX(), self.getBodyY(0.6), self.getZ(),
                        6, 0.35, 0.35, 0.35, 0.01);
                sw.spawnParticles(ParticleTypes.END_ROD,
                        self.getX(), self.getBodyY(0.6), self.getZ(),
                        6, 0.35, 0.35, 0.35, 0.01);
            }
        }

        ticks--;
        holder.odd$setCorruptionCureTicks(ticks);

        if (ticks == 0) {
            self.removeStatusEffect(ModStatusEffects.CORRUPTION);
            if (self.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, self.getBlockPos(),
                        SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED,
                        SoundCategory.NEUTRAL, 0.9f, 1.2f);
            }
        }
    }
}
