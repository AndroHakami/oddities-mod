package net.seep.odd.entity.ufo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.seep.odd.entity.ufo.client.AlienBombExplosionFx;
import net.seep.odd.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class AlienBombEntity extends Entity implements GeoEntity {
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final byte EXPLODE_STATUS = 61;
    private static final float EXPLOSION_POWER = 6.5f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AlienBombEntity(EntityType<? extends AlienBombEntity> type, World world) {
        super(type, world);
        this.noClip = false;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public boolean hasNoGravity() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isRemoved()) return;

        Vec3d start = this.getPos();
        Vec3d velocity = this.getVelocity().add(0.0, -0.062, 0.0).multiply(0.992);
        Vec3d end = start.add(velocity);

        BlockHitResult blockHit = this.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY,
                this
        ));

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.setPosition(blockHit.getPos());
            explode();
            return;
        }

        Box box = this.getBoundingBox().stretch(velocity).expand(0.35);
        EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                start,
                end,
                box,
                e -> e.isAlive() && e.canHit() && e != this
        );

        if (entityHit != null) {
            this.setPosition(entityHit.getPos());
            explode();
            return;
        }

        this.setPosition(end);
        this.setVelocity(velocity);

        if (this.getY() < this.getWorld().getBottomY() - 24) {
            this.discard();
        }
    }

    private void explode() {
        if (this.getWorld().isClient) return;

        this.getWorld().sendEntityStatus(this, EXPLODE_STATUS);
        this.getWorld().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                ModSounds.ALIEN_BOMB_EXPLOSION,
                SoundCategory.HOSTILE,
                1.25f,
                1.0f
        );

        // Non-destructive to blocks now.
        this.getWorld().createExplosion(
                this,
                this.getX(),
                this.getY(),
                this.getZ(),
                EXPLOSION_POWER,
                false,
                World.ExplosionSourceType.NONE
        );

        this.discard();
    }

    @Override
    public void handleStatus(byte status) {
        if (status == EXPLODE_STATUS && this.getWorld().isClient) {
            long id = (((long) this.getId()) << 32) ^ this.getWorld().getTime();
            AlienBombExplosionFx.spawn(id, this.getX(), this.getY(), this.getZ(), 10.0f, 18);
            return;
        }
        super.handleStatus(status);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "alien_bomb.controller", 0, state -> {
            state.setAndContinue(ANIM_IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}