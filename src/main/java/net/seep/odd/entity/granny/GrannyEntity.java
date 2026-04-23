package net.seep.odd.entity.granny;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.GameEventTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.EntityPositionSource;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import net.minecraft.world.event.Vibrations;
import net.minecraft.world.event.listener.EntityGameEventHandler;
import net.seep.odd.expeditions.atheneum.granny.GrannyEventManager;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;
import java.util.function.BiConsumer;

public final class GrannyEntity extends PathAwareEntity implements GeoEntity, Vibrations {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    private static final int ALERT_TICKS = 20 * 12;
    private static final int HEAR_RANGE = 96;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final Vibrations.ListenerData vibrationData = new Vibrations.ListenerData();
    private final Vibrations.Callback vibrationCallback = new GrannyVibrationCallback();
    private final Vibrations.VibrationListener vibrationListener = new Vibrations.VibrationListener(this);
    private final EntityGameEventHandler<Vibrations.VibrationListener> vibrationHandler =
            new EntityGameEventHandler<>(this.vibrationListener);

    private UUID alertedTarget;
    private int alertTicks;
    private int wanderCooldown;
    private int ambientCooldown;

    public GrannyEntity(EntityType<? extends GrannyEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) return;
        if (!GrannyEventManager.isAtheneum(this.getWorld())) {
            this.discard();
            return;
        }
        if (!GrannyEventManager.isEventRunning()) {
            this.discard();
            return;
        }

        Vibrations.Ticker.tick(this.getWorld(), this.vibrationData, this.vibrationCallback);

        ServerPlayerEntity target = getAlertedTarget();
        if (target != null) {
            this.getLookControl().lookAt(target, 30.0f, 30.0f);
            this.getNavigation().startMovingTo(target, 1.68D);

            if (this.squaredDistanceTo(target) <= 2.9D) {
                GrannyEventManager.catchPlayer(this, target);
                clearAlert();
            }

            if (--alertTicks <= 0 || !target.isAlive() || target.isSpectator() || !GrannyEventManager.isAtheneum(target.getWorld())) {
                clearAlert();
            }
        } else {
            if (wanderCooldown-- <= 0) {
                wanderCooldown = 30 + this.random.nextInt(40);
                Vec3d here = this.getPos();
                double dx = (this.random.nextDouble() - 0.5D) * 18.0D;
                double dz = (this.random.nextDouble() - 0.5D) * 18.0D;
                this.getNavigation().startMovingTo(here.x + dx, here.y, here.z + dz, 0.55D);
            }

            PlayerEntity tooClose = this.getWorld().getClosestPlayer(this, 4.1D);
            if (tooClose instanceof ServerPlayerEntity sp && GrannyEventManager.isAtheneum(sp.getWorld())) {
                triggerAlert(sp);
            }
        }

        if (ambientCooldown-- <= 0) {
            ambientCooldown = 90 + this.random.nextInt(80);
            GrannyEventManager.playGrannyAmbient(this);
        }
    }

    @Override
    public void updateEventHandler(BiConsumer<EntityGameEventHandler<?>, ServerWorld> callback) {
        super.updateEventHandler(callback);
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            callback.accept(this.vibrationHandler, serverWorld);
        }
    }

    private void triggerAlert(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        if (!GrannyEventManager.isAtheneum(player.getWorld())) return;

        boolean changedTarget = this.alertedTarget == null || !this.alertedTarget.equals(player.getUuid());
        this.alertedTarget = player.getUuid();
        this.alertTicks = ALERT_TICKS;

        if (changedTarget) {
            GrannyEventManager.onGrannyAlert(this, player);
        }
    }

    private void clearAlert() {
        this.alertedTarget = null;
        this.alertTicks = 0;
        this.getNavigation().stop();
    }

    private ServerPlayerEntity getAlertedTarget() {
        if (this.alertedTarget == null || !(this.getWorld() instanceof ServerWorld sw)) {
            return null;
        }
        return sw.getServer().getPlayerManager().getPlayer(this.alertedTarget);
    }

    private @Nullable ServerPlayerEntity resolvePlayerFromVibration(@Nullable Entity sourceEntity, @Nullable Entity entity, BlockPos pos) {
        if (sourceEntity instanceof ServerPlayerEntity player && player.isAlive() && !player.isSpectator()) {
            return player;
        }
        if (entity instanceof ServerPlayerEntity player && player.isAlive() && !player.isSpectator()) {
            return player;
        }
        if (!(this.getWorld() instanceof ServerWorld sw)) {
            return null;
        }
        return (ServerPlayerEntity) sw.getClosestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 2.5D, p -> p.isAlive() && !p.isSpectator());
    }

    @Override
    public Vibrations.ListenerData getVibrationListenerData() {
        return this.vibrationData;
    }

    @Override
    public Vibrations.Callback getVibrationCallback() {
        return this.vibrationCallback;
    }

    private final class GrannyVibrationCallback implements Vibrations.Callback {
        private final PositionSource positionSource = new EntityPositionSource(GrannyEntity.this, GrannyEntity.this.getHeight() * 0.75F);

        @Override
        public int getRange() {
            return HEAR_RANGE;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public net.minecraft.registry.tag.TagKey<GameEvent> getTag() {
            return GameEventTags.WARDEN_CAN_LISTEN;
        }

        @Override
        public boolean triggersAvoidCriterion() {
            return true;
        }

        @Override
        public boolean accepts(ServerWorld world, BlockPos pos, GameEvent event, GameEvent.Emitter emitter) {
            if (!GrannyEventManager.isEventRunning()) return false;
            if (!GrannyEventManager.isAtheneum(world)) return false;
            if (GrannyEntity.this.isRemoved()) return false;

            Entity sourceEntity = emitter.sourceEntity();
            if (sourceEntity == GrannyEntity.this) return false;
            if (sourceEntity instanceof GrannyEntity) return false;

            ServerPlayerEntity heardPlayer = resolvePlayerFromVibration(sourceEntity, null, pos);
            if (heardPlayer == null) return false;
            if (!GrannyEventManager.isAtheneum(heardPlayer.getWorld())) return false;

            ServerPlayerEntity current = getAlertedTarget();
            if (current != null && current.isAlive() && !current.getUuid().equals(heardPlayer.getUuid())) {
                return false;
            }
            return true;
        }

        @Override
        public void accept(ServerWorld world, BlockPos pos, GameEvent event, @Nullable Entity sourceEntity, @Nullable Entity entity, float distance) {
            ServerPlayerEntity heardPlayer = resolvePlayerFromVibration(sourceEntity, entity, pos);
            if (heardPlayer == null) return;
            triggerAlert(heardPlayer);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "granny.controller", 0, state -> {
            if (state.isMoving()) {
                state.setAndContinue(WALK);
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

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.alertedTarget != null) {
            nbt.putUuid("AlertedTarget", this.alertedTarget);
        }
        nbt.putInt("AlertTicks", this.alertTicks);
        nbt.putInt("WanderCooldown", this.wanderCooldown);
        nbt.putInt("AmbientCooldown", this.ambientCooldown);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("AlertedTarget")) {
            this.alertedTarget = nbt.getUuid("AlertedTarget");
        }
        this.alertTicks = nbt.getInt("AlertTicks");
        this.wanderCooldown = nbt.getInt("WanderCooldown");
        this.ambientCooldown = nbt.getInt("AmbientCooldown");
    }
}
