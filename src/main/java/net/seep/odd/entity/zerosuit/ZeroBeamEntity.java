package net.seep.odd.entity.zerosuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.seep.odd.abilities.power.ZeroSuitPower;

public class ZeroBeamEntity extends Entity {

    private static final TrackedData<Float> SX   = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SY   = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SZ   = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> DX   = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DY   = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DZ   = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> LEN  = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RAD  = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Integer> LIFE    = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MAXLIFE = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Float> PWR  = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private int life;
    private int maxLife;

    public ZeroBeamEntity(EntityType<? extends ZeroBeamEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(SX, 0f);
        dataTracker.startTracking(SY, 0f);
        dataTracker.startTracking(SZ, 0f);

        dataTracker.startTracking(DX, 0f);
        dataTracker.startTracking(DY, 0f);
        dataTracker.startTracking(DZ, 1f);

        dataTracker.startTracking(LEN, 0f);
        dataTracker.startTracking(RAD, 0.5f);

        dataTracker.startTracking(LIFE, 1);
        dataTracker.startTracking(MAXLIFE, 1);

        dataTracker.startTracking(PWR, 0f);
    }

    public void init(@NotNull ServerPlayerEntity src, @NotNull Vec3d start, @NotNull Vec3d dir,
                     double length, double radius, int visTicks, float powerRatio) {
        Vec3d n = dir.normalize();

        dataTracker.set(SX, (float) start.x);
        dataTracker.set(SY, (float) start.y);
        dataTracker.set(SZ, (float) start.z);

        dataTracker.set(DX, (float) n.x);
        dataTracker.set(DY, (float) n.y);
        dataTracker.set(DZ, (float) n.z);

        dataTracker.set(LEN, (float) length);
        dataTracker.set(RAD, (float) radius);

        this.life = visTicks;
        this.maxLife = visTicks;
        dataTracker.set(LIFE, life);
        dataTracker.set(MAXLIFE, maxLife);

        dataTracker.set(PWR, MathHelper.clamp(powerRatio, 0f, 1f));

        this.refreshPositionAndAngles(start.x, start.y, start.z, src.getYaw(), src.getPitch());
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) {
            if (--life <= 0) { discard(); return; }
            dataTracker.set(LIFE, life);
        } else {
            int prev = life;
            life = dataTracker.get(LIFE);
            maxLife = dataTracker.get(MAXLIFE);

            // one-time shake kick for nearby cameras on spawn at higher charges
            if (prev == 0 && life == maxLife) {
                float ratio = getPowerRatio();
                if (ratio >= 0.60f) {
                    Vec3d s = getStart();
                    Vec3d e = s.add(getDir().multiply(getBeamLength()));
                    Vec3d mid = s.lerp(e, 0.5);
                    double r = getRadius() + Math.min(10.0, getBeamLength() * 0.15);
                    ZeroSuitPower.ClientHud.shakeIfClose(mid, r, 1.0f * ratio, 10 + (int)(10 * ratio));
                }
            }
        }
    }

    // getters
    public Vec3d getStart()          { return new Vec3d(dataTracker.get(SX), dataTracker.get(SY), dataTracker.get(SZ)); }
    public Vec3d getDir()            { return new Vec3d(dataTracker.get(DX), dataTracker.get(DY), dataTracker.get(DZ)); }
    public double getBeamLength()    { return dataTracker.get(LEN); }
    public double getRadius()        { return Math.max(0.01, dataTracker.get(RAD)); }
    public int getLife()             { return dataTracker.get(LIFE); }
    public int getMaxLife()          { return Math.max(1, dataTracker.get(MAXLIFE)); }
    public float getLifeAlpha()      { return Math.min(1f, getLife() / (float) getMaxLife()); }
    public float getPowerRatio()     { return MathHelper.clamp(dataTracker.get(PWR), 0f, 1f); }

    // nbt
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", life);
        nbt.putInt("MaxLife", maxLife);
        nbt.putFloat("Pwr", getPowerRatio());
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("Life");
        maxLife = nbt.getInt("MaxLife");
        dataTracker.set(PWR, MathHelper.clamp(nbt.getFloat("Pwr"), 0f, 1f));
    }

    @Override public boolean isCollidable() { return false; }
}
