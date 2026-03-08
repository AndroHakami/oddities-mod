// src/main/java/net/seep/odd/mixin/SuperChargeExplosionDamageMixin.java
package net.seep.odd.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.explosion.Explosion;
import net.seep.odd.entity.supercharge.SuperThrownItemEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Explosion.class)
public abstract class SuperChargeExplosionDamageMixin {

    @Shadow @Final private Entity entity; // source entity that caused the explosion

    @ModifyArg(
            method = "collectBlocksAndDamageEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
            ),
            index = 1
    )
    private float odd$scaleSuperchargeExplosionDamage(float amount) {
        if (!(this.entity instanceof SuperThrownItemEntity st)) return amount;

        ItemStack s = st.getStack();
        boolean isTnt = (s != null && !s.isEmpty() && s.isOf(Items.TNT));

        // ✅ -60% normal => 0.40
        // ✅ -50% TNT    => 0.50
        float mult = isTnt ? 0.50f : 0.40f;

        return amount * mult;
    }
}