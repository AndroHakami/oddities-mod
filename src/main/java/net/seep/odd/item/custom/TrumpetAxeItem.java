// FILE: src/main/java/net/seep/odd/item/custom/TrumpetAxeItem.java
package net.seep.odd.item.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TrumpetAxeItem extends AxeItem implements GeoItem {
    public static final String BLOW_POSE_UNTIL_NBT = "TrumpetBlowPoseUntil";

    private static final int COOLDOWN_TICKS = 2 * 20;
    private static final int HOLD_TICKS = 12;

    private static final double RANGE = 6.0D;
    private static final double HALF_ANGLE_DOT = 0.8660254D; // ~60 degree cone total

    private static final float SONIC_DAMAGE = 3.0F;
    private static final double SONIC_KNOCKBACK = 0.35D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItemClientHooks.createRenderProvider(this);

    public TrumpetAxeItem(Settings settings) {
        super(ToolMaterials.DIAMOND, 5.0F, -3.0F, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public boolean isPerspectiveAware() {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!tryUseTrumpet(world, user, hand, stack)) {
            return TypedActionResult.pass(stack);
        }

        return TypedActionResult.consume(stack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity user = context.getPlayer();

        if (user == null) {
            return ActionResult.PASS;
        }

        return tryUseTrumpet(context.getWorld(), user, context.getHand(), context.getStack())
                ? ActionResult.success(context.getWorld().isClient)
                : ActionResult.PASS;
    }

    private boolean tryUseTrumpet(World world, PlayerEntity user, Hand hand, ItemStack stack) {
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return false;
        }

        setBlowPoseUntil(stack, world.getTime() + HOLD_TICKS);
        user.setCurrentHand(hand);

        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            applyConeDebuff(serverWorld, user);
            spawnNoteParticles(serverWorld, user);

            world.playSound(
                    null,
                    user.getX(), user.getY(), user.getZ(),
                    ModSounds.TRUMPET_AXE,
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.0F
            );

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        }

        return true;
    }

    private void applyConeDebuff(ServerWorld world, PlayerEntity user) {
        Vec3d eyePos = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0F).normalize();
        Box hitBox = user.getBoundingBox().expand(RANGE);

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, hitBox,
                entity -> entity.isAlive() && entity != user)) {

            Vec3d toTarget = target.getEyePos().subtract(eyePos);
            double distance = toTarget.length();

            if (distance <= 0.001D || distance > RANGE) {
                continue;
            }

            if (!user.canSee(target)) {
                continue;
            }

            Vec3d dir = toTarget.normalize();
            double dot = look.dotProduct(dir);

            if (dot < HALF_ANGLE_DOT) {
                continue;
            }

            target.damage(world.getDamageSources().playerAttack(user), SONIC_DAMAGE);
            target.takeKnockback(
                    SONIC_KNOCKBACK,
                    user.getX() - target.getX(),
                    user.getZ() - target.getZ()
            );

            // Slowness 10 for 2 seconds -> amplifier 9, 40 ticks
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 9));
            // Weakness 2 for 3 seconds -> amplifier 1, 60 ticks
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
        }
    }

    private void spawnNoteParticles(ServerWorld world, PlayerEntity user) {
        Vec3d origin = user.getEyePos().add(user.getRotationVec(1.0F).multiply(0.65D));
        Vec3d forward = user.getRotationVec(1.0F).normalize();

        Vec3d side = forward.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (side.lengthSquared() < 1.0E-4D) {
            side = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();

        Vec3d up = side.crossProduct(forward).normalize();

        for (int i = 0; i < 12; i++) {
            double dist = 0.75D + (i * 0.33D);
            double spread = 0.05D + dist * 0.055D;

            double sideAmount = (world.random.nextDouble() - 0.5D) * spread * 2.0D;
            double upAmount = (world.random.nextDouble() - 0.5D) * spread * 1.2D;

            Vec3d pos = origin
                    .add(forward.multiply(dist))
                    .add(side.multiply(sideAmount))
                    .add(up.multiply(upAmount));

            double colorSeed = world.random.nextDouble();

            world.spawnParticles(
                    ParticleTypes.NOTE,
                    pos.x, pos.y, pos.z,
                    1,
                    colorSeed, 0.0D, 0.0D,
                    1.0D
            );
        }
    }

    public static void setBlowPoseUntil(ItemStack stack, long gameTime) {
        stack.getOrCreateNbt().putLong(BLOW_POSE_UNTIL_NBT, gameTime);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return HOLD_TICKS;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main_controller", 0, state -> {
            state.setAnimation(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        GeoItemClientHooks.createBuiltinItemRenderer(consumer,
                "net.seep.odd.item.custom.client.TrumpetAxeRenderer");
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return this.renderProvider;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}