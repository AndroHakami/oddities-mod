package net.seep.odd.abilities.lunar.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.seep.odd.abilities.power.LunarPower;

public class LunarMarkProjectileEntity extends ThrownItemEntity {
    private int life; // ticks

    public LunarMarkProjectileEntity(net.minecraft.entity.EntityType<? extends LunarMarkProjectileEntity> type, World world) {
        super(type, world);
    }
    public LunarMarkProjectileEntity(World world, LivingEntity owner) {
        super(net.seep.odd.entity.ModEntities.LUNAR_MARK, owner, world);
    }

    @Override protected Item getDefaultItem() { return Items.GLOWSTONE_DUST; } // simple vanilla visual

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) this.getWorld();
        // small trail
        if ((life & 1) == 0) sw.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0,0,0, 0.0);

        // hard TTL fallback: if we somehow never collided, anchor to ground below current pos
        if (++life > 200) { // ~10s
            ServerPlayerEntity owner = getOwnerAsServerPlayer();
            if (owner != null) {
                // snap to ground beneath
                var start = getPos().add(0, 1, 0);
                var end   = getPos().add(0, -12, 0);
                var down = sw.raycast(new net.minecraft.world.RaycastContext(
                        start, end, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
                BlockPos pos = (down.getType() == HitResult.Type.BLOCK)
                        ? ((BlockHitResult) down).getBlockPos().offset(((BlockHitResult) down).getSide())
                        : BlockPos.ofFloored(getPos());
                LunarPower.setAnchorPosFromProjectile(owner, pos);
            }
            this.discard();
        }
    }

    /* ===== collisions ===== */
    @Override
    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) { super.onCollision(hit); return; }

        ServerPlayerEntity owner = getOwnerAsServerPlayer();
        if (owner == null) { this.discard(); return; }

        switch (hit.getType()) {
            case ENTITY -> {
                EntityHitResult ehr = (EntityHitResult) hit;
                Entity e = ehr.getEntity();
                if (e instanceof LivingEntity le && e != owner) {
                    LunarPower.setAnchorEntityFromProjectile(owner, le);
                } else {
                    // self-hit or non-living: treat as block at current spot
                    LunarPower.setAnchorPosFromProjectile(owner, BlockPos.ofFloored(getPos()));
                }
                this.discard();
            }
            case BLOCK -> {
                BlockHitResult bhr = (BlockHitResult) hit;
                BlockPos pos = bhr.getBlockPos().offset(bhr.getSide());
                LunarPower.setAnchorPosFromProjectile(owner, pos);
                this.discard();
            }
            default -> { /* MISS – let tick/TTL handle */ }
        }

        super.onCollision(hit);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        // handled in onCollision, but call super for vanilla behavior consistency (no damage)
        super.onEntityHit(entityHitResult);
    }

    private ServerPlayerEntity getOwnerAsServerPlayer() {
        Entity o = this.getOwner();
        if (o instanceof ServerPlayerEntity sp) return sp;
        if (o instanceof PlayerEntity p && !getWorld().isClient) return (ServerPlayerEntity) p;
        return null;
    }

    /* ---- minimal persistence (not really necessary here, but safe) ---- */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("life", life);
    }
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        life = nbt.getInt("life");
    }
}
