package net.seep.odd.abilities.climber.entity;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
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
        this.dataTracker.set(ROPE_LEN, Math.max(2.0f, Math.min(20.0f, len)));
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

        // Swing physics: constraint-based “rope” with tangential preservation
        Vec3d anchor = this.getPos();
        Vec3d waist = ClimberPower.ropeOrigin(owner);

        Vec3d r = waist.subtract(anchor);
        double d = r.length();
        if (d < 1.0e-4) {
            owner.fallDistance = 0;
            return;
        }

        double ropeLen = getRopeLength();

        // Only apply tension if stretched beyond rope length
        if (d > ropeLen) {
            Vec3d dir = r.multiply(1.0 / d);
            double error = d - ropeLen;

            Vec3d v = owner.getVelocity();

            // Remove outward radial velocity (prevents “rubberband away”)
            double vRad = v.dotProduct(dir);
            if (vRad > 0) {
                v = v.subtract(dir.multiply(vRad));
            }

            // Pull strength: proportional correction, clamped
            double pull = Math.min(1.25, error * 0.45);
            v = v.subtract(dir.multiply(pull));

            owner.setVelocity(v);
            owner.fallDistance = 0.0f;

            // Force a quick velocity sync (server authoritative rope feels WAY better with this)
            owner.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(owner));
        } else {
            // Even when slack, prevent fall damage while tethered
            owner.fallDistance = 0.0f;
        }
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
        return true; // we want rope to render from far away
    }

    /** Utility to find an anchor by UUID across worlds (same style as ConquerPower.findHorse). */
    public static ClimberRopeAnchorEntity findAnchor(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof ClimberRopeAnchorEntity a) return a;
        }
        return null;
    }
}
