// FILE: src/main/java/net/seep/odd/abilities/sniper/entity/SniperGrappleAnchorEntity.java
package net.seep.odd.abilities.sniper.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.SniperPower;

import java.util.Optional;
import java.util.UUID;

public class SniperGrappleAnchorEntity extends Entity {

    private static final TrackedData<Optional<UUID>> OWNER =
            DataTracker.registerData(SniperGrappleAnchorEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private static final TrackedData<Float> ROPE_LEN =
            DataTracker.registerData(SniperGrappleAnchorEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private BlockPos attachedBlockPos = null;

    public SniperGrappleAnchorEntity(EntityType<? extends SniperGrappleAnchorEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(OWNER, Optional.empty());
        this.dataTracker.startTracking(ROPE_LEN, 6.0f);
    }

    public void setOwnerUuid(UUID id) { this.dataTracker.set(OWNER, Optional.ofNullable(id)); }
    public UUID getOwnerUuid() { return this.dataTracker.get(OWNER).orElse(null); }

    public void setRopeLength(float len) {
        this.dataTracker.set(ROPE_LEN, Math.max(2.2f, Math.min(48.0f, len)));
    }
    public float getRopeLength() { return this.dataTracker.get(ROPE_LEN); }

    public void setAttachedBlockPos(BlockPos pos) { this.attachedBlockPos = pos; }
    public BlockPos getAttachedBlockPos() { return attachedBlockPos; }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        UUID ownerId = getOwnerUuid();
        if (ownerId == null) { discard(); return; }

        ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) { discard(); return; }

        // if block disappeared, drop
        if (attachedBlockPos != null && sw.getBlockState(attachedBlockPos).isAir()) {
            discard();
            return;
        }

        // space cancels early
        byte in = SniperPower.getInputFlags(owner);
        boolean jump = (in & SniperPower.IN_JUMP) != 0;
        if (jump) {
            discard();
            return;
        }

        // auto-detach timeout (prevents stuck)
        if (this.age > 55) { // ~2.75s
            discard();
            return;
        }

        Vec3d anchor = this.getPos();
        Vec3d waist  = owner.getPos().add(0.0, owner.getHeight() * 0.45, 0.0);

        Vec3d r = waist.subtract(anchor);
        double d = r.length();
        if (d < 2.35) {
            discard();
            return;
        }

        Vec3d dir = r.multiply(1.0 / d); // anchor -> player

        // rope rapidly shortens (this is the “pull fast to location”)
        float L = getRopeLength();
        L -= 1.35f; // speed (tweak)
        setRopeLength(L);

        double ropeLen = getRopeLength();

        // tension constraint when taut -> pulls player inward but keeps tangential velocity (swing)
        if (d > ropeLen) {
            double error = d - ropeLen;

            Vec3d v = owner.getVelocity();

            // remove outward radial velocity (keeps swing, removes “escape”)
            double vRad = v.dotProduct(dir);
            if (vRad > 0.0) v = v.subtract(dir.multiply(vRad));

            // strong inward pull
            double pull = Math.min(2.15, 0.85 + error * 0.75);
            v = v.subtract(dir.multiply(pull));

            owner.setVelocity(v);
            owner.fallDistance = 0.0f;
            owner.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(owner));
        } else {
            owner.fallDistance = 0.0f;
        }

        // mild horizontal drag (prevents infinite speed)
        Vec3d v2 = owner.getVelocity();
        owner.setVelocity(v2.x * 0.992, v2.y, v2.z * 0.992);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) setOwnerUuid(nbt.getUuid("Owner"));
        if (nbt.contains("RopeLen")) setRopeLength(nbt.getFloat("RopeLen"));
        if (nbt.contains("BlockPos")) {
            NbtCompound bp = nbt.getCompound("BlockPos");
            attachedBlockPos = new BlockPos(bp.getInt("X"), bp.getInt("Y"), bp.getInt("Z"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        UUID o = getOwnerUuid();
        if (o != null) nbt.putUuid("Owner", o);
        nbt.putFloat("RopeLen", getRopeLength());
        if (attachedBlockPos != null) {
            NbtCompound bp = new NbtCompound();
            bp.putInt("X", attachedBlockPos.getX());
            bp.putInt("Y", attachedBlockPos.getY());
            bp.putInt("Z", attachedBlockPos.getZ());
            nbt.put("BlockPos", bp);
        }
    }

     public boolean collides() { return false; }
    @Override public boolean shouldRender(double distance) { return true; }

    public static SniperGrappleAnchorEntity findAnchor(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof SniperGrappleAnchorEntity a) return a;
        }
        return null;
    }
}
