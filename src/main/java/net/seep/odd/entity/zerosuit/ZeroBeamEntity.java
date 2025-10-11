package net.seep.odd.entity.zerosuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Ephemeral visual beam entity. The server owns lifetime and fills a few
 * tracked fields (start/dir/length/width/life). Clients render the beam
 * using those tracked values; no custom spawn packet data is required.
 *
 * IMPORTANT:
 *  - Do NOT override Entity#getWidth() or #getLerpedPos() (final in 1.20.x).
 *  - Any "visual width" should be read by the renderer via getBeamWidth().
 */
public final class ZeroBeamEntity extends Entity {

    /* --------------------- Tracked data --------------------- */
    // Start position in world space
    private static final TrackedData<Float> SX = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SY = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SZ = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // Normalized direction vector
    private static final TrackedData<Float> DX = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DY = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DZ = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // Length (blocks) and visual width (radius in blocks)
    private static final TrackedData<Float> LEN = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> WID = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // Lifetime counters
    private static final TrackedData<Integer> LIFE     = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MAX_LIFE = DataTracker.registerData(ZeroBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // Server-authoritative life counters (mirrored into tracker)
    private int life;
    private int maxLife;

    public ZeroBeamEntity(EntityType<? extends ZeroBeamEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    /* --------------------- Init & lifetime --------------------- */

    /**
     * Server-side initializer. Populates tracked fields and positions
     * the entity roughly at the beam center (for culling). Rendering
     * should rely on tracked start/dir/length instead of entity pos.
     */
    public void init(@NotNull ServerPlayerEntity owner,
                     @NotNull Vec3d start,
                     @NotNull Vec3d dirNorm,
                     double length,
                     double radius,
                     int visualTicks) {

        Vec3d n = dirNorm.normalize();

        this.dataTracker.set(SX, (float) start.x);
        this.dataTracker.set(SY, (float) start.y);
        this.dataTracker.set(SZ, (float) start.z);

        this.dataTracker.set(DX, (float) n.x);
        this.dataTracker.set(DY, (float) n.y);
        this.dataTracker.set(DZ, (float) n.z);

        this.dataTracker.set(LEN, (float) length);
        this.dataTracker.set(WID, (float) radius);

        this.life = visualTicks;
        this.maxLife = visualTicks;
        this.dataTracker.set(LIFE,     life);
        this.dataTracker.set(MAX_LIFE, maxLife);

        // Park the entity at beam midpoint so vanilla culling makes sense.
        Vec3d mid = start.add(n.multiply(length * 0.5));
        this.refreshPositionAndAngles(mid.x, mid.y, mid.z, owner.getYaw(), owner.getPitch());
    }

    @Override
    protected void initDataTracker() {
        // sensible defaults to avoid NPEs when a client renders before sync
        this.dataTracker.startTracking(SX, 0f);
        this.dataTracker.startTracking(SY, 0f);
        this.dataTracker.startTracking(SZ, 0f);

        this.dataTracker.startTracking(DX, 0f);
        this.dataTracker.startTracking(DY, 0f);
        this.dataTracker.startTracking(DZ, 1f);

        this.dataTracker.startTracking(LEN, 0f);
        this.dataTracker.startTracking(WID, 0.5f);

        this.dataTracker.startTracking(LIFE, 1);
        this.dataTracker.startTracking(MAX_LIFE, 1);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            if (--life <= 0) {
                discard();
                return;
            }
            this.dataTracker.set(LIFE, life);
        } else {
            // Mirror counters for client-side fade
            this.life    = this.dataTracker.get(LIFE);
            this.maxLife = this.dataTracker.get(MAX_LIFE);
        }
    }

    /* --------------------- Accessors for renderer --------------------- */

    /** Beam start position (world space). */
    public Vec3d getStart() {
        return new Vec3d(
                this.dataTracker.get(SX),
                this.dataTracker.get(SY),
                this.dataTracker.get(SZ)
        );
    }

    /** Normalized beam direction. */
    public Vec3d getDir() {
        return new Vec3d(
                this.dataTracker.get(DX),
                this.dataTracker.get(DY),
                this.dataTracker.get(DZ)
        );
    }

    /** Beam length in blocks. */
    public double getBeamLength() {
        return this.dataTracker.get(LEN);
    }

    /**
     * Visual radius in blocks used by the renderer.
     * (Your ribbon’s diameter is radius*2.)
     */
    public double getBeamWidth() {
        return this.dataTracker.get(WID);
    }

    /** Remaining visual ticks (for client fade/alpha). */

    public int getLife() {
        // pull from the tracker so it’s valid on the first client frame
        return this.dataTracker.get(LIFE);
    }


    /** Max lifetime in ticks. */
    public int getMaxLife() {
        return this.maxLife;
    }

    /* --------------------- Persistence (ephemeral) --------------------- */

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", life);
        nbt.putInt("MaxLife", maxLife);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
        this.maxLife = nbt.getInt("MaxLife");
        // Keep trackers in sync if ever loaded (normally despawns first)
        this.dataTracker.set(LIFE, life);
        this.dataTracker.set(MAX_LIFE, maxLife);
    }

    /* --------------------- Misc --------------------- */

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        // no-op; beam has no passengers
    }
}
