package net.seep.odd.abilities.cosmic.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Modes:
 *  HOVER   — orbit owner until launched (can be scheduled).
 *  SEEK    — homes toward owner's current crosshair (forward flight).
 *  RETRACT — flies back toward owner's eye and despawns on arrival (ability controls).
 *  STUCK   — embedded in a block face; no movement; recall ignored while stuck.
 *
 * Key fixes:
 *  - Launch is gated by absolute world time (no same-tick firing).
 *  - Volley origin+direction can be pre-snapshotted for all swords (no spread).
 */
public class HomingCosmicSwordEntity extends ProjectileEntity implements GeoEntity {
    private static final double SPEED     = 0.9;   // blocks/tick
    private static final double TURN_RATE = 0.35;  // steering strength
    private static final float  DAMAGE    = 12.0f;
    private static final int    LIFE_MAX  = 320;   // ~16s
    private static final String GLOW_TEAM = "odd_cosmic_glow_black";

    // Client visual smoothing
    private static final float CLIENT_YAW_LERP   = 0.35f;
    private static final float CLIENT_PITCH_LERP = 0.35f;

    private final AnimatableInstanceCache geckoCache = GeckoLibUtil.createInstanceCache(this);

    private enum Mode { HOVER, SEEK, RETRACT, STUCK }
    private Mode mode = Mode.SEEK;

    // Hover params
    private int   hoverIndex        = 0;
    private int   hoverTotal        = 1;
    private float hoverRadius       = 1.15f;
    private float hoverAngularSpeed = 9.5f; // deg/tick
    private double hoverY           = -0.25;

    // Absolute-time launch gate
    private long launchAtTick = -1L;

    // Volley snapshot for perfect alignment
    private Vec3d fixedLaunchOrigin = null;
    private Vec3d fixedLaunchDir    = null;

    // Lifetime
    private int life = LIFE_MAX;

    // Glow team guard
    private boolean glowApplied = false;

    // Stuck-in-block state
    private boolean  stuck      = false;
    private BlockPos stuckPos   = null;
    private Direction stuckFace = null;

    public HomingCosmicSwordEntity(EntityType<? extends HomingCosmicSwordEntity> type, World world) {
        super(type, world);
        this.noClip = true;      // raycast for collisions
        this.setNoGravity(true); // no droop
    }
    public HomingCosmicSwordEntity(EntityType<? extends HomingCosmicSwordEntity> type, World world, LivingEntity owner) {
        this(type, world);
        this.setOwner(owner);
        this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
    }

    /* ======================= external controls ======================= */

    public void beginHover(int index, int total) {
        this.mode = Mode.HOVER;
        this.hoverIndex = Math.max(0, index);
        this.hoverTotal = Math.max(1, total);
        this.life = LIFE_MAX;
        this.launchAtTick = -1L;
        this.setVelocity(Vec3d.ZERO);
        this.stuck = false;
        this.stuckPos = null;
        this.stuckFace = null;
    }

    /** Convenience: set a fixed snapshot (origin+dir) to be used when launching. */
    public void setFixedSnapshot(Vec3d origin, Vec3d dir) {
        this.fixedLaunchOrigin = origin;
        this.fixedLaunchDir    = dir == null ? null : dir.normalize();
    }

    /** Schedule this sword to launch after a delay (ticks) using ABSOLUTE world time. */
    public void scheduleLaunch(int delayTicks) {
        this.mode = Mode.HOVER; // remain hovering until it’s time
        int d = Math.max(1, delayTicks); // ensure at least next tick
        this.launchAtTick = getWorld().getTime() + d;

        // If no fixed snapshot was provided, capture a per-entity one now (fallback)
        if (this.fixedLaunchOrigin == null || this.fixedLaunchDir == null) {
            LivingEntity owner = (LivingEntity) getOwner();
            if (owner != null) {
                this.fixedLaunchOrigin = owner.getEyePos().add(0, -0.1, 0);
                Vec3d look = (owner instanceof ServerPlayerEntity sp) ? sp.getRotationVec(1.0f)
                        : owner.getRotationVec(1.0f);
                this.fixedLaunchDir = look.normalize();
            } else {
                this.fixedLaunchOrigin = getPos();
                this.fixedLaunchDir    = getRotationVector().normalize();
            }
        }
    }

    /** Helper: schedule using a shared volley snapshot (identical for all swords). */
    public void scheduleLaunchWithSnapshot(Vec3d origin, Vec3d dir, int delayTicks) {
        setFixedSnapshot(origin, dir);
        scheduleLaunch(delayTicks);
    }

    public void launch(Vec3d initialDir) {
        this.mode = Mode.SEEK;
        this.launchAtTick = -1L;

        Vec3d origin = (fixedLaunchOrigin != null) ? fixedLaunchOrigin : getPos();
        Vec3d dir    = (fixedLaunchDir != null)    ? fixedLaunchDir    : (initialDir == null ? getRotationVector() : initialDir).normalize();

        this.setPosition(origin.x, origin.y, origin.z);
        this.setVelocity(dir.multiply(SPEED));
        setYawPitchFromDirection(dir);

        this.getWorld().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.7f);

        // keep snapshot null to avoid accidental reuse on re-schedule
        this.fixedLaunchOrigin = null;
        this.fixedLaunchDir    = null;

        this.stuck = false;
        this.stuckPos = null;
        this.stuckFace = null;
    }

    /** Public: can we be recalled? (false if stuck in a block) */
    public boolean isStuck() { return mode == Mode.STUCK && stuck; }

    /** Begin retracting toward the player; if stuck, recall is ignored (returns false). */
    public boolean startRetract() {
        if (isStuck()) return false; // do not allow recalling while stuck
        this.mode = Mode.RETRACT;
        this.launchAtTick = -1L;

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 0.5f, 1.4f);
        }
        return true;
    }

    private void setYawPitchFromDirection(Vec3d dir) {
        float yaw = (float)(MathHelper.atan2(dir.z, dir.x) * (180f/Math.PI)) - 90f;
        float pitch = (float)(-(MathHelper.atan2(dir.y, Math.sqrt(dir.x*dir.x + dir.z*dir.z)) * (180f/Math.PI)));
        setYaw(yaw);
        setPitch(pitch);
    }

    /* ======================= tick ======================= */
    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            clientVisualTick();
            return;
        }

        // SERVER

        if (!glowApplied && this.getWorld() instanceof ServerWorld sw) {
            applyGlow(sw);
            glowApplied = true;
        }

        if (--life <= 0) { removeGlowTeam(); discard(); return; }

        LivingEntity owner = (LivingEntity) getOwner();
        if (owner == null || !owner.isAlive()) { removeGlowTeam(); discard(); return; }

        // STUCK: do nothing but keep checking the block
        if (mode == Mode.STUCK) {
            if (stuck && stuckPos != null && this.getWorld().isAir(stuckPos)) {
                removeGlowTeam();
                discard();
            }
            return;
        }

        // Absolute-time gated launch while hovering
        if (mode == Mode.HOVER && launchAtTick >= 0 && this.getWorld().getTime() >= launchAtTick) {
            launch(fixedLaunchDir != null ? fixedLaunchDir
                    : (owner instanceof ServerPlayerEntity sp ? sp.getRotationVec(1.0f) : getRotationVector()));
            // (launch clears launchAtTick)
        }

        switch (mode) {
            case HOVER   -> serverTickHover(owner);
            case SEEK    -> serverTickSeek(owner);
            case RETRACT -> serverTickRetract(owner);
            default -> {}
        }
    }

    /* ---------- CLIENT VISUALS (smooth orientation + subtle trail when flying) ---------- */
    @Environment(EnvType.CLIENT)
    private void clientVisualTick() {
        // Smooth orient while flying (not when stuck)
        if (mode != Mode.STUCK) {
            Vec3d v = getVelocity();
            if (v.lengthSquared() > 1.0E-4) {
                float targetYaw   = (float)(MathHelper.atan2(v.z, v.x) * (180f/Math.PI)) - 90f;
                float targetPitch = (float)(-(MathHelper.atan2(v.y, Math.sqrt(v.x*v.x + v.z*v.z)) * (180f/Math.PI)));

                float newYaw   = MathHelper.lerpAngleDegrees(CLIENT_YAW_LERP,   getYaw(),   targetYaw);
                float newPitch = MathHelper.lerpAngleDegrees(CLIENT_PITCH_LERP, getPitch(), targetPitch);

                setYaw(newYaw);
                setPitch(newPitch);
            }
        }

        // Particles only while flying (SEEK/RETRACT)
        if ((mode == Mode.SEEK || mode == Mode.RETRACT) && (this.age & 1) == 0) {
            Vec3d v = getVelocity();

        }
    }

    /* ------------------------- SERVER LOGIC ------------------------- */
    private void serverTickHover(LivingEntity owner) {
        double baseYawRad = Math.toRadians((owner.age * hoverAngularSpeed) + (360.0 / hoverTotal) * hoverIndex);
        double ox = Math.cos(baseYawRad) * hoverRadius;
        double oz = Math.sin(baseYawRad) * hoverRadius;

        double cx = owner.getX();
        double cy = owner.getEyeY() + hoverY;
        double cz = owner.getZ();

        setVelocity(Vec3d.ZERO);
        setPosition(cx + ox, cy, cz + oz);

        float yaw = (float)(Math.toDegrees(Math.atan2(oz, ox)) + 90.0);
        setYaw(yaw);
        setPitch(0f);
    }

    private void serverTickSeek(LivingEntity owner) {
        Vec3d v = getVelocity();
        if (v.lengthSquared() < 0.001) v = getRotationVector().normalize().multiply(SPEED);

        Vec3d desired = (owner instanceof ServerPlayerEntity sp)
                ? sp.getRotationVec(1.0f).normalize().multiply(SPEED)
                : v;

        Vec3d steered = v.lerp(desired, TURN_RATE);
        setVelocity(steered.normalize().multiply(SPEED));
        setYawPitchFromDirection(steered);

        // collisions
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) onCollision(hit);

        updatePosition(getX() + getVelocity().x, getY() + getVelocity().y, getZ() + getVelocity().z);
    }

    private void serverTickRetract(LivingEntity owner) {
        Vec3d eye = owner.getEyePos().add(0, -0.1, 0);
        Vec3d toEye = eye.subtract(getPos());
        Vec3d desired = toEye.normalize().multiply(SPEED);

        Vec3d steered = getVelocity().lerp(desired, TURN_RATE);
        setVelocity(steered.normalize().multiply(SPEED));
        setYawPitchFromDirection(steered);

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) onCollision(hit);

        updatePosition(getX() + getVelocity().x, getY() + getVelocity().y, getZ() + getVelocity().z);
    }

    public boolean canHit(Entity e) {
        Entity owner = getOwner();
        return e.isAttackable() && e.isAlive() && e != owner;
    }

    /* ======================= collisions ======================= */
    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        DamageSource src = this.getWorld().getDamageSources().create(DamageTypes.MAGIC, this, (LivingEntity)getOwner());
        target.damage(src, DAMAGE);
        removeGlowTeam();
        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (this.getWorld().isClient) return;

        // Face the block / embed slightly
        Vec3d v = getVelocity();
        if (v.lengthSquared() > 1.0E-4) {
            setYawPitchFromDirection(v);
        } else {
            Direction f = hit.getSide();
            Vec3d n = new Vec3d(-f.getOffsetX(), -f.getOffsetY(), -f.getOffsetZ());
            setYawPitchFromDirection(n);
        }

        Direction face = hit.getSide();
        Vec3d n = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
        Vec3d embed = hit.getPos().subtract(n.multiply(0.01));
        this.setPosition(embed.x, embed.y, embed.z);

        // Stop and mark stuck
        this.setVelocity(Vec3d.ZERO);
        this.mode = Mode.STUCK;
        this.stuck = true;
        this.stuckPos = hit.getBlockPos();
        this.stuckFace = face;

        this.getWorld().playSound(null, getX(), getY(), getZ(),
                SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.6f, 1.1f);
    }

    /* ======================= glow helpers (BLACK outline) ======================= */
    private void applyGlow(ServerWorld sw) {
        try {
            Scoreboard sb = sw.getScoreboard();
            Team team = sb.getTeam(GLOW_TEAM);
            if (team == null) {
                team = sb.addTeam(GLOW_TEAM);
                team.setColor(Formatting.BLACK);
                team.setFriendlyFireAllowed(true);
            }
            String entry = this.getUuidAsString();
            if (!team.getPlayerList().contains(entry)) {
                sb.addPlayerToTeam(entry, team);
            }
            this.setGlowing(true);
        } catch (Exception ignored) {}
    }

    private void removeGlowTeam() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        try {
            Scoreboard sb = sw.getScoreboard();
            Team team = sb.getTeam(GLOW_TEAM);
            if (team != null) {
                String entry = this.getUuidAsString();
                if (team.getPlayerList().contains(entry)) sb.removePlayerFromTeam(entry, team);
            }
        } catch (Exception ignored) {}
        this.setGlowing(false);
    }

    /* ======================= NBT / spawn ======================= */
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("life", life);
        nbt.putString("mode", mode.name());
        nbt.putInt("hIdx", hoverIndex);
        nbt.putInt("hTot", hoverTotal);

        nbt.putLong("launchAt", launchAtTick);

        nbt.putBoolean("hasFix", fixedLaunchOrigin != null && fixedLaunchDir != null);
        if (fixedLaunchOrigin != null && fixedLaunchDir != null) {
            nbt.putDouble("fx", fixedLaunchOrigin.x);
            nbt.putDouble("fy", fixedLaunchOrigin.y);
            nbt.putDouble("fz", fixedLaunchOrigin.z);
            nbt.putDouble("dx", fixedLaunchDir.x);
            nbt.putDouble("dy", fixedLaunchDir.y);
            nbt.putDouble("dz", fixedLaunchDir.z);
        }

        nbt.putBoolean("stuck", stuck);
        if (stuckPos != null) {
            nbt.putInt("spx", stuckPos.getX());
            nbt.putInt("spy", stuckPos.getY());
            nbt.putInt("spz", stuckPos.getZ());
        }
        nbt.putInt("sface", stuckFace == null ? -1 : stuckFace.getId());
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("life");
        try { mode = Mode.valueOf(nbt.getString("mode")); } catch (Exception ignored) {}
        hoverIndex = nbt.getInt("hIdx");
        hoverTotal = nbt.getInt("hTot");

        launchAtTick = nbt.contains("launchAt") ? nbt.getLong("launchAt") : -1L;

        if (nbt.getBoolean("hasFix")) {
            fixedLaunchOrigin = new Vec3d(nbt.getDouble("fx"), nbt.getDouble("fy"), nbt.getDouble("fz"));
            fixedLaunchDir    = new Vec3d(nbt.getDouble("dx"), nbt.getDouble("dy"), nbt.getDouble("dz")).normalize();
        } else {
            fixedLaunchOrigin = null;
            fixedLaunchDir    = null;
        }

        stuck = nbt.getBoolean("stuck");
        if (nbt.contains("spx")) {
            stuckPos = new BlockPos(nbt.getInt("spx"), nbt.getInt("spy"), nbt.getInt("spz"));
        }
        int f = nbt.getInt("sface");
        stuckFace = (f < 0 || f >= Direction.values().length) ? null : Direction.byId(f);
    }
    @Override protected void initDataTracker() {}

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    /* ======================= GeckoLib ======================= */
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return geckoCache; }
}
