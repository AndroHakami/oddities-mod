// net/seep/odd/abilities/tamer/projectile/TameBallEntity.java
package net.seep.odd.abilities.tamer.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.abilities.tamer.TamerServerHooks;
import net.seep.odd.entity.ModEntities;

// GeckoLib
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class TameBallEntity extends ProjectileEntity implements GeoEntity {
    private static final double GRAVITY = 0.04;
    private static final double AIR_FRICTION = 0.99;
    private static final double GROUND_FRICTION = 0.80;

    private UUID thrower;
    private UUID targetId;
    private int  state = 0; // 0=flying, 1=capture
    private int  timer = 0, shakes = 0;
    private boolean captureSuccess = false;

    // cache for renderer spin (smoothed)
    public float clientYawFace = 0f;
    public float clientRollDeg = 0f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TameBallEntity(EntityType<? extends TameBallEntity> type, World world) {
        super(type, world);
    }
    public TameBallEntity(World world, LivingEntity owner) {
        this(ModEntities.TAME_BALL, world);
        setOwner(owner);
        this.thrower = owner.getUuid();
    }

    // GeoEntity
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar ctr) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override protected void initDataTracker() {}

    @Override
    public void tick() {
        super.tick();

        if (!getWorld().isClient) {
            if (state == 1) {
                tickCapture();
                return;
            }
            // robust collision raycast
            HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
            if (hit.getType() != HitResult.Type.MISS) onCollision(hit);
        }

        // physics (both sides)
        Vec3d v = getVelocity();
        if (!isOnGround()) v = v.add(0, -GRAVITY, 0).multiply(AIR_FRICTION);
        else               v = v.multiply(GROUND_FRICTION, 1.0, GROUND_FRICTION);

        setVelocity(v);
        move(MovementType.SELF, v);

        // client-only smoothing for renderer
        if (getWorld().isClient) {
            double yawRad = Math.atan2(v.x, v.z);
            float yawDeg = (float)(yawRad * 180.0 / Math.PI);
            float dy = wrapDeg(yawDeg - clientYawFace);
            clientYawFace += dy * 0.2f;

            float speed = (float)Math.sqrt(v.x*v.x + v.z*v.z);
            clientRollDeg += speed * 720f * (1f/20f);
        }

        // lifetime while flying
        if (age > 20*12 && state == 0) discard();
    }

    private static float wrapDeg(float a) {
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (state != 0) return;
        Entity e = hit.getEntity();
        if (!(e instanceof LivingEntity le)) return;
        if (le.isPlayer()) return; // never capture players
        startCapture(le);
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.08, 0.08, 0.08, 0.01);
            sw.playSoundFromEntity(null, this, SoundEvents.BLOCK_CALCITE_HIT, SoundCategory.PLAYERS, 0.55f, 1.35f);
        }
    }

    @Override
    protected void onCollision(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) onEntityHit((EntityHitResult)result);
        else if (result.getType() == HitResult.Type.BLOCK) onBlockHit((BlockHitResult)result);
    }

    private void startCapture(LivingEntity target) {
        state = 1; timer = 0; shakes = 0;
        targetId = target.getUuid();

        if (getWorld() instanceof ServerWorld sw) {
            // bright flash at start
            sw.spawnParticles(ParticleTypes.FLASH, target.getX(), target.getY() + target.getStandingEyeHeight()*0.6, target.getZ(), 1, 0,0,0,0);
            sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.UI_TOAST_IN, SoundCategory.PLAYERS, 0.9f, 1.1f);
        }

        if (target instanceof net.minecraft.entity.mob.MobEntity mob) mob.setAiDisabled(true);
        target.setVelocity(Vec3d.ZERO); target.velocityModified = true;
        target.setNoGravity(true);

        setPosition(target.getX(), target.getY() + 0.2, target.getZ());
        setVelocity(Vec3d.ZERO);
    }

    private void tickCapture() {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        LivingEntity target = (targetId == null) ? null : (LivingEntity)((ServerWorld)getWorld()).getEntity(targetId);
        if (target == null || !target.isAlive()) { discard(); return; }

        timer++;

        // three shakes @ 0.5s apart (10 ticks)
        if (timer % 10 == 0 && shakes < 3) {
            shakes++;
            // green swirl + subtle flash per shake
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.5, getZ(), 14, 0.25, 0.25, 0.25, 0.0);
            sw.playSoundFromEntity(null, this, SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.PLAYERS, 0.55f, 1.35f);
        }

        if (shakes >= 3 && timer >= 32) {
            // success chance scales with missing HP
            float hp = target.getHealth();
            float max = Math.max(1f, target.getMaxHealth());
            double hpFactor = Math.max(0, Math.min(1, 1.0 - (hp / max)));
            double chance = 0.20 + hpFactor * 0.70; // 20%..90%

            captureSuccess = this.random.nextDouble() < chance;

            if (target instanceof net.minecraft.entity.mob.MobEntity mob) mob.setAiDisabled(false);
            target.setNoGravity(false);

            // owner (if any)
            ServerPlayerEntity sp = (getOwner() instanceof ServerPlayerEntity p) ? p : null;

            if (captureSuccess) {
                // do capture bookkeeping (NO party UI open)
                if (sp != null) {
                    TamerServerHooks.handleCapture(sp, target);
                }

                // very obvious success FX
                sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 0.6, getZ(), 64, 0.5, 0.5, 0.5, 0.01);
                sw.spawnParticles(ParticleTypes.FIREWORK, getX(), getY() + 0.6, getZ(), 32, 0.4, 0.4, 0.4, 0.02);
                sw.playSound(null, getX(), getY(), getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);

                if (sp != null) {
                    // big clear message (actionbar so it doesn't spam chat)
                    String name = target.getDisplayName().getString();
                    sp.sendMessage(Text.literal("Captured " + name + "!"), true);
                }
            } else {
                // very obvious failure FX
                sw.spawnParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 0.5, target.getZ(), 24, 0.3, 0.3, 0.3, 0.01);
                sw.spawnParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.6, target.getZ(), 10, 0.2, 0.2, 0.2, 0.0);
                sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.9f, 0.9f);
                if (sp != null) sp.sendMessage(Text.literal("Capture failed!"), true);
            }

            discard();
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // ball can’t be damaged
        return false;
    }
}
