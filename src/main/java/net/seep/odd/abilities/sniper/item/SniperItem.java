// FILE: src/main/java/net/seep/odd/abilities/sniper/item/SniperItem.java
package net.seep.odd.abilities.sniper.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
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

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.SniperPower;
import net.seep.odd.abilities.sniper.client.SniperClientState;
import net.seep.odd.abilities.sniper.client.SniperGlideClient;
import net.seep.odd.abilities.sniper.client.SniperScopeFx;

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

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SniperItem extends Item implements GeoItem {

    public SniperItem(Settings s) {
        super(s.maxCount(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /* ===== Tunables ===== */
    private static final double RANGE = 260.0;
    private static final float  BASE_DAMAGE = 8.0f;
    private static final double HIT_RADIUS = 0.30;

    private static final int FIRE_COOLDOWN_T = 20; // 2s @ 20 TPS
    private static final String NBT_LAST_FIRE_T = "sniper_lastFire";

    // Zoom anim lengths: 0.1s = 2 ticks
    private static final int ZOOM_IN_TICKS  = 2;
    private static final int ZOOM_OUT_TICKS = 2;
    private static final int FIRE_ANIM_TICKS = 7;
    private static final Identifier PKT_HIT_CONFIRM = new Identifier(Oddities.MOD_ID, "sniper/hit_confirm"); // S->C


    /* ---------- Networking ---------- */
    private static final Identifier PKT_FIRE = new Identifier(Oddities.MOD_ID, "sniper/fire"); // C->S

    static {
        ServerPlayNetworking.registerGlobalReceiver(PKT_FIRE, (server, player, handler, buf, response) -> {
            final int handOrd = buf.readVarInt();
            server.execute(() -> {
                ServerPlayerEntity sp = player;
                Hand hand = (handOrd == Hand.OFF_HAND.ordinal()) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = sp.getStackInHand(hand);
                if (stack.getItem() instanceof SniperItem item) item.tryFire(sp, stack);
            });
        });

        AttackBlockCallback.EVENT.register((p, world, hand, pos, dir) -> {
            ItemStack s = p.getStackInHand(hand);
            return s.getItem() instanceof SniperItem ? ActionResult.FAIL : ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((p, world, hand, target, hit) -> {
            ItemStack s = p.getStackInHand(hand);
            return s.getItem() instanceof SniperItem ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    /* ---------- Client state ---------- */
    @Environment(EnvType.CLIENT) private static boolean lastAttackDown = false;
    @Environment(EnvType.CLIENT) private static boolean lastUseDown = false;

    @Environment(EnvType.CLIENT) private static int fireMainTicks = 0;
    @Environment(EnvType.CLIENT) private static int zoomInMainTicks = 0;
    @Environment(EnvType.CLIENT) private static int zoomOutMainTicks = 0;

    @Environment(EnvType.CLIENT) private static int clientFireCooldown = 0;
    @Environment(EnvType.CLIENT) private static boolean initedClient = false;
    @Environment(EnvType.CLIENT) private static boolean s2cRegistered = false;


    @Environment(EnvType.CLIENT)
    public static void initClientHooks() {
        if (initedClient) return;
        initedClient = true;

        SniperClientState.init();
        SniperScopeFx.init();
        SniperGlideClient.initOnce();
        if (!s2cRegistered) {
            s2cRegistered = true;
            ClientPlayNetworking.registerGlobalReceiver(PKT_HIT_CONFIRM, (client, handler, buf, response) -> {
                client.execute(() -> {
                    if (client.player == null) return;
                    // any temp sound you want:
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f, 1.8f);
                });
            });
        }


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (clientFireCooldown > 0) clientFireCooldown--;
            if (fireMainTicks > 0) fireMainTicks--;
            if (zoomInMainTicks > 0) zoomInMainTicks--;
            if (zoomOutMainTicks > 0) zoomOutMainTicks--;

            boolean holding = client.player.getMainHandStack().getItem() instanceof SniperItem
                    || client.player.getOffHandStack().getItem() instanceof SniperItem;

            // If you stop holding, smoothly un-scope
            if (!holding) {
                lastAttackDown = false;
                lastUseDown = false;
                SniperClientState.setScopedTarget(false);
                return;
            }

            // ---- Scope target (hold RMB) ----
            boolean useNow = client.options.useKey.isPressed() && client.currentScreen == null;

            // Smooth camera zoom comes from SniperClientState.scopeAmount(tickDelta) via mixin.
            SniperClientState.setScopedTarget(useNow);

            // Edges only for playing animations
            boolean useEdgeOn  = useNow && !lastUseDown;
            boolean useEdgeOff = !useNow && lastUseDown;
            lastUseDown = useNow;

            if (useEdgeOn) {
                zoomInMainTicks = ZOOM_IN_TICKS;
            } else if (useEdgeOff) {
                zoomOutMainTicks = ZOOM_OUT_TICKS;
            }

            // ---- Grapple cancel input (Space) to server ----
            trySendGrappleInput(client);

            // ---- Fire (LMB edge) ----
            boolean atkNow = client.options.attackKey.isPressed() && client.currentScreen == null;
            boolean atkEdge = atkNow && !lastAttackDown;
            lastAttackDown = atkNow;

            if (atkEdge && clientFireCooldown <= 0) {
                clientFireCooldown = FIRE_COOLDOWN_T;
                fireMainTicks = FIRE_ANIM_TICKS;

                Hand hand = (client.player.getMainHandStack().getItem() instanceof SniperItem) ? Hand.MAIN_HAND : Hand.OFF_HAND;

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(hand.ordinal());
                ClientPlayNetworking.send(PKT_FIRE, buf);

                if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            }
        });
    }

    @Environment(EnvType.CLIENT)
    private static byte lastSentGrappleFlags = (byte)0x7F;

    @Environment(EnvType.CLIENT)
    private static void trySendGrappleInput(MinecraftClient client) {
        if (client.player == null) return;

        byte flags = 0;
        if (client.currentScreen == null && client.options.jumpKey.isPressed()) {
            flags |= SniperPower.IN_JUMP;
        }

        if (flags == lastSentGrappleFlags) return;
        lastSentGrappleFlags = flags;

        PacketByteBuf out = PacketByteBufs.create();
        out.writeByte(flags);
        ClientPlayNetworking.send(SniperPower.SNIPER_CTRL_C2S, out);
    }

    /* ---------- Server: fire ---------- */
    private void tryFire(ServerPlayerEntity sp, ItemStack stack) {
        long now = sp.getWorld().getTime();
        long last = stack.getOrCreateNbt().getLong(NBT_LAST_FIRE_T);
        if (now - last < FIRE_COOLDOWN_T) return;
        stack.getOrCreateNbt().putLong(NBT_LAST_FIRE_T, now);

        fire(sp, stack);
    }

    private void fire(ServerPlayerEntity sp, ItemStack stack) {
        if (!(sp.getWorld() instanceof ServerWorld sw)) return;

        sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.35f, 2.0f);

        Vec3d eye = sp.getEyePos();
        Vec3d look = sp.getRotationVec(1f).normalize();
        Vec3d end = eye.add(look.multiply(RANGE));

        BlockHitResult bhr = sw.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, sp));
        double maxHit = (bhr.getType() == HitResult.Type.MISS) ? RANGE : eye.distanceTo(bhr.getPos());
        Vec3d stop = eye.add(look.multiply(maxHit));

        LivingEntity target = findFirstHitEntity(sw, sp, eye, stop, HIT_RADIUS);
        Vec3d impact = (target != null) ? target.getBoundingBox().getCenter() : stop;

        if (target != null && target.isAlive()) {
            float dmg = BASE_DAMAGE + stack.getOrCreateNbt().getFloat("sniper_bonusDmg");
            DamageSource src = sw.getDamageSources().playerAttack(sp);
            boolean didDamage = target.damage(src, dmg);

// ✅ hitmarker only for shooter (no one else hears it)
            if (didDamage) {
                PacketByteBuf out = PacketByteBufs.create();
                ServerPlayNetworking.send(sp, PKT_HIT_CONFIRM, out);
            }


            sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.7f, 1.5f);

            sw.spawnParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 10, 0.12, 0.12, 0.12, 0.02);
        } else {
            sw.playSound(null, impact.x, impact.y, impact.z,
                    SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.6f, 1.7f);

            sw.spawnParticles(ParticleTypes.SMOKE, impact.x, impact.y, impact.z, 6, 0.06, 0.06, 0.06, 0.01);
        }

        spawnTracer(sw, eye, impact);
    }

    private static void spawnTracer(ServerWorld sw, Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double len = d.length();
        if (len < 0.01) return;

        Vec3d step = d.normalize().multiply(2.0);
        int n = (int)Math.ceil(len / 2.0);
        Vec3d p = from;
        for (int i = 0; i < n; i++) {
            p = p.add(step);
            sw.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static LivingEntity findFirstHitEntity(ServerWorld sw, PlayerEntity owner, Vec3d start, Vec3d end, double radius) {
        Box sweep = new Box(start, end).expand(radius);
        double bestDist2 = Double.POSITIVE_INFINITY;
        LivingEntity best = null;

        for (Entity e : sw.getOtherEntities(owner, sweep, ent -> ent instanceof LivingEntity le && le.isAlive() && ent != owner)) {
            LivingEntity le = (LivingEntity) e;
            Box box = le.getBoundingBox().expand(radius);
            Optional<Vec3d> hit = box.raycast(start, end);
            if (hit.isPresent()) {
                double d2 = hit.get().squaredDistanceTo(start);
                if (d2 < bestDist2) {
                    bestDist2 = d2;
                    best = le;
                }
            }
        }
        return best;
    }

    /* ---------- IMPORTANT: do NOT consume RMB (prevents use-hand loop) ---------- */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    private static final RawAnimation IDLE     = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ZOOM_IN  = RawAnimation.begin().thenPlay("zoom_in");
    private static final RawAnimation ZOOM_OUT = RawAnimation.begin().thenPlay("zoom_out");
    private static final RawAnimation FIRE     = RawAnimation.begin().thenPlay("fire");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrs) {
        ctrs.add(new AnimationController<>(this, "main", state -> {
            PlayerEntity p = null;
            Object e = state.getData(DataTickets.ENTITY);
            if (e instanceof PlayerEntity pe) p = pe; else p = getClientPlayer();

            boolean playFire = false;
            boolean playZoomIn = false;
            boolean playZoomOut = false;

            if (p != null && p.getWorld().isClient) {
                boolean main = p.getMainHandStack().isOf(this);
                if (main) {
                    playFire = fireMainTicks > 0;
                    playZoomIn = zoomInMainTicks > 0;
                    playZoomOut = zoomOutMainTicks > 0;
                }
            }

            if (playFire) {
                state.setAndContinue(FIRE);
            } else if (playZoomOut) {
                state.setAndContinue(ZOOM_OUT);
            } else if (playZoomIn) {
                state.setAndContinue(ZOOM_IN);
            } else {
                // While scoped, we don't loop zoom_in (prevents “repeating zoom_in”).
                // Once fully scoped, the first-person model will be hidden anyway (via HeldItemRenderer mixin).
                state.setAndContinue(IDLE);
            }

            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public double getTick(Object animatable) { return RenderUtils.getCurrentTick(); }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;
            @Override public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null) renderer = new net.seep.odd.abilities.sniper.item.client.SniperRenderer();
                return renderer;
            }
        });
    }

    @Environment(EnvType.CLIENT)
    private static PlayerEntity getClientPlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null ? mc.player : null;
    }
}
