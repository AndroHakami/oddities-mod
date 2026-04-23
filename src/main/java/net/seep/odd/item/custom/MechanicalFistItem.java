package net.seep.odd.item.custom;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.sound.ModSounds;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class MechanicalFistItem extends SwordItem implements GeoItem {
    public static final int MAX_CHARGE_TICKS = 30;          // 1.5 seconds
    private static final int MIN_RELEASE_TICKS = 6;         // small tap does nothing
    private static final int ABILITY_COOLDOWN_TICKS = 18;

    private static final float ABILITY_DAMAGE_MIN = 5.0F;
    private static final float ABILITY_DAMAGE_MAX = 8.0F;   // netherite-ish feel

    private static final double DASH_DISTANCE_MAX = 5.0D;
    private static final double DASH_VERTICAL_BOOST = 0.12D;
    private static final double CONE_LENGTH = 4.2D;
    private static final double CONE_HALF_ANGLE_DEG = 33.0D;
    private static final double CONE_BASE_RADIUS = 0.70D;
    private static final double ABILITY_KNOCKBACK_MIN = 1.00D;
    private static final double ABILITY_KNOCKBACK_MAX = 1.60D;
    private static final double ABILITY_UPWARD_KB = 0.24D;

    private static final String NBT_USE_HAND = "odd_mechanical_fist_use_hand";
    private static final String NBT_FULL_CHARGE_SOUND_DONE = "odd_mechanical_fist_charge_sound_done";
    private static final String NBT_CHARGE_SOUND_PLAYING = "odd_mechanical_fist_charge_sound_playing";

    public static final String IMPACT_FX_UNTIL_NBT = "odd_mechanical_fist_fx_until";
    public static final String IMPACT_ORIGIN_X_NBT = "odd_mechanical_fist_fx_origin_x";
    public static final String IMPACT_ORIGIN_Y_NBT = "odd_mechanical_fist_fx_origin_y";
    public static final String IMPACT_ORIGIN_Z_NBT = "odd_mechanical_fist_fx_origin_z";
    public static final String IMPACT_DIR_X_NBT = "odd_mechanical_fist_fx_dir_x";
    public static final String IMPACT_DIR_Y_NBT = "odd_mechanical_fist_fx_dir_y";
    public static final String IMPACT_DIR_Z_NBT = "odd_mechanical_fist_fx_dir_z";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final Supplier<Object> renderProvider = GeoItemClientHooks.createRenderProvider(this);

    public MechanicalFistItem(Settings settings) {
        // Iron sword damage, much faster attack speed.
        super(ToolMaterials.IRON, 3, -1.55f, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient) {
            attacker.getWorld().playSound(
                    null,
                    target.getX(), target.getY(), target.getZ(),
                    ModSounds.FIST_PUNCH,
                    SoundCategory.PLAYERS,
                    1.0f,
                    1.0f
            );

            float yawRad = attacker.getYaw() * 0.017453292F;
            target.takeKnockback(0.65F, MathHelper.sin(yawRad), -MathHelper.cos(yawRad));
        }

        return super.postHit(stack, target, attacker);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        stack.getOrCreateNbt().putInt(NBT_USE_HAND, hand.ordinal());
        stack.getOrCreateNbt().putBoolean(NBT_FULL_CHARGE_SOUND_DONE, false);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        // Keep vanilla third-person alone; first-person pullback is handled by a client mixin.
        return UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        int clamped = Math.min(usedTicks, MAX_CHARGE_TICKS);

        if (world.isClient) {
            spawnChargeSteamClient(world, user, stack, clamped);
        } else if (world instanceof ServerWorld sw) {
            var nbt = stack.getOrCreateNbt();

            if (!nbt.getBoolean(NBT_CHARGE_SOUND_PLAYING)) {
                playChargeSoundForOthers(sw, user);
                nbt.putBoolean(NBT_CHARGE_SOUND_PLAYING, true);
            }

            boolean alreadyPlayed = nbt.getBoolean(NBT_FULL_CHARGE_SOUND_DONE);
            if (!alreadyPlayed && clamped >= MAX_CHARGE_TICKS) {
                Vec3d fistPos = getFistPos(user, handFromNbt(stack), 1.0f);
                sw.playSound(
                        null,
                        fistPos.x, fistPos.y, fistPos.z,
                        ModSounds.FIST_PUNCH_STRONG_CHARGE,
                        SoundCategory.PLAYERS,
                        0.95f,
                        1.0f
                );
                nbt.putBoolean(NBT_FULL_CHARGE_SOUND_DONE, true);
            }

            spawnChargeSteam(sw, user, stack, clamped);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        super.onStoppedUsing(stack, world, user, remainingUseTicks);

        stopChargeSound(world, user, stack);
        stack.getOrCreateNbt().putBoolean(NBT_FULL_CHARGE_SOUND_DONE, false);

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        if (usedTicks < MIN_RELEASE_TICKS) {
            return;
        }
        if (world.isClient || !(world instanceof ServerWorld sw)) {
            return;
        }

        float charge01 = getCharge01(usedTicks);
        Vec3d dashDir = getDashDirection(user);

        double dashDistance = DASH_DISTANCE_MAX * charge01;
        applyDashImpulse(sw, user, dashDir, dashDistance);

        float damage = MathHelper.lerp(charge01, ABILITY_DAMAGE_MIN, ABILITY_DAMAGE_MAX);
        double knockback = MathHelper.lerp(charge01, ABILITY_KNOCKBACK_MIN, ABILITY_KNOCKBACK_MAX);

        Vec3d impactOrigin = user.getEyePos().add(dashDir.multiply(0.55D)).add(0.0D, -0.35D, 0.0D);

        sw.playSound(
                null,
                impactOrigin.x, impactOrigin.y, impactOrigin.z,
                ModSounds.FIST_PUNCH_STRONG,
                SoundCategory.PLAYERS,
                1.15f,
                0.98f + 0.05f * charge01
        );

        storeImpactFx(stack, sw, impactOrigin, dashDir);
        spawnImpactBurst(sw, impactOrigin, dashDir, charge01);

        DamageSource source = getDamageSource(sw, user);
        doPunchCone(sw, user, impactOrigin, dashDir, damage, knockback, source);

        if (user instanceof PlayerEntity player) {
            player.getItemCooldownManager().set(this, ABILITY_COOLDOWN_TICKS);
        }

        if (user instanceof ServerPlayerEntity sp) {
            stack.damage(1, sp, p -> p.sendToolBreakStatus(handFromNbt(stack)));
        } else {
            stack.damage(1, user, e -> {});
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient || !(world instanceof ServerWorld sw) || !(entity instanceof LivingEntity living)) {
            return;
        }

        if (!stack.hasNbt() || !stack.getNbt().getBoolean(NBT_CHARGE_SOUND_PLAYING)) {
            return;
        }

        boolean stillCharging = living.isUsingItem() && living.getActiveItem() == stack;
        if (!stillCharging) {
            stopChargeSound(sw, living, stack);
            stack.getOrCreateNbt().putBoolean(NBT_FULL_CHARGE_SOUND_DONE, false);
        }
    }

    private static float getCharge01(int usedTicks) {
        float t = MathHelper.clamp(usedTicks / (float) MAX_CHARGE_TICKS, 0.0f, 1.0f);
        // Bow-ish easing so the front of the charge grows quickly then settles.
        return MathHelper.clamp((t * t + t * 2.0f) / 3.0f, 0.0f, 1.0f);
    }

    private static Vec3d getDashDirection(LivingEntity user) {
        Vec3d look = user.getRotationVec(1.0f).normalize();
        return new Vec3d(look.x, MathHelper.clamp(look.y, -0.35D, 0.45D), look.z).normalize();
    }

    private static void applyDashImpulse(ServerWorld world, LivingEntity user, Vec3d dir, double maxDistance) {
        if (maxDistance <= 0.05D) return;

        double allowed = getAvailableDashDistance(world, user, dir, maxDistance);
        if (allowed <= 0.05D) return;

        float reach01 = (float) MathHelper.clamp(allowed / DASH_DISTANCE_MAX, 0.0D, 1.0D);
        double horizontalImpulse = MathHelper.lerp(reach01, 0.42D, 1.02D);
        double verticalImpulse = Math.max(DASH_VERTICAL_BOOST, dir.y * 0.22D);

        Vec3d newVel = user.getVelocity().multiply(0.25D)
                .add(dir.x * horizontalImpulse, verticalImpulse, dir.z * horizontalImpulse);

        user.setVelocity(newVel);
        user.velocityModified = true;
        user.fallDistance = 0.0f;
    }

    private static double getAvailableDashDistance(ServerWorld world, LivingEntity user, Vec3d dir, double maxDistance) {
        Vec3d start = user.getEyePos();
        Vec3d end = start.add(dir.multiply(maxDistance));

        BlockHitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                user
        ));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return maxDistance;
        }

        double dist = start.distanceTo(hit.getPos()) - 0.45D;
        return Math.max(0.0D, Math.min(maxDistance, dist));
    }

    private static void doPunchCone(ServerWorld world,
                                    LivingEntity user,
                                    Vec3d origin,
                                    Vec3d direction,
                                    float damage,
                                    double knockback,
                                    DamageSource source) {
        double angleTan = Math.tan(Math.toRadians(CONE_HALF_ANGLE_DEG));
        Box search = user.getBoundingBox().stretch(direction.multiply(CONE_LENGTH + 0.8D)).expand(3.0D);

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, search, e -> e.isAlive() && e != user)) {
            Vec3d center = target.getBoundingBox().getCenter();
            Vec3d rel = center.subtract(origin);

            double axial = rel.dotProduct(direction);
            if (axial < 0.0D || axial > CONE_LENGTH) continue;

            double radial = rel.subtract(direction.multiply(axial)).length();
            double maxRadius = CONE_BASE_RADIUS + axial * angleTan;
            if (radial > maxRadius) continue;

            target.damage(source, damage);
            target.addVelocity(
                    direction.x * knockback,
                    ABILITY_UPWARD_KB,
                    direction.z * knockback
            );
            target.velocityModified = true;
        }
    }

    private static void spawnChargeSteam(ServerWorld world, LivingEntity user, ItemStack stack, int chargeTicks) {
        if ((user.age % 4) != 0) return;

        Vec3d fist = getFistPos(user, handFromNbt(stack), 1.0f);
        int count = chargeTicks >= MAX_CHARGE_TICKS ? 2 : 1;

        for (int i = 0; i < count; i++) {
            double ox = (world.random.nextDouble() - 0.5D) * 0.10D;
            double oy = (world.random.nextDouble() - 0.5D) * 0.06D;
            double oz = (world.random.nextDouble() - 0.5D) * 0.10D;

            Vec3d spawn = fist.add(ox, oy, oz);

            world.spawnParticles(ParticleTypes.POOF,
                    spawn.x, spawn.y, spawn.z,
                    1,
                    0.0D, 0.0D, 0.0D,
                    0.0D);
        }
    }

    private static void spawnChargeSteamClient(World world, LivingEntity user, ItemStack stack, int chargeTicks) {
        if ((user.age % 3) != 0) return;

        Vec3d fist = getFistPos(user, handFromNbt(stack), 1.0f);
        int count = chargeTicks >= MAX_CHARGE_TICKS ? 2 : 1;

        for (int i = 0; i < count; i++) {
            double ox = (world.random.nextDouble() - 0.5D) * 0.12D;
            double oy = (world.random.nextDouble() - 0.5D) * 0.08D;
            double oz = (world.random.nextDouble() - 0.5D) * 0.12D;

            Vec3d spawn = fist.add(ox, oy, oz);
            Vec3d vel = fist.subtract(spawn).multiply(0.12D);

            world.addParticle(ParticleTypes.SMOKE,
                    spawn.x, spawn.y, spawn.z,
                    vel.x, vel.y, vel.z);
        }
    }

    private static void spawnImpactBurst(ServerWorld world, Vec3d origin, Vec3d direction, float charge01) {
        double spread = 0.25D + 0.15D * charge01;

        world.spawnParticles(ParticleTypes.EXPLOSION,
                origin.x + direction.x * 0.35D,
                origin.y + direction.y * 0.35D,
                origin.z + direction.z * 0.35D,
                1,
                0.0D, 0.0D, 0.0D,
                0.0D);

        world.spawnParticles(ParticleTypes.CRIT,
                origin.x, origin.y, origin.z,
                18,
                spread, spread * 0.55D, spread,
                0.25D);

        world.spawnParticles(ParticleTypes.SMOKE,
                origin.x, origin.y, origin.z,
                8,
                spread * 0.5D, spread * 0.25D, spread * 0.5D,
                0.02D);
    }

    private static void storeImpactFx(ItemStack stack, ServerWorld world, Vec3d origin, Vec3d direction) {
        stack.getOrCreateNbt().putLong(IMPACT_FX_UNTIL_NBT, world.getTime() + 8L);
        stack.getOrCreateNbt().putDouble(IMPACT_ORIGIN_X_NBT, origin.x);
        stack.getOrCreateNbt().putDouble(IMPACT_ORIGIN_Y_NBT, origin.y);
        stack.getOrCreateNbt().putDouble(IMPACT_ORIGIN_Z_NBT, origin.z);
        stack.getOrCreateNbt().putDouble(IMPACT_DIR_X_NBT, direction.x);
        stack.getOrCreateNbt().putDouble(IMPACT_DIR_Y_NBT, direction.y);
        stack.getOrCreateNbt().putDouble(IMPACT_DIR_Z_NBT, direction.z);
    }

    private static Vec3d getFistPos(LivingEntity user, Hand hand, float tickDelta) {
        Vec3d look = user.getRotationVec(tickDelta).normalize();
        Vec3d side = new Vec3d(-look.z, 0.0D, look.x);
        if (side.lengthSquared() < 1.0E-6D) {
            side = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();

        int sign = getHandSign(user, hand);
        return new Vec3d(user.getX(), user.getEyeY(), user.getZ())
                .add(side.multiply(0.38D * sign))
                .add(look.multiply(0.52D))
                .add(0.0D, -0.28D, 0.0D);
    }

    private static int getHandSign(LivingEntity user, Hand hand) {
        boolean rightMain = user.getMainArm() == Arm.RIGHT;
        if (hand == Hand.MAIN_HAND) {
            return rightMain ? 1 : -1;
        }
        return rightMain ? -1 : 1;
    }

    private static Hand handFromNbt(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt() != null) {
            int ord = stack.getNbt().getInt(NBT_USE_HAND);
            return ord == Hand.OFF_HAND.ordinal() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private static DamageSource getDamageSource(ServerWorld world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity sp) {
            return world.getDamageSources().playerAttack(sp);
        }
        return world.getDamageSources().mobAttack(user);
    }

    private static void playChargeSoundForOthers(ServerWorld world, LivingEntity user) {
        if (user instanceof PlayerEntity player) {
            world.playSoundFromEntity(player, user, ModSounds.FIST_PUNCH_STRONG_CHARGE, SoundCategory.PLAYERS, 0.90f, 1.0f);
        } else {
            world.playSoundFromEntity(null, user, ModSounds.FIST_PUNCH_STRONG_CHARGE, SoundCategory.PLAYERS, 0.90f, 1.0f);
        }
    }

    private static void stopChargeSound(World world, LivingEntity user, ItemStack stack) {
        stack.getOrCreateNbt().putBoolean(NBT_CHARGE_SOUND_PLAYING, false);

        if (!(world instanceof ServerWorld sw)) {
            return;
        }

        Identifier soundId = Registries.SOUND_EVENT.getId(ModSounds.FIST_PUNCH_STRONG_CHARGE);
        if (soundId == null) {
            return;
        }

        StopSoundS2CPacket packet = new StopSoundS2CPacket(soundId, SoundCategory.PLAYERS);
        double maxSq = 48.0D * 48.0D;

        for (ServerPlayerEntity player : sw.getPlayers()) {
            if (player.squaredDistanceTo(user) <= maxSq) {
                player.networkHandler.sendPacket(packet);
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        GeoItemClientHooks.createGeoItemRenderer(consumer,
                "net.seep.odd.item.custom.client.MechanicalFistItemRenderer");
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return renderProvider;
    }

    @Override
    public double getTick(Object animatable) {
        return RenderUtils.getCurrentTick();
    }

    private static PlayerEntity getClientPlayer() {
        return GeoItemClientHooks.getClientPlayerOrNull();
    }
}
