package net.seep.odd.abilities.voids;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
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

    public VoidPortalEntity(EntityType<? extends VoidPortalEntity> type, World world) {
        super(type, world);
        this.ignoreCameraFrustum = true;
        this.noClip = true;
    }

    // ===== visual parameters (shared with renderer) =====
    // Palette (0..1). tweak to taste.
    public static final org.joml.Vector3f PURPLE_BRIGHT = new org.joml.Vector3f(0.82f, 0.48f, 1.0f);
    public static final org.joml.Vector3f PURPLE_DARK   = new org.joml.Vector3f(0.32f, 0.09f, 0.52f);

    // Shape/speed
    public static final float R_BASE     = 0.65f;  // horizontal radius of the disk (blocks)
    public static final float R_WOBBLE   = 0.08f;  // small radius wobble
    public static final float HEIGHT     = 1.8f;   // total portal height (blocks)
    private static final int   ARC_COUNT  = 3;     // rotating “swirls”
    private static final int   ARC_POINTS = 24;    // density per arc per tick
    private static final float ARC_SPEED  = 0.15f; // angular speed

    // portal orientation (radians, around Y)
    private float orientYawRad = 0f;

    /** Call this when spawning so the portal "faces" the caster's yaw. */
    public void setFacingFrom(Entity owner) {
        this.orientYawRad = (float) Math.toRadians(owner.getYaw());
    }
    public float getFacingYawRad() { return orientYawRad; }

    public void setOwner(UUID o){ this.owner = o; }

    @Override protected void initDataTracker() {}

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("life");
        if (nbt.containsUuid("owner")) owner = nbt.getUuid("owner");
        if (nbt.contains("oy")) orientYawRad = nbt.getFloat("oy");
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("life", life);
        if (owner != null) nbt.putUuid("owner", owner);
        nbt.putFloat("oy", orientYawRad);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        // ===== PARTICLES =====
        if (getWorld() instanceof ServerWorld sw) {
            double t = this.age * ARC_SPEED;

            // plane basis: RIGHT (in world XZ) and UP (Y)
            final double cy = getY() + HEIGHT * 0.5;
            final double yaw = this.orientYawRad;
            final double rx = Math.cos(yaw), rz = Math.sin(yaw); // RIGHT direction in XZ

            // rotating swirls (vertical disk)
            for (int arc = 0; arc < ARC_COUNT; arc++) {
                double phase = t + arc * (Math.PI * 2.0 / ARC_COUNT);
                for (int i = 0; i < ARC_POINTS; i++) {
                    double u = (double) i / ARC_POINTS;
                    double theta = u * Math.PI * 2.0 + phase;

                    double rH = R_BASE + R_WOBBLE * Math.sin(theta * 3.0 + phase * 1.7); // horizontal radius
                    double rV = HEIGHT * 0.5;                                           // vertical radius

                    double x = getX() + Math.cos(theta) * rH * rx; // along RIGHT
                    double y = cy + Math.sin(theta) * rV;          // along UP
                    double z = getZ() + Math.cos(theta) * rH * rz; // along RIGHT

                    sw.spawnParticles(
                            new DustColorTransitionParticleEffect(PURPLE_BRIGHT, PURPLE_DARK, 1.2f),
                            x, y, z, 1, 0, 0, 0, 0
                    );
                }
            }

            // inward sparks from rim to center (same plane)
            for (int s = 0; s < 4; s++) {
                double theta = random.nextDouble() * Math.PI * 2.0;
                double rim = R_BASE + 0.05 + random.nextDouble() * 0.12;
                double sx = getX() + Math.cos(theta) * rim * rx;
                double sy = cy + Math.sin(theta) * (HEIGHT * 0.5);
                double sz = getZ() + Math.cos(theta) * rim * rz;

                double vx = (getX() - sx) * 0.25;
                double vy = (cy      - sy) * 0.25;
                double vz = (getZ() - sz) * 0.25;

                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, sx, sy, sz, 1, vx, vy, vz, 0.0);
            }

            // core depth


            // occasional rim flashes
            if ((age % 6) == 0) {
                double theta = random.nextDouble() * Math.PI * 2.0;
                double sx = getX() + Math.cos(theta) * (R_BASE + 0.05) * rx;
                double sy = cy + Math.sin(theta) * (HEIGHT * 0.5);
                double sz = getZ() + Math.cos(theta) * (R_BASE + 0.05) * rz;
                sw.spawnParticles(ParticleTypes.END_ROD, sx, sy, sz, 2, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // ===== TELEPORT DETECTION =====
        Vec3d p = getPos();
        Box detect = new Box(
                p.x - 0.7, p.y,       p.z - 0.7,
                p.x + 0.7, p.y + 2.4, p.z + 0.7
        );

        boolean used = false;

        for (Entity e : getWorld().getOtherEntities(this, detect, Entity::isAlive)) {
            if (!(getWorld() instanceof ServerWorld sw)) break;

            if (e instanceof ServerPlayerEntity sp) {
                // players → system handles round-trip
                VoidSystem.enterPortal(sp);
                used = true;
                break;
            } else {
                // non-players: move to void world
                ServerWorld voidW = sw.getServer().getWorld(VoidSystem.VOID_WORLD);
                if (voidW == null) continue;
                Entity newE = e.moveToWorld(voidW);
                if (newE != null) {
                    newE.refreshPositionAndAngles(0.5, 66.0, 0.5, e.getYaw(), e.getPitch());
                    voidW.playSound(null, BlockPos.ofFloored(0.5, 66.0, 0.5),
                            SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                }
                used = true;
            }
        }

        if (used) {
            if (getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, getBlockPos(),
                        SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.7f, 1.0f);
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
