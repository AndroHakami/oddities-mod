// FILE: src/main/java/net/seep/odd/entity/rotten_roots/SporeMushroomProjectileEntity.java
package net.seep.odd.entity.rotten_roots;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public final class SporeMushroomProjectileEntity extends ThrownItemEntity {

    private static final float DIRECT_DAMAGE = 1.0F;     // low damage
    private static final float DIRECT_KNOCK  = 1.35F;    // strong knockback
    private static final float UP_BOOST      = 0.08F;

    // Burst (“explode”) tuning (no block damage, just knockback + spores)
    private static final float BURST_RADIUS = 2.6F;
    private static final float BURST_KNOCK  = 1.15F;
    private static final float BURST_UP     = 0.10F;
    private static final float BURST_DAMAGE = 0.0F;      // keep 0 if you want NO AoE damage

    private boolean burstDone = false;
    private int removeInTicks = -1; // delay removal so client sees it at close range

    public SporeMushroomProjectileEntity(EntityType<? extends SporeMushroomProjectileEntity> type, World world) {
        super(type, world);
        this.setItem(new ItemStack(Items.RED_MUSHROOM)); // default tracked stack
    }

    public SporeMushroomProjectileEntity(EntityType<? extends SporeMushroomProjectileEntity> type, World world, LivingEntity owner) {
        super(type, owner, world);
        this.setItem(new ItemStack(Items.RED_MUSHROOM));
    }

    /** Called by the bow right before spawning the entity. */
    public void setShotStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack s = stack.copy();
        s.setCount(1);

        // IMPORTANT: tracked/synced to client -> fixes “sometimes invisible”
        this.setItem(s);
    }

    @Override
    protected net.minecraft.item.Item getDefaultItem() {
        return Items.RED_MUSHROOM;
    }

    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    public void tick() {
        super.tick();
        ProjectileUtil.setRotationFromVelocity(this, 0.2F);

        if (removeInTicks >= 0) {
            if (--removeInTicks <= 0) {
                this.discard();
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);

        Entity target = hit.getEntity();
        Entity owner = this.getOwner();

        DamageSource src = (owner != null)
                ? this.getDamageSources().thrown(this, owner)
                : this.getDamageSources().thrown(this, this);

        // tiny direct damage
        target.damage(src, DIRECT_DAMAGE);

        // strong direct knockback in flight direction
        Vec3d vel = this.getVelocity();
        if (vel.lengthSquared() > 1.0E-6) {
            Vec3d n = vel.normalize();

            if (target instanceof LivingEntity le) {
                le.takeKnockback(DIRECT_KNOCK, -n.x, -n.z);
                le.addVelocity(0.0, UP_BOOST, 0.0);
            } else {
                target.setVelocity(target.getVelocity().add(n.x * DIRECT_KNOCK, UP_BOOST, n.z * DIRECT_KNOCK));
                target.velocityModified = true;
            }
        }

        if (!this.getWorld().isClient) {
            doBurst(this.getPos());
            stickThenRemove();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);

        // Block hit also bursts
        if (!this.getWorld().isClient) {
            doBurst(this.getPos());
            stickThenRemove();
        }
    }

    private void stickThenRemove() {
        // freeze it for 2 ticks so client sees it even at point blank
        this.setVelocity(Vec3d.ZERO);
        this.setNoGravity(true);
        this.noClip = true;
        this.removeInTicks = 2;
    }

    private void doBurst(Vec3d pos) {
        if (burstDone) return;
        burstDone = true;

        World world = this.getWorld();
        Entity owner = this.getOwner();

        // spore-y pop sound (no explosion particle)
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_SPORE_BLOSSOM_BREAK, SoundCategory.PLAYERS,
                0.9f, 1.2f);

        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            // ✅ NO ParticleTypes.EXPLOSION
            sw.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, pos.x, pos.y, pos.z,
                    22, 0.35, 0.25, 0.35, 0.03);
        }

        // AoE knockback from burst location
        Box box = new Box(pos.x, pos.y, pos.z, pos.x, pos.y, pos.z).expand(BURST_RADIUS);
        List<LivingEntity> ents = world.getEntitiesByClass(LivingEntity.class, box,
                LivingEntity::isAlive);

        for (LivingEntity e : ents) {
            Vec3d d = e.getPos().subtract(pos);
            double dsq = d.lengthSquared();
            if (dsq < 1.0E-6) continue;

            double dist = Math.sqrt(dsq);
            double t = 1.0 - (dist / BURST_RADIUS);
            if (t <= 0) continue;

            Vec3d n = d.multiply(1.0 / dist);

            if (BURST_DAMAGE > 0.0f) {
                DamageSource src = (owner != null)
                        ? this.getDamageSources().thrown(this, owner)
                        : this.getDamageSources().thrown(this, this);
                e.damage(src, BURST_DAMAGE * (float) t);
            }

            float strength = (float) (BURST_KNOCK * t);
            e.takeKnockback(strength, -n.x, -n.z);
            e.addVelocity(0.0, BURST_UP * t, 0.0);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("ShotStack", this.getStack().writeNbt(new NbtCompound()));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("ShotStack")) {
            ItemStack s = ItemStack.fromNbt(nbt.getCompound("ShotStack"));
            if (s.isEmpty()) s = new ItemStack(Items.RED_MUSHROOM);
            s.setCount(1);
            this.setItem(s);
        }
    }
}