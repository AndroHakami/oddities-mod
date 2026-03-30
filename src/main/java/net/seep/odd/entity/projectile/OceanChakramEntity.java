package net.seep.odd.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.item.ModItems;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OceanChakramEntity extends ThrownItemEntity {
    private static final float DIRECT_DAMAGE = 6.0F;
    private static final float CHAIN_DAMAGE = 3.0F;
    private static final int MAX_EXTRA_HITS = 3;
    private static final double CHAIN_RANGE = 8.0D;
    private static final double HOMING_SPEED = 1.8D;
    private static final int MAX_LIFETIME = 20;

    private static final int MAX_WALL_BOUNCES = 6;
    private static final double BOUNCE_SPEED_MULTIPLIER = 0.88D;
    private static final double MIN_BOUNCE_SPEED_SQ = 0.08D;

    private final Set<UUID> hitTargets = new HashSet<>();

    private boolean returning = false;
    private boolean shouldReturnToInventory = true;
    private LivingEntity currentTarget = null;
    private int wallBounces = 0;

    public OceanChakramEntity(EntityType<? extends OceanChakramEntity> entityType, World world) {
        super(entityType, world);
        this.setNoGravity(true);
    }

    public OceanChakramEntity(World world, LivingEntity owner) {
        super(ModEntities.OCEAN_CHAKRAM, owner, world);
        this.setNoGravity(true);
    }

    public void setShouldReturnToInventory(boolean shouldReturnToInventory) {
        this.shouldReturnToInventory = shouldReturnToInventory;
    }

    public boolean isReturning() {
        return this.returning;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.OCEAN_CHAKRAM;
    }

    @Override
    public void tick() {
        if (!this.getWorld().isClient) {
            if (this.returning) {
                this.noClip = true;

                Entity owner = this.getOwner();
                if (owner instanceof PlayerEntity player && player.isAlive()) {
                    Vec3d toOwner = player.getEyePos().subtract(this.getPos());
                    double distance = toOwner.length();

                    if (distance < 1.25D) {
                        this.catchBy(player);
                        return;
                    }

                    Vec3d returnVelocity = toOwner.normalize()
                            .multiply(1.75D + Math.min(0.80D, distance * 0.15D));
                    this.setVelocity(returnVelocity);
                } else if (this.age > 200) {
                    this.dropAndDiscard();
                    return;
                }
            } else {
                this.noClip = false;

                if (this.currentTarget != null) {
                    if (!this.currentTarget.isAlive()) {
                        this.beginReturn();
                    } else {
                        Vec3d toTarget = this.currentTarget.getBoundingBox().getCenter().subtract(this.getPos());
                        if (toTarget.lengthSquared() > 0.0001D) {
                            this.setVelocity(toTarget.normalize().multiply(HOMING_SPEED));
                        }
                    }
                }

                if (this.age > MAX_LIFETIME) {
                    this.beginReturn();
                }
            }
        }

        super.tick();

        if (this.getWorld().isClient && !this.isRemoved()) {
            spawnTrailParticles();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (this.getWorld().isClient) {
            return;
        }

        Entity hit = entityHitResult.getEntity();

        if (this.returning) {
            if (hit == this.getOwner() && hit instanceof PlayerEntity player) {
                this.catchBy(player);
            }
            return;
        }

        if (!(hit instanceof LivingEntity living)) {
            this.beginReturn();
            return;
        }

        if (hit == this.getOwner()) {
            return;
        }

        if (!this.hitTargets.add(living.getUuid())) {
            return;
        }

        Entity owner = this.getOwner();
        float damage = this.hitTargets.size() == 1 ? DIRECT_DAMAGE : CHAIN_DAMAGE;
        living.damage(this.getDamageSources().thrown(this, owner), damage);

        if (this.hitTargets.size() - 1 < MAX_EXTRA_HITS) {
            LivingEntity next = this.findNextTarget();
            if (next != null) {
                this.currentTarget = next;

                Vec3d toTarget = next.getBoundingBox().getCenter().subtract(this.getPos());
                if (toTarget.lengthSquared() > 0.0001D) {
                    this.setVelocity(toTarget.normalize().multiply(HOMING_SPEED));
                }
                return;
            }
        }

        this.beginReturn();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        if (this.getWorld().isClient) {
            return;
        }

        if (this.returning) {
            return;
        }

        bounceOffWall(blockHitResult);
    }

    private void bounceOffWall(BlockHitResult hitResult) {
        Direction side = hitResult.getSide();
        Vec3d velocity = this.getVelocity();

        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;

        switch (side.getAxis()) {
            case X -> vx = -vx;
            case Y -> vy = -vy;
            case Z -> vz = -vz;
        }

        Vec3d bounced = new Vec3d(vx, vy, vz).multiply(BOUNCE_SPEED_MULTIPLIER);

        // Nudge slightly away from the wall so it doesn't get stuck re-colliding instantly
        Vec3d hitPos = hitResult.getPos();
        Vec3d normal = new Vec3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        this.setPosition(hitPos.add(normal.multiply(0.08D)));

        this.setVelocity(bounced);
        this.velocityDirty = true;
        this.wallBounces++;

        if (this.wallBounces >= MAX_WALL_BOUNCES || bounced.lengthSquared() < MIN_BOUNCE_SPEED_SQ) {
            this.beginReturn();
        }
    }

    private LivingEntity findNextTarget() {
        List<LivingEntity> candidates = this.getWorld().getEntitiesByClass(
                LivingEntity.class,
                this.getBoundingBox().expand(CHAIN_RANGE),
                entity -> entity.isAlive()
                        && entity != this.getOwner()
                        && !this.hitTargets.contains(entity.getUuid())
        );

        return candidates.stream()
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(this)))
                .orElse(null);
    }

    private void beginReturn() {
        this.returning = true;
        this.currentTarget = null;
        this.noClip = true;
    }

    private void catchBy(PlayerEntity player) {
        if (!this.getWorld().isClient) {
            if (this.shouldReturnToInventory) {
                ItemStack returned = this.getStack().copy();
                if (!player.getInventory().insertStack(returned)) {
                    player.dropItem(returned, false);
                }
            }
            this.discard();
        }
    }

    private void dropAndDiscard() {
        if (!this.getWorld().isClient) {
            this.dropStack(this.getStack().copy());
            this.discard();
        }
    }

    private void spawnTrailParticles() {
        if (this.age < 2 || this.age % 3 != 0) {
            return;
        }

        Vec3d velocity = this.getVelocity();
        double speedSq = velocity.lengthSquared();
        if (speedSq < 0.01D) {
            return;
        }

        Vec3d dir = velocity.normalize();
        Vec3d back = dir.multiply(-0.20D);

        for (int i = 0; i < 2; i++) {
            double ox = (this.random.nextDouble() - 0.5D) * 0.12D;
            double oy = (this.random.nextDouble() - 0.5D) * 0.10D;
            double oz = (this.random.nextDouble() - 0.5D) * 0.12D;

            this.getWorld().addParticle(
                    ParticleTypes.BUBBLE,
                    this.getX() + back.x + ox,
                    this.getY() + 0.05D + back.y + oy,
                    this.getZ() + back.z + oz,
                    0.0D, 0.0D, 0.0D
            );
        }

        if (this.age % 6 == 0) {
            this.getWorld().addParticle(
                    ParticleTypes.BUBBLE_POP,
                    this.getX() + back.x,
                    this.getY() + 0.05D + back.y,
                    this.getZ() + back.z,
                    0.0D, 0.0D, 0.0D
            );
        }
    }
}