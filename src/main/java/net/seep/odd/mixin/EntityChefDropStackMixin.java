package net.seep.odd.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.seep.odd.abilities.chef.Chef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityChefDropStackMixin {
    @Inject(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;", at = @At("RETURN"))
    private void odd$chef_duplicate(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
        LivingEntity victim = Chef.LootTL.CURRENT.get();
        if (victim == null) return;

        Entity self = (Entity)(Object)this;
        if (self != victim) return;

        if (!Chef.LootTL.DOUBLE.get()) return;
        if (Chef.LootTL.GUARD.get()) return;

        ItemEntity original = cir.getReturnValue();
        if (original == null) return;
        if (stack == null || stack.isEmpty()) return;

        World w = self.getWorld();
        if (w.isClient) return;

        Chef.LootTL.GUARD.set(true);
        try {
            ItemStack extraStack = stack.copy();
            ItemEntity extra = new ItemEntity(w, original.getX(), original.getY(), original.getZ(), extraStack);
            extra.setPickupDelay(original.pickupDelay);
            extra.setVelocity(original.getVelocity());
            w.spawnEntity(extra);
        } finally {
            Chef.LootTL.GUARD.set(false);
        }
    }
}
