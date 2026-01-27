package net.seep.odd.abilities.climber.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.seep.odd.abilities.power.ClimberPower;

import java.util.Optional;
import java.util.UUID;

public class ClimberPullTetherEntity extends Entity {

    private static final TrackedData<Optional<UUID>> OWNER =
            DataTracker.registerData(ClimberPullTetherEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> TARGET_ID =
            DataTracker.registerData(ClimberPullTetherEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LIFE =
            DataTracker.registerData(ClimberPullTetherEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public ClimberPullTetherEntity(EntityType<? extends ClimberPullTetherEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(OWNER, Optional.empty());
        this.dataTracker.startTracking(TARGET_ID, -1);
        this.dataTracker.startTracking(LIFE, 10);
    }

    public void setOwnerUuid(UUID id) {
        this.dataTracker.set(OWNER, Optional.ofNullable(id));
    }

    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER).orElse(null);
    }

    public void setTargetId(int id) {
        this.dataTracker.set(TARGET_ID, id);
    }

    public int getTargetId() {
        return this.dataTracker.get(TARGET_ID);
    }

    public void setLifetimeTicks(int ticks) {
        this.dataTracker.set(LIFE, Math.max(1, ticks));
    }

    public int getLifetimeTicks() {
        return this.dataTracker.get(LIFE);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        UUID ownerId = getOwnerUuid();
        if (ownerId == null) {
            discard();
            return;
        }

        ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }

        Entity t = sw.getEntityById(getTargetId());
        if (!(t instanceof LivingEntity target) || !target.isAlive()) {
            discard();
            return;
        }

        // Pull force
        Vec3d toOwner = owner.getPos().add(0, owner.getHeight() * 0.5, 0).subtract(target.getPos().add(0, target.getHeight() * 0.5, 0));
        double dist = toOwner.length();
        if (dist < 1.2) {
            discard();
            return;
        }

        Vec3d dir = toOwner.normalize();

        // stronger early, softer late
        double strength = Math.min(1.25, 0.55 + dist * 0.08);

        Vec3d v = target.getVelocity().add(dir.multiply(strength)).add(0, 0.06, 0);
        target.setVelocity(v);
        target.fallDistance = 0.0f;

        // keep tether entity riding near the target for rendering
        this.setPosition(target.getX(), target.getBodyY(0.5), target.getZ());

        // If target is a player, push velocity update
        if (target instanceof ServerPlayerEntity spT) {
            spT.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(spT));
        }

        int life = getLifetimeTicks() - 1;
        this.dataTracker.set(LIFE, life);
        if (life <= 0) discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) setOwnerUuid(nbt.getUuid("Owner"));
        if (nbt.contains("TargetId")) setTargetId(nbt.getInt("TargetId"));
        if (nbt.contains("Life")) setLifetimeTicks(nbt.getInt("Life"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        UUID o = getOwnerUuid();
        if (o != null) nbt.putUuid("Owner", o);
        nbt.putInt("TargetId", getTargetId());
        nbt.putInt("Life", getLifetimeTicks());
    }


    public boolean collides() { return false; }

    @Override
    public boolean shouldRender(double distance) { return true; }
}
