package net.seep.odd.abilities.umbra.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.UmbraSoulPower;
import net.seep.odd.entity.umbra.UmbraEntities;
import net.seep.odd.item.ModItems;
import net.seep.odd.sound.ModSounds;

import org.joml.Vector3f;

public class ShadowKunaiEntity extends ThrownItemEntity {

    private int maxLifeTicks = 40;
    private float damage = 2.0f;

    public ShadowKunaiEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public ShadowKunaiEntity(World world, LivingEntity owner) {
        super(UmbraEntities.SHADOW_KUNAI, owner, world);
        this.setItem(ModItems.SHADOW_KUNAI.getDefaultStack());
        this.setNoGravity(true);
    }

    public void setMaxLifeTicks(int t) { this.maxLifeTicks = Math.max(1, t); }
    public void setDamage(float d) { this.damage = Math.max(0f, d); }

    @Override
    protected Item getDefaultItem() { return ModItems.SHADOW_KUNAI; }

    @Override
    protected float getGravity() { return 0.0f; }

    @Override
    public void tick() {
        super.tick();

        // keep orientation aligned with velocity
        Vec3d v = this.getVelocity();
        if (v.lengthSquared() > 1.0e-6) {
            float yaw = (float)(MathHelper.atan2(v.x, v.z) * 57.295776f);
            float pitch = (float)(MathHelper.atan2(v.y, v.horizontalLength()) * 57.295776f);
            this.setYaw(yaw);
            this.setPitch(pitch);
        }

        // trail particles (server side so everyone sees)
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld sw) {
            DustParticleEffect dust = new DustParticleEffect(new Vector3f(0.75f, 0.10f, 0.10f), 1.1f);
            sw.spawnParticles(dust, this.getX(), this.getY(), this.getZ(), 1, 0.03,0.03,0.03, 0.0);
            sw.spawnParticles(ParticleTypes.ASH, this.getX(), this.getY(), this.getZ(), 1, 0.02,0.02,0.02, 0.0);
        }

        // lifetime
        if (!this.getWorld().isClient && this.age >= maxLifeTicks) {
            poofRedSmoke();
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (this.getWorld().isClient) return;

        if (hit.getType() == HitResult.Type.BLOCK) {
            onBlockHit((BlockHitResult) hit);
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (this.getWorld().isClient) return;

        Entity owner = this.getOwner();
        if (!(owner instanceof ServerPlayerEntity sp)) {
            burstRedDust(false);
            poofRedSmoke();
            this.discard();
            return;
        }

        ServerWorld sw = (ServerWorld) this.getWorld();

        Vec3d target = hit.getPos();
        Vec3d safe = findSafeStandPos(sw, target);
        if (safe != null) {
            teleportPlayer(sw, sp, safe);

            // ✅ TP sound (custom)
            sw.playSound(null, BlockPos.ofFloored(safe), ModSounds.SHADOW_KUNAI_TP,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
        }

        // ✅ tiny dust burst on block hit
        burstRedDust(false);

        poofRedSmoke();
        this.discard();
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (this.getWorld().isClient) return;

        Entity owner = this.getOwner();
        if (!(owner instanceof ServerPlayerEntity sp)) {
            poofRedSmoke();
            this.discard();
            return;
        }

        Entity e = hit.getEntity();
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            poofRedSmoke();
            this.discard();
            return;
        }

        ServerWorld sw = (ServerWorld) this.getWorld();

        // deal 1 heart magic damage
        le.damage(sw.getDamageSources().indirectMagic(this, sp), damage);

        boolean hitPlayer = (le instanceof ServerPlayerEntity);

        // ✅ refund 1 charge on ANY living hit
        UmbraSoulPower.refundKunaiCharge(sp);

        // ✅ tiny dust burst ONLY if enemy (not player)
        if (!hitPlayer) {
            burstRedDust(true);

            // ✅ crit sound only on enemy (not player)
            sw.playSound(null, le.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
                    SoundCategory.PLAYERS, 0.85f, 1.15f);
        }

        Vec3d hitPos = this.getPos();

        if (UmbraSoulPower.canSwapWith(le)) {
            Vec3d pPos = sp.getPos();
            Vec3d tPos = le.getPos();

            Vec3d pSafe = findSafeStandPos(sw, tPos);
            teleportPlayer(sw, sp, (pSafe != null) ? pSafe : tPos);

            if (le instanceof ServerPlayerEntity tp) {
                Vec3d tSafe = findSafeStandPos(sw, pPos);
                teleportPlayer(sw, tp, (tSafe != null) ? tSafe : pPos);
            } else {
                le.requestTeleport(pPos.x, pPos.y, pPos.z);
                le.setVelocity(Vec3d.ZERO);
                le.velocityModified = true;
            }

            // ✅ swap sound (custom) — play at BOTH ends so it feels big
            sw.playSound(null, BlockPos.ofFloored(pPos), ModSounds.SHADOW_KUNAI_SWAP,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
            sw.playSound(null, BlockPos.ofFloored(tPos), ModSounds.SHADOW_KUNAI_SWAP,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);

        } else {
            Vec3d safe = findSafeStandPos(sw, hitPos);
            if (safe != null) {
                teleportPlayer(sw, sp, safe);

                // ✅ normal TP sound (custom)
                sw.playSound(null, BlockPos.ofFloored(safe), ModSounds.SHADOW_KUNAI_TP,
                        SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        }

        poofRedSmoke();
        this.discard();
    }

    private static void teleportPlayer(ServerWorld sw, ServerPlayerEntity p, Vec3d pos) {
        p.teleport(sw, pos.x, pos.y, pos.z, p.getYaw(), p.getPitch());
        p.setVelocity(Vec3d.ZERO);
        p.velocityModified = true;
        p.fallDistance = 0;
    }

    private static Vec3d findSafeStandPos(ServerWorld sw, Vec3d target) {
        BlockPos base = BlockPos.ofFloored(target);
        for (int dy = -1; dy <= 3; dy++) {
            BlockPos p = base.up(dy);
            Box bb = EntityType.PLAYER.getDimensions().getBoxAt(new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
            if (sw.isSpaceEmpty(null, bb) && sw.isSpaceEmpty(null, bb.offset(0, 1.0, 0))) {
                return new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            }
        }
        return null;
    }

    /** Tiny “pop” of red dust (enemy/block). */
    private void burstRedDust(boolean stronger) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        DustParticleEffect redDust = new DustParticleEffect(new Vector3f(0.85f, 0.08f, 0.10f), stronger ? 1.8f : 1.35f);
        int n = stronger ? 28 : 16;

        sw.spawnParticles(redDust,
                this.getX(), this.getY() + 0.10, this.getZ(),
                n,
                0.18, 0.14, 0.18,
                0.02
        );

        sw.spawnParticles(ParticleTypes.SMOKE,
                this.getX(), this.getY() + 0.12, this.getZ(),
                stronger ? 10 : 6,
                0.16, 0.12, 0.16,
                0.01
        );
    }

    private void poofRedSmoke() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        DustParticleEffect redDust = new DustParticleEffect(new Vector3f(0.85f, 0.08f, 0.10f), 1.6f);

        sw.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.15, this.getZ(),
                18, 0.22, 0.18, 0.22, 0.01);

        sw.spawnParticles(redDust, this.getX(), this.getY() + 0.10, this.getZ(),
                26, 0.25, 0.18, 0.25, 0.01);
    }
}