package net.seep.odd.abilities.firesword.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.seep.odd.item.ModItems;

public class FireSwordProjectileEntity extends ThrownItemEntity {

    private int ownerNoClipTicks = 5;

    private static final ExplosionBehavior NO_BLOCK_DAMAGE_BEHAVIOR = new ExplosionBehavior() {
        @Override
        public boolean canDestroyBlock(Explosion explosion, BlockView world, BlockPos pos, BlockState state, float power) {
            return false;
        }
    };

    public FireSwordProjectileEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public FireSwordProjectileEntity(EntityType<? extends ThrownItemEntity> type, World world, LivingEntity owner) {
        super(type, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.FIRE_SWORD;
    }

    @Override
    public void tick() {
        super.tick();
        if (ownerNoClipTicks > 0) ownerNoClipTicks--;

        // flying trail (client visual)
        if (getWorld().isClient) {
            Vec3d v = getVelocity();
            double bx = getX() - v.x * 0.25;
            double by = getY() - v.y * 0.25;
            double bz = getZ() - v.z * 0.25;

            getWorld().addParticle(ParticleTypes.FLAME, bx, by, bz, 0.0, 0.0, 0.0);
            if ((age & 1) == 0) getWorld().addParticle(ParticleTypes.SMOKE, bx, by, bz, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        if (ownerNoClipTicks > 0 && entity == getOwner()) return false;
        return super.canHit(entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        entityHitResult.getEntity().setOnFireFor(6);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);

        World world = getWorld();
        if (world.isClient) return;

        // ✅ Particle burst so the explosion looks "flamey"
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 60, 0.6, 0.4, 0.6, 0.02);
            sw.spawnParticles(ParticleTypes.LAVA,  getX(), getY(), getZ(), 20, 0.4, 0.2, 0.4, 0.02);
            sw.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 25, 0.6, 0.4, 0.6, 0.01);
        }

        DamageSource dmg = world.getDamageSources().explosion(this, getOwner());

        // ✅ createFire=true means it WILL place fire
        world.createExplosion(
                this,
                dmg,
                NO_BLOCK_DAMAGE_BEHAVIOR,
                getX(), getY(), getZ(),
                2.6f,
                true, // create fire
                World.ExplosionSourceType.MOB
        );

        discard();
    }

    @Override
    public ItemStack getStack() {
        return new ItemStack(ModItems.FIRE_SWORD);
    }
}
