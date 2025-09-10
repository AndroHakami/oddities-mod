package net.seep.odd.abilities.voids;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class VoidPortalEntity extends Entity {
    private int life = 200; // ~10s
    private UUID owner;

    // ===== tracked facing normal so clients have full orientation immediately =====
    private static final TrackedData<Float> NX =
            DataTracker.registerData(VoidPortalEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> NY =
            DataTracker.registerData(VoidPortalEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> NZ =
            DataTracker.registerData(VoidPortalEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // server copy (also saved to NBT)
    private float nX = 0f, nY = 0f, nZ = 1f; // default face +Z

    public VoidPortalEntity(EntityType<? extends VoidPortalEntity> type, World world) {
        super(type, world);
        this.ignoreCameraFrustum = true;
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(NX, 0f);
        this.dataTracker.startTracking(NY, 0f);
        this.dataTracker.startTracking(NZ, 1f);
    }

    // ===== visual parameters (shared with renderer) =====
    public static final org.joml.Vector3f PURPLE_BRIGHT = new org.joml.Vector3f(0.82f, 0.48f, 1.0f);
    public static final org.joml.Vector3f PURPLE_DARK   = new org.joml.Vector3f(0.32f, 0.09f, 0.52f);

    public static final float R_BASE     = 0.65f;  // horizontal radius (blocks)
    public static final float R_WOBBLE   = 0.08f;  // small radius wobble
    public static final float HEIGHT     = 1.8f;   // total portal height (blocks)
    private static final int   ARC_COUNT  = 3;     // rotating “swirls”
    private static final int   ARC_POINTS = 24;    // density per arc per tick
    private static final float ARC_SPEED  = 0.15f; // angular speed

    /** Call this when spawning so the portal plane faces the player (normal = -look). */
    public void setFacingFrom(Entity owner) {
        Vec3d look = owner.getRotationVec(1f).normalize().multiply(-1); // face back toward player
        this.nX = (float) look.x;
        this.nY = (float) look.y;
        this.nZ = (float) look.z;
        // sync to clients
        this.dataTracker.set(NX, nX);
        this.dataTracker.set(NY, nY);
        this.dataTracker.set(NZ, nZ);
    }

    /** Normalized facing normal; on client we read tracked values. */
    public Vec3d getFacingNormal() {
        if (this.getWorld() != null && this.getWorld().isClient) {
            return new Vec3d(
                    this.dataTracker.get(NX),
                    this.dataTracker.get(NY),
                    this.dataTracker.get(NZ)
            ).normalize();
        }
        return new Vec3d(nX, nY, nZ).normalize();
    }

    public void setOwner(UUID o){ this.owner = o; }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("life");
        if (nbt.containsUuid("owner")) owner = nbt.getUuid("owner");
        if (nbt.contains("nx")) {
            nX = nbt.getFloat("nx");
            nY = nbt.getFloat("ny");
            nZ = nbt.getFloat("nz");
            if (!this.getWorld().isClient) {
                this.dataTracker.set(NX, nX);
                this.dataTracker.set(NY, nY);
                this.dataTracker.set(NZ, nZ);
            }
        }
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("life", life);
        if (owner != null) nbt.putUuid("owner", owner);
        nbt.putFloat("nx", nX);
        nbt.putFloat("ny", nY);
        nbt.putFloat("nz", nZ);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        // ===== PARTICLES (tilted vertical disk) =====
        if (getWorld() instanceof ServerWorld sw) {
            double t = this.age * ARC_SPEED;

            final Vec3d C = getPos().add(0, HEIGHT * 0.5, 0); // center
            final Vec3d N = getFacingNormal();                 // plane normal

            // build an orthonormal basis (R,U) spanning the portal plane
            Vec3d ref = Math.abs(N.y) > 0.98 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
            Vec3d R = ref.crossProduct(N).normalize(); // right in plane
            Vec3d U = N.crossProduct(R).normalize();   // up in plane

            for (int arc = 0; arc < ARC_COUNT; arc++) {
                double phase = t + arc * (Math.PI * 2.0 / ARC_COUNT);
                for (int i = 0; i < ARC_POINTS; i++) {
                    double u = (double) i / ARC_POINTS;
                    double theta = u * Math.PI * 2.0 + phase;

                    double rH = R_BASE + R_WOBBLE * Math.sin(theta * 3.0 + phase * 1.7); // horizontal radius
                    double rV = HEIGHT * 0.5;                                           // vertical radius

                    Vec3d pos = C.add(R.multiply(Math.cos(theta) * rH))
                            .add(U.multiply(Math.sin(theta) * rV));

                    sw.spawnParticles(
                            new DustColorTransitionParticleEffect(PURPLE_BRIGHT, PURPLE_DARK, 1.2f),
                            pos.x, pos.y, pos.z, 1, 0, 0, 0, 0
                    );
                }
            }

            // inward sparks from rim to center
            for (int s = 0; s < 4; s++) {
                double theta = random.nextDouble() * Math.PI * 2.0;
                double rim = R_BASE + 0.05 + random.nextDouble() * 0.12;
                Vec3d rimPos = C.add(R.multiply(Math.cos(theta) * rim))
                        .add(U.multiply(Math.sin(theta) * (HEIGHT * 0.5)));

                Vec3d vel = C.subtract(rimPos).multiply(0.25);
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, rimPos.x, rimPos.y, rimPos.z,
                        1, vel.x, vel.y, vel.z, 0.0);
            }

            // core depth


            // occasional rim flashes
            if ((age % 6) == 0) {
                double theta = random.nextDouble() * Math.PI * 2.0;
                Vec3d rim = C.add(R.multiply(Math.cos(theta) * (R_BASE + 0.05)))
                        .add(U.multiply(Math.sin(theta) * (HEIGHT * 0.5)));
                sw.spawnParticles(ParticleTypes.END_ROD, rim.x, rim.y, rim.z, 2, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // ===== TELEPORT DETECTION (oriented plane hit) =====
        final Vec3d C = getPos().add(0, HEIGHT * 0.5, 0);
        final Vec3d N = getFacingNormal();
        Vec3d ref = Math.abs(N.y) > 0.98 ? new Vec3d(1,0,0) : new Vec3d(0,1,0);
        Vec3d R = ref.crossProduct(N).normalize();
        Vec3d U = N.crossProduct(R).normalize();

        boolean used = false;

        // expand a box around the portal to limit scanning cost
        Box scan = new Box(getPos().add(-1,-1,-1), getPos().add(1, 2.5, 1));
        for (Entity e : getWorld().getOtherEntities(this, scan, Entity::isAlive)) {
            if (!(getWorld() instanceof ServerWorld sw)) break;

            // test entity center at mid-height
            Vec3d ec = e.getPos().add(0, e.getHeight() * 0.5, 0);
            Vec3d d  = ec.subtract(C);

            double forward = d.dotProduct(N);           // distance through the plane (+ in front)
            if (Math.abs(forward) > 0.5) continue;      // thickness

            double xr = d.dotProduct(R);
            double yu = d.dotProduct(U);
            if (Math.abs(xr) > (R_BASE + 0.25)) continue;
            if (Math.abs(yu) > (HEIGHT * 0.5 + 0.25)) continue;

            if (e instanceof ServerPlayerEntity sp) {
                VoidSystem.enterPortal(sp);
                used = true; break;
            } else {
                ServerWorld sworld = (ServerWorld) getWorld();
                ServerWorld voidW = sworld.getServer().getWorld(VoidSystem.VOID_WORLD);
                if (voidW != null) {
                    Entity newE = e.moveToWorld(voidW);
                    if (newE != null) {
                        newE.refreshPositionAndAngles(0.5, 66.0, 0.5, e.getYaw(), e.getPitch());
                        voidW.playSound(null, BlockPos.ofFloored(0.5, 66.0, 0.5),
                                SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                    }
                    used = true;
                }
            }
        }

        if (used) {
            if (getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_PORTAL_TRAVEL,
                        SoundCategory.PLAYERS, 0.7f, 1.0f);
            }
            discard();
            return;
        }

        if (--life <= 0) discard();
    }

    @Override public EntityDimensions getDimensions(EntityPose pose) { return EntityDimensions.fixed(1.2f, 2.0f); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
    @Override public boolean shouldRender(double distance) { return true; }
}
