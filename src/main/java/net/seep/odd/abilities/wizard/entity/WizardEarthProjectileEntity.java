// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/WizardEarthProjectileEntity.java
package net.seep.odd.abilities.wizard.entity;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.mixin.BlockDisplayEntityInvoker;

public class WizardEarthProjectileEntity extends ThrownItemEntity {

    public static float DAMAGE = 6.0f;       // 3 hearts
    public static double KNOCKBACK = 1.6;

    private static final Vec3d[] BOULDER_OFFSETS = new Vec3d[] {
            new Vec3d(0.00, 0.00, 0.00),
            new Vec3d(0.35, 0.10, 0.00),
            new Vec3d(-0.30, 0.15, 0.20),
            new Vec3d(0.10, 0.25, -0.35),
            new Vec3d(-0.15, -0.05, -0.25),
            new Vec3d(0.25, -0.10, 0.30)
    };

    private DisplayEntity.BlockDisplayEntity[] boulderPieces;
    private boolean spawned = false;

    public WizardEarthProjectileEntity(EntityType<? extends WizardEarthProjectileEntity> type, World world) {
        super(type, world);
    }

    public WizardEarthProjectileEntity(EntityType<? extends WizardEarthProjectileEntity> type, LivingEntity owner, World world) {
        super(type, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        // doesn't matter if you render this entity as no-render
        return Items.STONE;
    }

    @Override
    protected float getGravity() {
        return 0.06f; // heavier arc than normal thrown item
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld() instanceof ServerWorld sw) {
            // spawn the boulder blocks once
            if (!spawned) {
                spawned = true;
                spawnBoulder(sw);
            }

            // follow projectile
            tickBoulderFollow();

            // some chunky particles
            if ((this.age % 2) == 0) {
                sw.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
                        this.getX(), this.getY(), this.getZ(),
                        3,
                        0.08, 0.08, 0.08,
                        0.0
                );
            }

            // safety lifetime
            if (this.age > 80) {
                cleanupBoulder();
                this.discard();
            }
        }
    }

    private void spawnBoulder(ServerWorld sw) {
        boulderPieces = new DisplayEntity.BlockDisplayEntity[BOULDER_OFFSETS.length];

        for (int i = 0; i < BOULDER_OFFSETS.length; i++) {
            DisplayEntity.BlockDisplayEntity bd = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, sw);

            // pick a rocky palette
            var state = switch (sw.random.nextInt(3)) {
                case 0 -> Blocks.COBBLESTONE.getDefaultState();
                case 1 -> Blocks.ANDESITE.getDefaultState();
                default -> Blocks.STONE.getDefaultState();
            };

            ((BlockDisplayEntityInvoker)(Object)bd).odd$setBlockState(state);

            bd.setNoGravity(true);
            bd.setInvulnerable(true);
            bd.setSilent(true);

            Vec3d off = BOULDER_OFFSETS[i];
            bd.refreshPositionAndAngles(
                    this.getX() + off.x,
                    this.getY() + off.y,
                    this.getZ() + off.z,
                    this.getYaw(),
                    this.getPitch()
            );

            sw.spawnEntity(bd);
            boulderPieces[i] = bd;
        }
    }

    private void tickBoulderFollow() {
        if (boulderPieces == null) return;

        for (int i = 0; i < boulderPieces.length; i++) {
            DisplayEntity.BlockDisplayEntity bd = boulderPieces[i];
            if (bd == null || !bd.isAlive()) continue;

            Vec3d off = BOULDER_OFFSETS[i];

            bd.refreshPositionAndAngles(
                    this.getX() + off.x,
                    this.getY() + off.y,
                    this.getZ() + off.z,
                    this.getYaw(),
                    this.getPitch()
            );
        }
    }

    private void cleanupBoulder() {
        if (boulderPieces == null) return;
        for (var bd : boulderPieces) {
            if (bd != null && bd.isAlive()) bd.discard();
        }
        boulderPieces = null;
    }

    @Override
    protected void onEntityHit(EntityHitResult ehr) {
        super.onEntityHit(ehr);

        Entity hit = ehr.getEntity();
        Entity owner = this.getOwner();

        DamageSource src = this.getWorld().getDamageSources().thrown(this, owner instanceof LivingEntity le ? le : null);

        if (hit instanceof LivingEntity living) {
            living.damage(src, DAMAGE);

            Vec3d dir = this.getVelocity();
            if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(0, 0, 1);
            dir = dir.normalize();

            living.addVelocity(dir.x * KNOCKBACK, 0.18, dir.z * KNOCKBACK);
            living.velocityModified = true;
        }

        if (!this.getWorld().isClient) {
            cleanupBoulder();
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) {
            cleanupBoulder();
            this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        cleanupBoulder();
        super.remove(reason);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
