package net.seep.odd.abilities.cosmic.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

// GeckoLib
import software.bernie.geckolib.animatable.GeoEntity;

import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Modes:
 *  HOVER — orbits owner until launched.
 *  SEEK  — homes toward owner's current crosshair every tick.
 */
public class HomingCosmicSwordEntity extends ProjectileEntity implements GeoEntity {
    private static final double SPEED = 1.0;      // blocks/tick when seeking
    private static final double TURN_RATE = 0.20; // steering strength toward crosshair
    private static final float DAMAGE = 6.0f;
    private static final int LIFE_MAX = 200;      // 10s max after spawn/launch

    private final AnimatableInstanceCache geckoCache = GeckoLibUtil.createInstanceCache(this);

    private enum Mode { HOVER, SEEK }
    private Mode mode = Mode.SEEK;

    // Hover params
    private int hoverIndex = 0;
    private int hoverTotal = 1;
    private float hoverRadius = 1.15f;
    private float hoverAngularSpeed = 9.5f; // deg per tick
    private double hoverY = -0.25;          // offset from eye

    private int life = LIFE_MAX;

    public HomingCosmicSwordEntity(EntityType<? extends HomingCosmicSwordEntity> type, World world) {
        super(type, world);
        this.noClip = true;
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
        this.life = LIFE_MAX; // reset life while preparing
        this.setVelocity(Vec3d.ZERO);
    }

    /** Launch toward current aim; switches to SEEK mode. */
    public void launch(Vec3d initialDir) {
        this.mode = Mode.SEEK;
        Vec3d dir = initialDir == null ? getRotationVector().normalize() : initialDir.normalize();
        this.setVelocity(dir.multiply(SPEED));
    }

    /* ======================= tick ======================= */
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        if (--life <= 0) { discard(); return; }

        LivingEntity owner = (LivingEntity) getOwner();
        if (owner == null || !owner.isAlive()) { discard(); return; }

        switch (mode) {
            case HOVER -> tickHover(owner);
            case SEEK  -> tickSeek(owner);
        }
    }

    private void tickHover(LivingEntity owner) {
        // Orbit around owner at fixed radius & height; advance angle with world age plus slot offset
        double baseYawRad = Math.toRadians((owner.age * hoverAngularSpeed) + (360.0 / hoverTotal) * hoverIndex);
        double ox = Math.cos(baseYawRad) * hoverRadius;
        double oz = Math.sin(baseYawRad) * hoverRadius;

        double cx = owner.getX();
        double cy = owner.getEyeY() + hoverY;
        double cz = owner.getZ();

        setVelocity(Vec3d.ZERO);
        setPosition(cx + ox, cy, cz + oz);

        // Face tangent direction for niceness
        float yaw = (float)(Math.toDegrees(Math.atan2(oz, ox)) + 90.0);
        setYaw(yaw);
        setPitch(0f);
    }

    private void tickSeek(LivingEntity owner) {
        // Steer toward owner's current crosshair direction
        Vec3d v = getVelocity();
        if (v.lengthSquared() < 0.001) v = getRotationVector().normalize().multiply(SPEED);

        Vec3d desired;
        if (owner instanceof ServerPlayerEntity sp) desired = sp.getRotationVec(1.0f).normalize().multiply(SPEED);
        else desired = v;

        Vec3d steered = v.lerp(desired, TURN_RATE);
        setVelocity(steered.normalize().multiply(SPEED));
        setYaw((float)(MathHelper.atan2(steered.z, steered.x) * (180f/Math.PI)) - 90f);
        setPitch((float)(-(MathHelper.atan2(steered.y, Math.sqrt(steered.x*steered.x + steered.z*steered.z)) * (180f/Math.PI))));

        // Collisions
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) onCollision(hit);
        updatePosition(getX() + getVelocity().x, getY() + getVelocity().y, getZ() + getVelocity().z);
    }

    public boolean canHit(Entity e) {
        Entity owner = getOwner();
        return e.isAttackable() && e.isAlive() && e != owner;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        DamageSource src = this.getWorld().getDamageSources().create(DamageTypes.MAGIC, this, (LivingEntity)getOwner());
        target.damage(src, DAMAGE);
        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        // spectral; keep flying. To collide, uncomment:
        // discard();
    }

    /* ======================= NBT / spawn ======================= */
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("life", life);
        nbt.putString("mode", mode.name());
        nbt.putInt("hIdx", hoverIndex);
        nbt.putInt("hTot", hoverTotal);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("life");
        try { mode = Mode.valueOf(nbt.getString("mode")); } catch (Exception ignored) {}
        hoverIndex = nbt.getInt("hIdx");
        hoverTotal = nbt.getInt("hTot");
    }
    @Override protected void initDataTracker() {}

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    /* ======================= GeckoLib ======================= */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Add a controller here if you have an animation clip (e.g., "spin")
    }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geckoCache;
    }
}
