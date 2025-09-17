package net.seep.odd.abilities.artificer.mixer.projectile;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
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
    protected void onCollision(net.minecraft.util.hit.HitResult hit) {
        super.onCollision(hit);
        if (!this.getWorld().isClient) {
            var world = (net.minecraft.server.world.ServerWorld) this.getWorld();
            var pos = this.getPos();
            ItemStack payload = this.getItem(); // carries odd_brew_id + odd_brew_color

            // 1) Potion break sound (vanilla splash potion)
            world.playSound(
                    null,
                    getBlockPos(),
                    net.minecraft.sound.SoundEvents.ENTITY_SPLASH_POTION_BREAK,
                    net.minecraft.sound.SoundCategory.NEUTRAL,
                    1.0f,
                    0.9f + world.getRandom().nextFloat() * 0.2f
            );

            // 2) Shattering "glass bits" using the item model
            world.spawnParticles(
                    new net.minecraft.particle.ItemStackParticleEffect(net.minecraft.particle.ParticleTypes.ITEM, payload),
                    pos.x, pos.y, pos.z,
                    16,   // count
                    0.15, 0.1, 0.15, // spread
                    0.12  // speed
            );

            // 3) Watery splash ring for extra effect
            world.spawnParticles(
                    net.minecraft.particle.ParticleTypes.SPLASH,
                    pos.x, pos.y, pos.z,
                    24,
                    0.35, 0.05, 0.35,
                    0.02
            );

            // (optional) Try vanilla colored potion splash event
            // If your mappings have WorldEvents.SPLASH_POTION, this produces the curved, colored arc particles.
            try {
                int argb = payload.getOrCreateNbt().getInt("odd_brew_color");
                int rgb  = argb & 0x00FFFFFF;
                this.getWorld().syncWorldEvent(WorldEvents.SPLASH_POTION_SPLASHED, getBlockPos(), rgb);
            } catch (Throwable ignored) { /* fine on mappings that don't have this event */ }

            // Run your gameplay effect
            String brewId = payload.getOrCreateNbt().getString("odd_brew_id");
            net.seep.odd.abilities.artificer.mixer.brew.BrewEffects.applyThrowable(
                    world, getBlockPos(),
                    (this.getOwner() instanceof net.minecraft.entity.LivingEntity le) ? le : null,
                    brewId,
                    payload
            );

            this.discard();
        }
    }
}
