package net.seep.odd.entity.zerosuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ZeroBeamEntity extends Entity {
    // Tracked values so the client renderer has everything at spawn time
    private static final TrackedData<Float> DX     = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DY     = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DZ     = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public ZeroBeamEntity(EntityType<? extends ZeroBeamEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    /** Server-side initializer */
    public void init(Vec3d start, Vec3d dirUnit, double length, double radius, int lifeTicks) {
        this.refreshPositionAndAngles(start.x, start.y, start.z, 0f, 0f);
        this.dataTracker.set(DX, (float) (dirUnit.x * length));
        this.dataTracker.set(DY, (float) (dirUnit.y * length));
        this.dataTracker.set(DZ, (float) (dirUnit.z * length));
        this.dataTracker.set(RADIUS, (float) radius);
        this.dataTracker.set(LIFE, lifeTicks);
    }

    public Vec3d getStart() { return getPos(); }
    public Vec3d getEnd()   { return getPos().add(getDX(), getDY(), getDZ()); }
    public float getRadius(){ return this.dataTracker.get(RADIUS); }
    public int   getMaxLife(){ return this.dataTracker.get(LIFE); }

    public float getDX(){ return this.dataTracker.get(DX); }
    public float getDY(){ return this.dataTracker.get(DY); }
    public float getDZ(){ return this.dataTracker.get(DZ); }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(DX, 0f);
        this.dataTracker.startTracking(DY, 0f);
        this.dataTracker.startTracking(DZ, 0f);
        this.dataTracker.startTracking(RADIUS, 0.25f);
        this.dataTracker.startTracking(LIFE, 10);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && this.age >= getMaxLife()) discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.dataTracker.set(DX, nbt.getFloat("dx"));
        this.dataTracker.set(DY, nbt.getFloat("dy"));
        this.dataTracker.set(DZ, nbt.getFloat("dz"));
        this.dataTracker.set(RADIUS, nbt.getFloat("radius"));
        this.dataTracker.set(LIFE, nbt.getInt("life"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("dx", getDX());
        nbt.putFloat("dy", getDY());
        nbt.putFloat("dz", getDZ());
        nbt.putFloat("radius", getRadius());
        nbt.putInt("life", getMaxLife());
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public boolean shouldRender(double distance) { return true; }
}
