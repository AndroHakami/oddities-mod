// src/main/java/net/seep/odd/mixin/umbra/LivingEntityJumpingAccessorMixin.java
package net.seep.odd.mixin.umbra;

import net.minecraft.entity.LivingEntity;
import net.seep.odd.abilities.astral.OddLivingJumpingAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpingAccessorMixin implements OddLivingJumpingAccess {

    // Yarn 1.20.1 field name is "jumping"
    @Accessor("jumping")
    @Override
    public abstract boolean oddities$getJumping();
}
