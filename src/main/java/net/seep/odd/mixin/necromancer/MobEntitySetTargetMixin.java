// src/main/java/net/seep/odd/mixin/necro/MobEntitySetTargetMixin.java
package net.seep.odd.mixin.necromancer;

import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.seep.odd.abilities.power.NecromancerPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntitySetTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void odd$blockTargetingNecromancerOnly(LivingEntity newTarget, CallbackInfo ci) {
        if (newTarget == null) return;

        MobEntity mob = (MobEntity)(Object)this;

        // only undead mobs
        if (mob.getGroup() != EntityGroup.UNDEAD) return;

        // only if the NEW target is a necromancer player
        if (!(newTarget instanceof ServerPlayerEntity sp)) return;
        if (!NecromancerPower.isNecromancer(sp)) return;

        if (!(mob.getWorld() instanceof ServerWorld sw)) return;
        long now = sw.getTime();

        // true = ignore necromancer (unless grace window says otherwise)
        if (NecromancerPower.shouldUndeadIgnore(sp, mob.getUuid(), now)) {
            ci.cancel(); // prevent setting necromancer as target
        }
    }
}
