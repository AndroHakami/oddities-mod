package net.seep.odd.entity.windwitch;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class TornadoProjectileEntity extends ExplosiveProjectileEntity implements GeoEntity {

    private static final RawAnimation SPAWN = RawAnimation.begin().thenPlay("spawn");
    private static final RawAnimation IDLE  = RawAnimation.begin().thenLoop("idle");

    private static final int SPAWN_ANIM_TICKS = 12;
    private static final int MAX_AGE = 120;

    private static final int STATE_HELD      = 0;
    private static final int STATE_TO_ANCHOR = 1;
    private static final int STATE_ORBIT     = 2;
    private static final int STATE_RETURN    = 3;

    private static final float PULL_RADIUS = 3.4f;
    private static final float EXPLOSION_RADIUS = 4.5f;
    private static final float DOT_DAMAGE = 2.0f;
    private static final float EXPLOSION_DAMAGE = 4.0f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private boolean armed = false;
    private boolean heldVisual = false;
    private int life = 0;
    private int state = STATE_HELD;
    private int phaseTime = 0;
    private int slotIndex = 0;

    private double anchorX;
    private double anchorY;
    private double anchorZ;

    private double returnStartX;
    private double returnStartY;
    private double returnStartZ;

    private float orbitAngle = 0.0f;
    private float orbitRadius = 2.05f;

    public TornadoProjectileEntity(EntityType<? extends ExplosiveProjectileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
    }

    public boolean isArmed() {
        return this.armed;
    }

    public void setHeldVisual(boolean heldVisual) {
        this.heldVisual = heldVisual;
    }

    public boolean isHeldVisual() {
        return this.heldVisual;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public void launchToAnchor(Vec3d anchor) {
        this.armed = true;
        this.heldVisual = false;
        this.state = STATE_TO_ANCHOR;
        this.phaseTime = 0;

        this.anchorX = anchor.x;
        this.anchorY = anchor.y;
        this.anchorZ = anchor.z;

        this.orbitAngle = (float) (slotIndex * (Math.PI * 2.0 / 3.0));
        this.noClip = true;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }

        this.noClip = true;

        if (this.getWorld().isClient) {
            return;
        }

        if (++life > MAX_AGE) {
            explodeWind();
            this.discard();
            return;
        }

        if (!armed || state == STATE_HELD) {
            this.setVelocity(Vec3d.ZERO);
            this.powerX = 0.0;
            this.powerY = 0.0;
            this.powerZ = 0.0;
            return;
        }

        switch (state) {
            case STATE_TO_ANCHOR -> tickToAnchor();
            case STATE_ORBIT -> tickOrbit();
            case STATE_RETURN -> tickReturn();
        }
    }

    private void tickToAnchor() {
        phaseTime++;

        Vec3d current = this.getPos();
        Vec3d anchor = new Vec3d(anchorX, anchorY, anchorZ);
        Vec3d delta = anchor.subtract(current);

        if (delta.lengthSquared() <= 0.20 || phaseTime >= 18) {
            this.setPosition(anchorX, anchorY, anchorZ);
            this.setVelocity(Vec3d.ZERO);
            this.state = STATE_ORBIT;
            this.phaseTime = 0;
            return;
        }

        Vec3d step = delta.normalize().multiply(0.55);
        this.setPosition(current.x + step.x, current.y + step.y, current.z + step.z);
        this.setVelocity(step);

        applyVortex();
        spawnWindParticles();
    }

    private void tickOrbit() {
        phaseTime++;
        orbitAngle += 0.52f;

        double x = anchorX + Math.cos(orbitAngle) * orbitRadius;
        double z = anchorZ + Math.sin(orbitAngle) * orbitRadius;
        double y = anchorY + Math.sin((life + slotIndex * 7) * 0.35) * 0.18;

        this.setPosition(x, y, z);
        this.setVelocity(Vec3d.ZERO);

        applyVortex();
        spawnWindParticles();

        if (phaseTime >= 28) {
            this.state = STATE_RETURN;
            this.phaseTime = 0;
            this.returnStartX = this.getX();
            this.returnStartY = this.getY();
            this.returnStartZ = this.getZ();
        }
    }

    private void tickReturn() {
        phaseTime++;

        double t = Math.min(1.0, phaseTime / 10.0);
        double x = MathHelper.lerp(t, returnStartX, anchorX);
        double y = MathHelper.lerp(t, returnStartY, anchorY);
        double z = MathHelper.lerp(t, returnStartZ, anchorZ);

        this.setPosition(x, y, z);
        this.setVelocity(Vec3d.ZERO);

        applyVortex();
        spawnWindParticles();

        if (phaseTime >= 10) {
            explodeWind();
            this.discard();
        }
    }

    private void spawnWindParticles() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        sw.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.7, this.getZ(),
                4, 0.35, 0.45, 0.35, 0.01);
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY() + 0.35, this.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
    }

    private void applyVortex() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        Box box = this.getBoundingBox().expand(PULL_RADIUS, 1.25, PULL_RADIUS);
        Entity owner = this.getOwner();

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive())) {
            if (owner != null && e == owner) continue;

            double ex = e.getX();
            double ey = e.getY() + e.getHeight() * 0.5;
            double ez = e.getZ();

            Vec3d toCenter = new Vec3d(this.getX() - ex, (this.getY() + 0.6) - ey, this.getZ() - ez);
            double dist = toCenter.length();

            if (dist > PULL_RADIUS || dist < 1.0E-6) continue;

            Vec3d pull = toCenter.normalize().multiply(0.22 * (1.0 - (dist / PULL_RADIUS) * 0.55));
            e.addVelocity(pull.x, Math.max(0.05, pull.y * 0.35 + 0.06), pull.z);
            e.velocityModified = true;

            if (life % 10 == 0) {
                e.damage(sw.getDamageSources().indirectMagic(this, owner), DOT_DAMAGE);
            }
        }
    }

    private void explodeWind() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        double x = this.anchorX;
        double y = this.anchorY + 0.35;
        double z = this.anchorZ;

        sw.spawnParticles(ParticleTypes.CLOUD, x, y, z, 48, 0.9, 0.55, 0.9, 0.06);
        sw.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 6, 0.15, 0.10, 0.15, 0.0);
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 12, 1.0, 0.25, 1.0, 0.0);
        sw.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, this.getSoundCategory(), 0.85f, 1.35f);
        sw.playSound(null, x, y, z, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, this.getSoundCategory(), 0.9f, 1.5f);

        Box box = new Box(
                x - EXPLOSION_RADIUS, y - 1.25, z - EXPLOSION_RADIUS,
                x + EXPLOSION_RADIUS, y + 1.75, z + EXPLOSION_RADIUS
        );

        Entity owner = this.getOwner();

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive())) {
            if (owner != null && e == owner) continue;

            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            Vec3d push = new Vec3d(dx, 0.0, dz);

            if (push.lengthSquared() < 1.0E-6) {
                push = new Vec3d(0.0, 0.0, 1.0);
            } else {
                push = push.normalize();
            }

            e.addVelocity(push.x * 1.35, 0.65, push.z * 1.35);
            e.velocityModified = true;
            e.damage(sw.getDamageSources().indirectMagic(this, owner), EXPLOSION_DAMAGE);
        }
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        // Intentionally ignored.
        // This tornado follows scripted motion and only explodes at the end.
    }

    @Override
    protected ParticleEffect getParticleType() {
        return ParticleTypes.CLOUD;
    }

    @Override
    protected boolean isBurning() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Armed", armed);
        nbt.putBoolean("HeldVisual", heldVisual);
        nbt.putInt("Life", life);
        nbt.putInt("State", state);
        nbt.putInt("PhaseTime", phaseTime);
        nbt.putInt("SlotIndex", slotIndex);

        nbt.putDouble("AnchorX", anchorX);
        nbt.putDouble("AnchorY", anchorY);
        nbt.putDouble("AnchorZ", anchorZ);

        nbt.putDouble("ReturnStartX", returnStartX);
        nbt.putDouble("ReturnStartY", returnStartY);
        nbt.putDouble("ReturnStartZ", returnStartZ);

        nbt.putFloat("OrbitAngle", orbitAngle);
        nbt.putFloat("OrbitRadius", orbitRadius);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.armed = nbt.getBoolean("Armed");
        this.heldVisual = nbt.getBoolean("HeldVisual");
        this.life = nbt.getInt("Life");
        this.state = nbt.getInt("State");
        this.phaseTime = nbt.getInt("PhaseTime");
        this.slotIndex = nbt.getInt("SlotIndex");

        this.anchorX = nbt.getDouble("AnchorX");
        this.anchorY = nbt.getDouble("AnchorY");
        this.anchorZ = nbt.getDouble("AnchorZ");

        this.returnStartX = nbt.getDouble("ReturnStartX");
        this.returnStartY = nbt.getDouble("ReturnStartY");
        this.returnStartZ = nbt.getDouble("ReturnStartZ");

        this.orbitAngle = nbt.getFloat("OrbitAngle");
        this.orbitRadius = nbt.getFloat("OrbitRadius");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (this.age < SPAWN_ANIM_TICKS) {
                state.setAndContinue(SPAWN);
                return PlayState.CONTINUE;
            }

            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public void noClip(boolean b) {
        this.noClip = b;
    }

    public void setNoClip(boolean b) {
        this.noClip = b;
    }
}