// src/main/java/net/seep/odd/mixin/MilkBucketItemMixin.java
package net.seep.odd.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.world.World;
import net.seep.odd.status.ModStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(MilkBucketItem.class)
public abstract class MilkBucketItemMixin {
    @Unique private static final Map<LivingEntity, StatusEffectInstance> odd$stored = new WeakHashMap<>();

    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void odd$store(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClient) return;

        StatusEffectInstance inst = user.getStatusEffect(ModStatusEffects.POWERLESS);
        if (inst == null) return;

        odd$stored.put(user, new StatusEffectInstance(
                inst.getEffectType(),
                inst.getDuration(),
                inst.getAmplifier(),
                inst.isAmbient(),
                inst.shouldShowParticles(),
                inst.shouldShowIcon()
        ));
    }

    @Inject(method = "finishUsing", at = @At("TAIL"))
    private void odd$restore(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClient) return;

        StatusEffectInstance stored = odd$stored.remove(user);
        if (stored != null && !user.hasStatusEffect(ModStatusEffects.POWERLESS)) {
            user.addStatusEffect(stored);
        }
    }
}
