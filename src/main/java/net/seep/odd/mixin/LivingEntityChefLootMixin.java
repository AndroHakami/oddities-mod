package net.seep.odd.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.seep.odd.abilities.chef.Chef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityChefLootMixin {
    @Inject(method = "dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V", at = @At("HEAD"))
    private void odd$chef_begin(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!causedByPlayer) return;

        boolean dbl = Chef.rollDoubleLoot(self, source.getAttacker(), self.getWorld().random);
        if (dbl) Chef.LootTL.begin(self, true);
    }

    @Inject(method = "dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V", at = @At("RETURN"))
    private void odd$chef_end(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        Chef.LootTL.end();
    }
}
