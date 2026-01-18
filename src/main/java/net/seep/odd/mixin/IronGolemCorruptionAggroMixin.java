// src/main/java/net/seep/odd/mixin/IronGolemCorruptionAggroMixin.java
package net.seep.odd.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.conquer.entity.DarkHorseEntity;
import net.seep.odd.abilities.power.ConquerPower;
import net.seep.odd.mixin.access.MobEntityTargetSelectorAccessor;
import net.seep.odd.status.ModStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IronGolemEntity.class)
public abstract class IronGolemCorruptionAggroMixin {

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void odd$addCorruptionAggro(CallbackInfo ci) {
        final IronGolemEntity self = (IronGolemEntity)(Object)this;

        // Access the *real* MobEntity.targetSelector safely
        GoalSelector selector = ((MobEntityTargetSelectorAccessor)(Object) self).odd$getTargetSelector();
        if (selector == null) return;

        // Priority 1: very aggressive targeting, but only while corrupted (predicate below)
        selector.add(1, new ActiveTargetGoal<>(
                self,
                LivingEntity.class,
                1,      // reciprocalChance (1 = checks constantly)
                true,   // checkVisibility
                false,  // checkCanNavigate
                target -> shouldCorruptedGolemTarget(self, target)
        ));
    }

    private static boolean shouldCorruptedGolemTarget(IronGolemEntity self, LivingEntity target) {
        // Only berserk if corrupted
        if (!self.hasStatusEffect(ModStatusEffects.CORRUPTION)) return false;

        if (target == null || target == self) return false;
        if (!target.isAlive()) return false;

        // Don't hit Milo / Dark Horse
        if (target instanceof DarkHorseEntity) return false;

        // Don't hit the Conquer player
        if (target instanceof PlayerEntity player) {
            if (player.isSpectator()) return false;
            if (ConquerPower.hasConquer(player)) return false;
        }

        // Don't hit corrupted villagers
        if (target instanceof VillagerEntity villager) {
            if (villager.hasStatusEffect(ModStatusEffects.CORRUPTION)) return false;
        }

        // Don't hit other corrupted golems (prevents your 4x spawn from self-destructing)
        if (target instanceof IronGolemEntity golem) {
            if (golem.hasStatusEffect(ModStatusEffects.CORRUPTION)) return false;
        }

        // Otherwise: attack EVERYTHING
        return true;
    }
}
