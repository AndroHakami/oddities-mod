package net.seep.odd.abilities.climber.entity;

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
import net.seep.odd.abilities.power.ClimberPower;

import java.util.Optional;
import java.util.UUID;

public class ClimberRopeAnchorEntity extends Entity {

    private static final TrackedData<Optional<UUID>> OWNER =
            DataTracker.registerData(ClimberRopeAnchorEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private static final TrackedData<Float> ROPE_LEN =
            DataTracker.registerData(ClimberRopeAnchorEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private BlockPos attachedBlockPos = null;

    public ClimberRopeAnchorEntity(EntityType<? extends ClimberRopeAnchorEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(OWNER, Optional.empty());
        this.dataTracker.startTracking(ROPE_LEN, 6.0f);
    }

    public void setOwnerUuid(UUID id) {
        this.dataTracker.set(OWNER, Optional.ofNullable(id));
    }

    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER).orElse(null);
    }

    public void setRopeLength(float len) {
        // max is now 30m
        this.dataTracker.set(ROPE_LEN, Math.max(2.0f, Math.min(30.0f, len)));
    }

    public float getRopeLength() {
        return this.dataTracker.get(ROPE_LEN);
    }

    public void setAttachedBlockPos(BlockPos pos) {
        this.attachedBlockPos = pos;
    }

    public BlockPos getAttachedBlockPos() {
        return attachedBlockPos;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;

        UUID ownerId = getOwnerUuid();
        if (ownerId == null) {
            this.discard();
            return;
        }

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        // If block disappeared, drop rope
        if (attachedBlockPos != null && sw.getBlockState(attachedBlockPos).isAir()) {
            this.discard();
            return;
        }

        // Rope length control: Space shortens, Shift lengthens
        byte in = ClimberPower.getInputFlags(owner);
        boolean jump  = (in & ClimberPower.IN_JUMP) != 0;
        boolean sneak = (in & ClimberPower.IN_SNEAK) != 0;

        float L = getRopeLength();
        if (jump && !sneak) {
            L -= 0.32f;
        } else if (sneak && !jump) {
            L += 0.32f;
        }
        setRopeLength(L);

        // Rope vectors
        Vec3d anchor = this.getPos();
        Vec3d waist  = ClimberPower.ropeOrigin(owner);

        Vec3d r = waist.subtract(anchor);
        double d = r.length();
        if (d < 1.0e-4) {
            owner.fallDistance = 0.0f;
            return;
        }

        double ropeLen = getRopeLength();
        Vec3d dir = r.multiply(1.0 / d); // anchor -> player

        // =========================
        // Tiny swing pump (WASD -> tangential impulse)
        // =========================
        boolean f = (in & ClimberPower.IN_FORWARD) != 0;
        boolean b = (in & ClimberPower.IN_BACK) != 0;
        boolean l = (in & ClimberPower.IN_LEFT) != 0;
        boolean rgt = (in & ClimberPower.IN_RIGHT) != 0;

        if (f || b || l || rgt) {
            Vec3d look = owner.getRotationVec(1.0f);
            Vec3d forward = new Vec3d(look.x, 0.0, look.z);
            if (forward.lengthSquared() > 1.0e-6) forward = forward.normalize();
            Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);

            Vec3d wish = Vec3d.ZERO;
            if (f)   wish = wish.add(forward);
            if (b)   wish = wish.subtract(forward);
            if (l)   wish = wish.subtract(right);
            if (rgt) wish = wish.add(right);

            if (wish.lengthSquared() > 1.0e-6) {
                Vec3d wishDir = wish.normalize();

                // tangential (remove component along rope)
                Vec3d tang = wishDir.subtract(dir.multiply(wishDir.dotProduct(dir)));
                if (tang.lengthSquared() > 1.0e-6) {
                    double pump = 0.055; // tiny boost

                    // if slack, reduce pump
                    if (d < ropeLen * 0.95) pump *= 0.40;

                    Vec3d v = owner.getVelocity().add(tang.normalize().multiply(pump));
                    owner.setVelocity(v);
                    owner.fallDistance = 0.0f;
                    owner.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(owner));
                }
            }
        }

        // =========================
        // Rope tension constraint (when taut)
        // =========================
        if (d > ropeLen) {
            double error = d - ropeLen;

            Vec3d v = owner.getVelocity();

            // Remove outward radial velocity
            double vRad = v.dotProduct(dir);
            if (vRad > 0.0) {
                v = v.subtract(dir.multiply(vRad));
            }

            // Snappy constraint
            double pull = Math.min(1.45, error * 0.65);
            v = v.subtract(dir.multiply(pull));

            owner.setVelocity(v);
            owner.fallDistance = 0.0f;
            owner.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(owner));
        } else {
            owner.fallDistance = 0.0f;
        }

        // Gentle horizontal drag while tethered (keeps things from going infinite)
        Vec3d v = owner.getVelocity();
        double drag = 0.992;
        owner.setVelocity(v.x * drag, v.y, v.z * drag);
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


    public boolean collides() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    public static ClimberRopeAnchorEntity findAnchor(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof ClimberRopeAnchorEntity a) return a;
        }
        return null;
    }
}
