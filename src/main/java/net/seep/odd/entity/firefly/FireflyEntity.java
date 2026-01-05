package net.seep.odd.entity.firefly;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/** Passive ambient flyer for Rotten Roots with persistent hover. */
public class FireflyEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    // Hover control
    private double targetHoverY = Double.NaN;
    private int    hoverRetargetCd = 0;

    public FireflyEntity(EntityType<? extends FireflyEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.moveControl = new FlightMoveControl(this, 8, false);
        this.setNoGravity(true);
        this.noClip = false;
    }

    /* ---------- Attributes (Bee has 10.0 HP) ---------- */
    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.30D);
    }

    /* ---------- Navigation ---------- */
    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanPathThroughDoors(false);
        nav.setCanSwim(false);
        nav.setCanEnterOpenDoors(false);
        return nav;
    }

    /* ---------- Goals ---------- */
    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new AirWanderGoal(this));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /** Idle air-wander that keeps the firefly floating around softly. */
    static final class AirWanderGoal extends Goal {
        private final FireflyEntity mob;
        private int cooldown;
        private @Nullable Vec3d target;

        AirWanderGoal(FireflyEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() { return true; }

        @Override
        public void tick() {
            if (cooldown > 0) cooldown--;

            // pick a target every few seconds
            if (target == null || mob.getPos().distanceTo(target) < 1.0 || cooldown <= 0) {
                target = pickAirTarget(mob);
                cooldown = 40 + mob.getRandom().nextInt(60);
                if (target != null) {
                    mob.getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
                    // also inform hover controller about desired altitude
                    mob.targetHoverY = target.y;
                    mob.hoverRetargetCd = 80 + mob.getRandom().nextInt(80);
                }
            }

            // face heading a bit
            mob.bodyYaw = mob.getYaw();
            mob.headYaw = mob.getYaw();
        }

        private static Vec3d pickAirTarget(FireflyEntity m) {
            final Random r = m.getRandom();
            Vec3d base = m.getPos();

            // prefer hovering 2–6 blocks above current Y (ambient “hanging” vibe)
            double dx = (r.nextDouble() - 0.5) * 12.0;
            double dy = 2.0 + r.nextDouble() * 4.0;
            double dz = (r.nextDouble() - 0.5) * 12.0;

            // nudge up if near solid ceiling
            BlockPos head = m.getBlockPos().up();
            if (!m.getWorld().getBlockState(head).isAir()) {
                dy = Math.max(dy, 3.5);
            }

            double targetY = MathHelper.clamp(base.y + dy, -16.0, 320.0);
            return new Vec3d(base.x + dx, targetY, base.z + dz);
        }
    }

    /* ---------- Hover & physics ---------- */
    @Override
    public void tick() {
        super.tick();

        // absolutely never enable gravity
        if (!this.hasNoGravity()) this.setNoGravity(true);

        // gentle altitude hold (if we don't have one, choose one around current Y)
        if (Double.isNaN(targetHoverY)) {
            targetHoverY = MathHelper.clamp(getY() + 2.5 + getRandom().nextDouble() * 3.0, -16.0, 320.0);
            hoverRetargetCd = 80 + getRandom().nextInt(80);
        } else if (--hoverRetargetCd <= 0) {
            // occasionally shift our preferred altitude a little
            targetHoverY = MathHelper.clamp(targetHoverY + (getRandom().nextDouble() - 0.5) * 4.0, -16.0, 320.0);
            hoverRetargetCd = 80 + getRandom().nextInt(80);
        }

        // If we somehow touch ground, puff upward
        if (this.isOnGround()) {
            this.addVelocity(0, 0.12, 0);
        }

        // Clamp fall speed & softly correct toward targetHoverY
        Vec3d v = this.getVelocity();
        double dy = targetHoverY - this.getY();

        // reduce any downward drift a lot
        double vy = v.y;
        if (vy < -0.02) vy *= 0.35; // heavy damping on falling

        // proportional correction toward hover height
        double corr = MathHelper.clamp(dy * 0.06, -0.04, 0.04);
        vy += corr;

        // tiny baseline upward bias so we never sink when idle
        vy += 0.0025;

        this.setVelocity(v.x * 0.98, vy, v.z * 0.98);
    }

    @Override
    protected void fall(double heightDiff, boolean onGround, net.minecraft.block.BlockState state, BlockPos pos) {
        // no landing thud logic
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, net.minecraft.entity.damage.DamageSource source) {
        return false; // no fall damage ever
    }

    @Override
    protected boolean isImmobile() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false; // don't get dragged down by fluids
    }

    /* ---------- GeckoLib ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "firefly.controller", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
