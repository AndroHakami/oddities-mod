package net.seep.odd.abilities.artificer.mixer.projectile;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.seep.odd.abilities.artificer.mixer.ArtificerBrewItem;
import net.seep.odd.abilities.artificer.mixer.brew.BrewEffects;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;

public class BrewBottleEntity extends ThrownItemEntity {

    public BrewBottleEntity(EntityType<? extends BrewBottleEntity> type, World world) {
        super(type, world);
    }

    public BrewBottleEntity(World world, LivingEntity owner, ItemStack payload) {
        super(net.seep.odd.entity.ModEntities.BREW_BOTTLE, owner, world);
        // carry the actual brew stack + NBT (odd_brew_id, odd_brew_color, etc.)
        this.setItem(payload.copyWithCount(1));
    }

    @Override
    protected Item getDefaultItem() {
        // purely for renderer particles; the actual stack (with NBT) is set above
        return ArtificerMixerRegistry.BREW_THROWABLE;
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!this.getWorld().isClient) {
            ItemStack payload = this.getItem(); // contains odd_brew_id
            String brewId = payload.getOrCreateNbt().getString("odd_brew_id");

            LivingEntity thrower = (this.getOwner() instanceof LivingEntity le) ? le : null;
            BrewEffects.applyThrowable(this.getWorld(), this.getBlockPos(), thrower, brewId, payload);

            this.discard();
        }
    }
}
