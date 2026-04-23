package net.seep.odd.item.custom;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.sound.ModSounds;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuitarItem extends SwordItem implements GeoItem {

    // =========================
    // Base melee stats
    // Diamond sword equivalent:
    // material = diamond, attack damage bonus = 3, attack speed = -2.4
    // =========================

    // =========================
    // Smash tuning
    // =========================
    private static final int CHARGE_REQUIRED_TICKS = 10;   // 0.5 seconds
    private static final int COOLDOWN_TICKS = 40;          // 2 seconds

    private static final double SMASH_RANGE = 6.0D;
    private static final double AOE_RADIUS = 3.25D;

    private static final float DIRECT_HIT_DAMAGE = 9.0F;   // slightly above netherite sword feel
    private static final float AOE_DAMAGE = 5.0F;

    private static final double DIRECT_KNOCKBACK = 1.00D;
    private static final double AOE_KNOCKBACK = 0.80D;
    private static final double KNOCK_UP = 0.42D;

    private static final int DIRECT_SLOW_TICKS = 40;       // 2 sec
    private static final int DIRECT_SLOW_AMP = 3;          // Slowness IV

    // =========================
    // Normal hit pitch-combo tuning
    // =========================
    private static final long HIT_CHAIN_WINDOW_TICKS = 20L; // 1 second
    private static final int MAX_HIT_CHAIN = 5;
    private static final float BASE_HIT_PITCH = 1.00F;
    private static final float HIT_PITCH_STEP = 0.10F;

    private static final Map<UUID, Long> LAST_HIT_TICK = new HashMap<>();
    private static final Map<UUID, Integer> HIT_CHAIN = new HashMap<>();

    // =========================
    // NBT
    // =========================
    private static final String NBT_USE_HAND = "odd_guitar_use_hand";

    // =========================
    // Gecko anims
    // =========================
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final Supplier<Object> renderProvider = GeoItemClientHooks.createRenderProvider(this);

    public GuitarItem(Settings settings) {
        super(ToolMaterials.DIAMOND, 3, -2.4f, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // =========================
    // Normal melee hit
    // =========================
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient) {
            long now = attacker.getWorld().getTime();
            UUID id = attacker.getUuid();

            long last = LAST_HIT_TICK.getOrDefault(id, -9999L);
            int chain = (now - last <= HIT_CHAIN_WINDOW_TICKS)
                    ? Math.min(MAX_HIT_CHAIN, HIT_CHAIN.getOrDefault(id, 0) + 1)
                    : 0;

            LAST_HIT_TICK.put(id, now);
            HIT_CHAIN.put(id, chain);

            float pitch = BASE_HIT_PITCH + (chain * HIT_PITCH_STEP);

            attacker.getWorld().playSound(
                    null,
                    target.getX(), target.getY(), target.getZ(),
                    ModSounds.GUITAR_HIT,
                    SoundCategory.PLAYERS,
                    1.0f,
                    pitch
            );
        }

        return super.postHit(stack, target, attacker);
    }

    // =========================
    // Right-click charge
    // =========================
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        stack.getOrCreateNbt().putInt(NBT_USE_HAND, hand.ordinal());
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        // Gives the trident/spear hold animation in first + third person
        return UseAction.SPEAR;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        super.onStoppedUsing(stack, world, user, remainingUseTicks);

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        if (usedTicks < CHARGE_REQUIRED_TICKS) return;
        if (world.isClient) return;
        if (!(world instanceof ServerWorld sw)) return;

        LivingEntity directTarget = findDirectTarget(sw, user, SMASH_RANGE);
        Vec3d impactPos = resolveImpactPos(sw, user, directTarget);

        sw.playSound(
                null,
                impactPos.x, impactPos.y, impactPos.z,
                ModSounds.GUITAR_SMASH,
                SoundCategory.PLAYERS,
                1.1f,
                1.0f
        );

        spawnSmashParticles(sw, impactPos);

        DamageSource source = getDamageSource(sw, user);

        if (directTarget != null && directTarget.isAlive()) {
            directTarget.damage(source, DIRECT_HIT_DAMAGE);
            directTarget.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    DIRECT_SLOW_TICKS,
                    DIRECT_SLOW_AMP
            ));

            applyKnockbackFromPoint(directTarget, impactPos, DIRECT_KNOCKBACK, KNOCK_UP, user.getRotationVec(1.0f));
        }

        doShockwave(sw, user, impactPos, directTarget, source);

        if (user instanceof PlayerEntity player) {
            player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        }

        if (user instanceof ServerPlayerEntity sp) {
            stack.damage(2, sp, p -> p.sendToolBreakStatus(handFromNbt(stack)));
        } else {
            stack.damage(2, user, e -> {});
        }
    }

    // =========================
    // Smash logic
    // =========================
    private static LivingEntity findDirectTarget(ServerWorld world, LivingEntity user, double range) {
        Vec3d eyePos = new Vec3d(user.getX(), user.getEyeY(), user.getZ());
        Vec3d look = user.getRotationVec(1.0f).normalize();
        Vec3d end = eyePos.add(look.multiply(range));

        Box searchBox = user.getBoundingBox().stretch(look.multiply(range)).expand(1.25D);

        EntityHitResult hit = ProjectileUtil.getEntityCollision(
                world,
                user,
                eyePos,
                end,
                searchBox,
                entity -> entity instanceof LivingEntity le && le.isAlive() && entity != user
        );

        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            return le;
        }

        return null;
    }

    private static Vec3d resolveImpactPos(ServerWorld world, LivingEntity user, LivingEntity directTarget) {
        if (directTarget != null) {
            return findGroundBelow(world, directTarget.getPos().add(0.0, 1.5, 0.0), user);
        }

        Vec3d eyePos = new Vec3d(user.getX(), user.getEyeY(), user.getZ());
        Vec3d look = user.getRotationVec(1.0f).normalize();
        Vec3d forwardEnd = eyePos.add(look.multiply(SMASH_RANGE));

        BlockHitResult forwardHit = world.raycast(new RaycastContext(
                eyePos,
                forwardEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                user
        ));

        if (forwardHit.getType() == HitResult.Type.BLOCK) {
            return forwardHit.getPos().add(0.0, 0.08, 0.0);
        }

        Vec3d ahead = user.getPos().add(look.x * 3.0, 2.5, look.z * 3.0);
        return findGroundBelow(world, ahead, user);
    }

    private static Vec3d findGroundBelow(ServerWorld world, Vec3d start, Entity user) {
        Vec3d end = start.add(0.0, -8.0, 0.0);

        BlockHitResult downHit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                user
        ));

        if (downHit.getType() == HitResult.Type.BLOCK) {
            return downHit.getPos().add(0.0, 0.08, 0.0);
        }

        return start;
    }

    private static void doShockwave(ServerWorld world, LivingEntity user, Vec3d impactPos, LivingEntity directTarget, DamageSource source) {
        Box area = new Box(
                impactPos.x - AOE_RADIUS, impactPos.y - 1.5, impactPos.z - AOE_RADIUS,
                impactPos.x + AOE_RADIUS, impactPos.y + 2.5, impactPos.z + AOE_RADIUS
        );

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e.isAlive() && e != user)) {
            double dist = target.getPos().distanceTo(impactPos);
            if (dist > AOE_RADIUS + 0.6D) continue;

            if (target != directTarget) {
                target.damage(source, AOE_DAMAGE);
            }

            double strength = AOE_KNOCKBACK * (1.0D - MathHelper.clamp(dist / AOE_RADIUS, 0.0D, 1.0D));
            strength = Math.max(0.22D, strength);

            applyKnockbackFromPoint(target, impactPos, strength, KNOCK_UP, user.getRotationVec(1.0f));
        }
    }

    private static void applyKnockbackFromPoint(LivingEntity target, Vec3d origin, double horizontalStrength, double upwardStrength, Vec3d fallbackDir) {
        Vec3d away = target.getPos().subtract(origin.x, target.getY(), origin.z);
        Vec3d flat = new Vec3d(away.x, 0.0, away.z);

        if (flat.lengthSquared() < 1.0E-6) {
            flat = new Vec3d(fallbackDir.x, 0.0, fallbackDir.z);
        }

        if (flat.lengthSquared() < 1.0E-6) {
            flat = new Vec3d(0.0, 0.0, 1.0);
        }

        flat = flat.normalize().multiply(horizontalStrength);
        target.addVelocity(flat.x, upwardStrength, flat.z);
        target.velocityModified = true;
    }

    private static DamageSource getDamageSource(ServerWorld world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity sp) {
            return world.getDamageSources().playerAttack(sp);
        }
        return world.getDamageSources().mobAttack(user);
    }

    private static void spawnSmashParticles(ServerWorld world, Vec3d impactPos) {
        world.spawnParticles(ParticleTypes.EXPLOSION, impactPos.x, impactPos.y + 0.1, impactPos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.CLOUD, impactPos.x, impactPos.y + 0.1, impactPos.z, 26, 0.75, 0.12, 0.75, 0.03);
        world.spawnParticles(ParticleTypes.CRIT, impactPos.x, impactPos.y + 0.25, impactPos.z, 22, 0.55, 0.18, 0.55, 0.06);
    }

    private static Hand handFromNbt(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt() != null) {
            int ord = stack.getNbt().getInt(NBT_USE_HAND);
            return ord == Hand.OFF_HAND.ordinal() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }
        return Hand.MAIN_HAND;
    }

    // =========================
    // GeckoLib
    // =========================
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
                "net.seep.odd.item.custom.client.GuitarItemRenderer");
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