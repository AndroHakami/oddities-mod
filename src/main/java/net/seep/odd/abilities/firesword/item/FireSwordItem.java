package net.seep.odd.abilities.firesword.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FireSwordItem extends SwordItem implements GeoItem {

    public static final String SUMMONED_NBT = "OdditiesSummonedFireSword";

    // ===== Charge / slam tuning =====
    private static final int CHARGE_REQUIRED_T = 4;      // 0.2s @ 20tps
    private static final int POST_RELEASE_ANIM_T = 16;   // keep "charge" anim briefly after release
    private static final int PILLARS = 5;

    private static final float SLAM_DAMAGE = 6.0f;
    private static final int FIRE_SECONDS = 5;
    private static final double KNOCK_UP = 0.48;
    private static final double KNOCK_SIDE = 0.62;

    // Wider pillars / hit area
    private static final double PILLAR_RADIUS = 1.15; // was ~0.75
    private static final double PILLAR_HEIGHT = 2.8;

    // Sounds
    private static final float SND_VOL = 0.80f;

    // NBT keys
    private static final String NBT_USE_HAND = "odd_fs_useHand";
    private static final String NBT_READY = "odd_fs_ready";

    // ===== Gecko anims =====
    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlay("charge");

    public FireSwordItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);

        // Ensure our client tick hook is registered even if you forget to call init somewhere
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            initClientHooks();
        }
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.setOnFireFor(4);

        Hand hand = (attacker instanceof PlayerEntity p) ? p.getActiveHand() : Hand.MAIN_HAND;
        if (hand == null) hand = Hand.MAIN_HAND;

        Hand finalHand = hand;
        stack.damage(1, attacker, e -> e.sendToolBreakStatus(finalHand));
        return true;
    }

    /* ============================================================
     * Right-click: vanilla "using item" charge (reliable)
     * ============================================================ */

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // Mark which hand started the charge (for correct durability break status)
        stack.getOrCreateNbt().putInt(NBT_USE_HAND, hand.ordinal());
        stack.getOrCreateNbt().putBoolean(NBT_READY, false);

        // Small "start charging" cue
        world.playSound(
                null,
                user.getX(), user.getY(), user.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS,
                0.55f,
                1.05f
        );

        // Start using so holding RMB is “charging”
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        // don't do vanilla bow visuals; Gecko handles visuals
        return UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;

        // Once we cross the 0.2s threshold, play a "ready" cue ONCE.
        if (usedTicks >= CHARGE_REQUIRED_T) {
            var nbt = stack.getOrCreateNbt();
            if (!nbt.getBoolean(NBT_READY)) {
                nbt.putBoolean(NBT_READY, true);

                world.playSound(
                        null,
                        user.getX(), user.getY(), user.getZ(),
                        SoundEvents.BLOCK_LAVA_POP,
                        SoundCategory.PLAYERS,
                        0.55f,
                        1.55f
                );
            }

            // Tiny charge ember aura (purely visual; safe to do both sides)
            if (world.isClient && (user.age & 1) == 0) {
                Vec3d v = user.getRotationVec(1.0f).normalize();
                double px = user.getX() + v.x * 0.55;
                double py = user.getEyeY() - 0.25 + v.y * 0.15;
                double pz = user.getZ() + v.z * 0.55;
                world.addParticle(ParticleTypes.FLAME, px, py, pz, 0, 0.01, 0);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        super.onStoppedUsing(stack, world, user, remainingUseTicks);

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;

        // Released too early: cancel
        if (usedTicks < CHARGE_REQUIRED_T) {
            // reset ready marker so next charge can play cues again
            stack.getOrCreateNbt().putBoolean(NBT_READY, false);
            return;
        }

        // Client: start post-release animation timer only
        if (world.isClient) {
            if (user instanceof PlayerEntity) {
                Hand hand = handFromNbt(stack);
                if (hand == Hand.MAIN_HAND) slamMainTicks = POST_RELEASE_ANIM_T;
                else slamOffTicks = POST_RELEASE_ANIM_T;
            }
            stack.getOrCreateNbt().putBoolean(NBT_READY, false);
            return;
        }

        // Server: do slam + durability
        if (!(world instanceof ServerWorld sw)) return;

        Hand hand = handFromNbt(stack);

        // Big slam cue (once)
        sw.playSound(
                null,
                user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.PLAYERS,
                SND_VOL,
                0.85f
        );

        // durability cost 3
        if (user instanceof ServerPlayerEntity sp) {
            stack.damage(3, sp, e -> e.sendToolBreakStatus(hand));
            doSlam(sw, sp);
        } else {
            stack.damage(3, user, e -> {});
            doSlam(sw, user);
        }

        stack.getOrCreateNbt().putBoolean(NBT_READY, false);
    }

    private static Hand handFromNbt(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt() != null) {
            int ord = stack.getNbt().getInt(NBT_USE_HAND);
            return ord == Hand.OFF_HAND.ordinal() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }
        return Hand.MAIN_HAND;
    }

    /* ============================================================
     * Slam logic: 5 flame pillars forward (wider + nicer erupt)
     * ============================================================ */

    private static void doSlam(ServerWorld sw, LivingEntity user) {
        Vec3d look = user.getRotationVec(1.0f);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z);
        if (dir.lengthSquared() < 1.0E-6) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();

        // perpendicular (for width offsets)
        Vec3d right = new Vec3d(-dir.z, 0.0, dir.x).normalize();

        DamageSource dmg;
        if (user instanceof ServerPlayerEntity sp) dmg = sw.getDamageSources().playerAttack(sp);
        else dmg = sw.getDamageSources().mobAttack(user);

        // small "ground rupture" cue (subtle)
        sw.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 0.45f, 1.25f);

        for (int i = 1; i <= PILLARS; i++) {
            Vec3d target = user.getPos().add(dir.multiply(i));

            // raycast down so it erupts from the ground
            Vec3d start = target.add(0, 1.5, 0);
            Vec3d end   = target.add(0, -6.0, 0);

            BlockHitResult hit = sw.raycast(new RaycastContext(
                    start, end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    user
            ));

            if (hit == null || hit.getType() != HitResult.Type.BLOCK) continue;

            Vec3d hp = hit.getPos();
            double x = hp.x;
            double y = hp.y + 0.05;
            double z = hp.z;

            // --- Nicer "erupt" (fire-only): base burst + rising column + side flickers ---
            // Base burst (wide)
            sw.spawnParticles(ParticleTypes.FLAME, x, y + 0.05, z, 70, 0.55, 0.10, 0.55, 0.05);

            // Rising column (layered)
            sw.spawnParticles(ParticleTypes.FLAME, x, y + 0.55, z, 45, 0.35, 0.25, 0.35, 0.03);
            sw.spawnParticles(ParticleTypes.FLAME, x, y + 1.20, z, 32, 0.30, 0.25, 0.30, 0.02);
            sw.spawnParticles(ParticleTypes.FLAME, x, y + 1.85, z, 22, 0.25, 0.25, 0.25, 0.015);

            // Side flickers to make it feel wider
            Vec3d lOff = right.multiply(-0.55);
            Vec3d rOff = right.multiply( 0.55);
            sw.spawnParticles(ParticleTypes.FLAME, x + lOff.x, y + 0.25, z + lOff.z, 25, 0.18, 0.20, 0.18, 0.02);
            sw.spawnParticles(ParticleTypes.FLAME, x + rOff.x, y + 0.25, z + rOff.z, 25, 0.18, 0.20, 0.18, 0.02);

            // Affect entities around the pillar (wider)
            Box box = new Box(
                    x - PILLAR_RADIUS, y,
                    z - PILLAR_RADIUS,
                    x + PILLAR_RADIUS, y + PILLAR_HEIGHT,
                    z + PILLAR_RADIUS
            );

            for (Entity e : sw.getOtherEntities(user, box, ent -> ent instanceof LivingEntity le && le.isAlive() && ent != user)) {
                LivingEntity le = (LivingEntity) e;

                le.damage(dmg, SLAM_DAMAGE);
                le.setOnFireFor(FIRE_SECONDS);

                Vec3d away = le.getPos().subtract(x, le.getY(), z);
                Vec3d push = (away.lengthSquared() < 1.0E-6)
                        ? new Vec3d(0, 0, 0)
                        : away.normalize().multiply(KNOCK_SIDE);

                le.addVelocity(push.x, KNOCK_UP, push.z);
                le.velocityModified = true;
            }
        }
    }

    /* ============================================================
     * GeckoLib “Gamble-style” animation logic (client timers)
     * ============================================================ */

    @Environment(EnvType.CLIENT) private static int slamMainTicks = 0;
    @Environment(EnvType.CLIENT) private static int slamOffTicks  = 0;
    @Environment(EnvType.CLIENT) private static boolean hooksRegistered = false;

    @Environment(EnvType.CLIENT)
    private static void initClientHooks() {
        if (hooksRegistered) return;
        hooksRegistered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (slamMainTicks > 0) slamMainTicks--;
            if (slamOffTicks  > 0) slamOffTicks--;
        });
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Stable renderer supplier pattern (client has it; server gets harmless supplier).
     */
    private final Supplier<Object> renderProvider =
            FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                    ? GeoItem.makeRenderer(this)
                    : () -> null;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrs) {
        ctrs.add(new AnimationController<>(this, "main", state -> {
            PlayerEntity p;
            var e = state.getData(DataTickets.ENTITY);
            if (e instanceof PlayerEntity pe) p = pe;
            else p = getClientPlayer();

            boolean playCharge = false;

            if (p != null && p.getWorld().isClient) {
                boolean main = p.getMainHandStack().isOf(this);
                boolean off  = p.getOffHandStack().isOf(this);

                boolean usingThis =
                        p.isUsingItem() &&
                                p.getActiveItem() != null &&
                                p.getActiveItem().isOf(this);

                if (main) {
                    playCharge = (usingThis && p.getActiveHand() == Hand.MAIN_HAND) || (slamMainTicks > 0);
                } else if (off) {
                    playCharge = (usingThis && p.getActiveHand() == Hand.OFF_HAND) || (slamOffTicks > 0);
                }
            }

            if (playCharge) state.setAndContinue(CHARGE);
            else state.setAndContinue(IDLE);

            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public double getTick(Object animatable) { return RenderUtils.getCurrentTick(); }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            initClientHooks();
        }

        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;
            @Override public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null)
                    renderer = new net.seep.odd.abilities.firesword.client.FireSwordItemRenderer();
                return renderer;
            }
        });
    }

    @Environment(EnvType.CLIENT)
    private static PlayerEntity getClientPlayer() {
        var mc = MinecraftClient.getInstance();
        return mc != null ? mc.player : null;
    }
}
