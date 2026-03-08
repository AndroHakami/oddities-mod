// FILE: src/main/java/net/seep/odd/abilities/sniper/item/SniperItem.java
package net.seep.odd.abilities.sniper.item;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.SniperPower;
import net.seep.odd.abilities.sniper.client.SniperClientState;
import net.seep.odd.abilities.sniper.client.SniperGlideClient;
import net.seep.odd.abilities.sniper.client.SniperScopeFx;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

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

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SniperItem extends Item implements GeoItem {

    // ✅ durability
    private static final int MAX_DURABILITY = 256;

    public SniperItem(Settings s) {
        super(s.maxCount(1).maxDamage(MAX_DURABILITY));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /* ===================== Power gate ===================== */

    private static boolean canUseSniper(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return true; // client: server authoritative
        if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) return false;
        return SniperPower.hasSniper(sp);
    }

    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();
    private static void warnOncePerSec(ServerPlayerEntity sp, String msg) {
        long now = sp.getWorld().getTime();
        long next = WARN_UNTIL.getOrDefault(sp.getUuid(), 0L);
        if (now < next) return;
        WARN_UNTIL.put(sp.getUuid(), now + 20);
        sp.sendMessage(Text.literal(msg), true);
    }

    /* ===================== Tunables ===================== */

    private static final double RANGE = 260.0;
    private static final float  BASE_DAMAGE = 8.0f;
    private static final double HIT_RADIUS = 0.30;

    private static final int FIRE_COOLDOWN_T = 20; // 2s @ 20 TPS
    private static final String NBT_LAST_FIRE_T = "sniper_lastFire";

    private static final float UNSCOPED_SPREAD_DEG = 0.85f;

    private static final int ZOOM_IN_TICKS  = 2;
    private static final int ZOOM_OUT_TICKS = 2;
    private static final int FIRE_ANIM_TICKS = 7;

    private static final Identifier PKT_HIT_CONFIRM = new Identifier(Oddities.MOD_ID, "sniper/hit_confirm"); // S->C
    private static final Identifier PKT_TRACER      = new Identifier(Oddities.MOD_ID, "sniper/tracer");      // S->C
    private static final int TRACER_HOLD_T = 16; // 0.8s

    /* ---------- Networking ---------- */
    private static final Identifier PKT_FIRE = new Identifier(Oddities.MOD_ID, "sniper/fire"); // C->S

    static {
        ServerPlayNetworking.registerGlobalReceiver(PKT_FIRE, (server, player, handler, buf, response) -> {
            final int handOrd = buf.readVarInt();
            final boolean scoped = buf.isReadable() ? buf.readBoolean() : true;
            server.execute(() -> {
                ServerPlayerEntity sp = player;

                if (!canUseSniper(sp)) {
                    warnOncePerSec(sp, "§cYou can't use that.");
                    sp.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
                    return;
                }

                Hand hand = (handOrd == Hand.OFF_HAND.ordinal()) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = sp.getStackInHand(hand);
                if (stack.getItem() instanceof SniperItem item) item.tryFire(sp, stack, hand, scoped);
            });
        });

        AttackBlockCallback.EVENT.register((p, world, hand, pos, dir) -> {
            ItemStack s = p.getStackInHand(hand);
            if (!(s.getItem() instanceof SniperItem)) return ActionResult.PASS;
            if (!canUseSniper(p)) return ActionResult.PASS;
            return ActionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((p, world, hand, target, hit) -> {
            ItemStack s = p.getStackInHand(hand);
            if (!(s.getItem() instanceof SniperItem)) return ActionResult.PASS;
            if (!canUseSniper(p)) return ActionResult.PASS;
            return ActionResult.FAIL;
        });
    }

    /* ===================== Client state ===================== */

    @Environment(EnvType.CLIENT) private static boolean lastAttackDown = false;
    @Environment(EnvType.CLIENT) private static boolean lastUseDown = false;

    @Environment(EnvType.CLIENT) private static int fireMainTicks = 0;
    @Environment(EnvType.CLIENT) private static int zoomInMainTicks = 0;
    @Environment(EnvType.CLIENT) private static int zoomOutMainTicks = 0;

    @Environment(EnvType.CLIENT) private static int clientFireCooldown = 0;
    @Environment(EnvType.CLIENT) private static boolean initedClient = false;
    @Environment(EnvType.CLIENT) private static boolean s2cRegistered = false;

    @Environment(EnvType.CLIENT) private static boolean forcedHud = false;
    @Environment(EnvType.CLIENT) private static boolean prevHudHidden = false;

    @Environment(EnvType.CLIENT)
    private static void setHudHiddenForced(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        if (on) {
            if (!forcedHud) {
                prevHudHidden = mc.options.hudHidden;
                forcedHud = true;
                mc.options.hudHidden = true;
            }
        } else {
            if (forcedHud) {
                mc.options.hudHidden = prevHudHidden;
                forcedHud = false;
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static void initClientHooks() {
        if (initedClient) return;
        initedClient = true;

        SniperClientState.init();
        SniperScopeFx.init();
        SniperGlideClient.initOnce();
        TracerFx.initOnce();

        if (!s2cRegistered) {
            s2cRegistered = true;
            ClientPlayNetworking.registerGlobalReceiver(PKT_HIT_CONFIRM, (client, handler, buf, response) -> {
                client.execute(() -> {
                    if (client.player == null) return;
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

            if (!holding) {
                lastAttackDown = false;
                lastUseDown = false;
                SniperClientState.setScopedTarget(false);
                setHudHiddenForced(false);
                return;
            }

            if (client.player.hasStatusEffect(ModStatusEffects.POWERLESS)) {
                lastAttackDown = false;
                lastUseDown = false;
                SniperClientState.setScopedTarget(false);
                setHudHiddenForced(false);
                return;
            }

            boolean useNow = client.options.useKey.isPressed() && client.currentScreen == null;
            SniperClientState.setScopedTarget(useNow);
            setHudHiddenForced(useNow);

            boolean useEdgeOn  = useNow && !lastUseDown;
            boolean useEdgeOff = !useNow && lastUseDown;
            lastUseDown = useNow;

            if (useEdgeOn) zoomInMainTicks = ZOOM_IN_TICKS;
            else if (useEdgeOff) zoomOutMainTicks = ZOOM_OUT_TICKS;

            trySendGrappleInput(client);

            boolean atkNow = client.options.attackKey.isPressed() && client.currentScreen == null;
            boolean atkEdge = atkNow && !lastAttackDown;
            lastAttackDown = atkNow;

            if (atkEdge && clientFireCooldown <= 0) {
                clientFireCooldown = FIRE_COOLDOWN_T;
                fireMainTicks = FIRE_ANIM_TICKS;

                Hand hand = (client.player.getMainHandStack().getItem() instanceof SniperItem) ? Hand.MAIN_HAND : Hand.OFF_HAND;

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(hand.ordinal());
                buf.writeBoolean(useNow); // scoped?
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

    /* ===================== Server: fire ===================== */

    private void tryFire(ServerPlayerEntity sp, ItemStack stack, Hand hand, boolean scoped) {
        if (!canUseSniper(sp)) return;

        long now = sp.getWorld().getTime();
        long last = stack.getOrCreateNbt().getLong(NBT_LAST_FIRE_T);
        if (now - last < FIRE_COOLDOWN_T) return;
        stack.getOrCreateNbt().putLong(NBT_LAST_FIRE_T, now);

        fire(sp, stack, scoped);

        // ✅ ALWAYS consume durability per shot (hit or miss)
        damageOnShot(sp, stack, hand);
    }

    private static void damageOnShot(ServerPlayerEntity sp, ItemStack stack, Hand hand) {
        if (!stack.isDamageable()) return;
        if (sp.getAbilities().creativeMode) return;
        stack.damage(1, sp, pl -> pl.sendToolBreakStatus(hand));
    }

    private void fire(ServerPlayerEntity sp, ItemStack stack, boolean scoped) {
        if (!(sp.getWorld() instanceof ServerWorld sw)) return;

        sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                ModSounds.SNIPER_SHOT, SoundCategory.PLAYERS, 1.0f, 1.0f);

        Vec3d eye = sp.getEyePos();
        Vec3d look = getShotDir(sw.random, sp, scoped).normalize();
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

            if (didDamage) {
                PacketByteBuf out = PacketByteBufs.create();
                ServerPlayNetworking.send(sp, PKT_HIT_CONFIRM, out);
            }

            sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.7f, 1.5f);
            sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                    ModSounds.SNIPER_LAND, SoundCategory.PLAYERS, 1.2f, 1.0f);

            sw.spawnParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 10, 0.12, 0.12, 0.12, 0.02);
        } else {
            sw.playSound(null, impact.x, impact.y, impact.z,
                    SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.6f, 1.7f);

            sw.spawnParticles(ParticleTypes.SMOKE, impact.x, impact.y, impact.z, 6, 0.06, 0.06, 0.06, 0.01);
        }

        sendTracer(sw, sp, eye, impact);
    }

    private static Vec3d getShotDir(Random rand, ServerPlayerEntity sp, boolean scoped) {
        float yaw = sp.getYaw();
        float pitch = sp.getPitch();

        if (!scoped) {
            float dy = (float) (rand.nextGaussian() * UNSCOPED_SPREAD_DEG);
            float dp = (float) (rand.nextGaussian() * UNSCOPED_SPREAD_DEG);
            yaw += dy;
            pitch += dp;
            pitch = MathHelper.clamp(pitch, -89.9f, 89.9f);
        }

        return Vec3d.fromPolar(pitch, yaw);
    }

    private static void sendTracer(ServerWorld sw, ServerPlayerEntity shooter, Vec3d from, Vec3d to) {
        HashSet<ServerPlayerEntity> targets = new HashSet<>();
        for (ServerPlayerEntity sp : PlayerLookup.tracking(shooter)) targets.add(sp);
        targets.add(shooter);

        for (ServerPlayerEntity sp : targets) {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeDouble(from.x); out.writeDouble(from.y); out.writeDouble(from.z);
            out.writeDouble(to.x);   out.writeDouble(to.y);   out.writeDouble(to.z);
            out.writeVarInt(TRACER_HOLD_T);
            ServerPlayNetworking.send(sp, PKT_TRACER, out);
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

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    /* ===================== Tracer FX (client) ===================== */

    @Environment(EnvType.CLIENT)
    private static final class TracerFx {
        private static boolean inited = false;

        private static final java.util.ArrayList<Tracer> TRACERS = new java.util.ArrayList<>();
        private static final Identifier WHITE_TEX = new Identifier("minecraft", "textures/misc/white.png");

        private static final int COL_R = 255, COL_G = 45, COL_B = 45;
        private static final float BASE_ALPHA = 0.70f;
        private static final int FADE_T = 14;
        private static final double HALF_WIDTH = 0.070;

        private record Tracer(Vec3d a, Vec3d b, long startTick, int holdTicks, long endTick) {}

        static void initOnce() {
            if (inited) return;
            inited = true;

            ClientPlayNetworking.registerGlobalReceiver(PKT_TRACER, (client, handler, buf, response) -> {
                double ax = buf.readDouble();
                double ay = buf.readDouble();
                double az = buf.readDouble();
                double bx = buf.readDouble();
                double by = buf.readDouble();
                double bz = buf.readDouble();
                int hold = buf.readVarInt();

                client.execute(() -> {
                    if (client.world == null) return;
                    long now = client.world.getTime();
                    int holdTicks = Math.max(1, hold);
                    long end = now + holdTicks + FADE_T;
                    TRACERS.add(new Tracer(new Vec3d(ax, ay, az), new Vec3d(bx, by, bz), now, holdTicks, end));
                });
            });

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.world == null) { TRACERS.clear(); return; }
                long now = client.world.getTime();
                TRACERS.removeIf(t -> now > t.endTick + 2);
            });

            WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
                var mc = MinecraftClient.getInstance();
                if (mc == null || mc.world == null || TRACERS.isEmpty()) return;

                float nowF = mc.world.getTime() + ctx.tickDelta();
                Vec3d camPos = ctx.camera().getPos();

                var matrices = ctx.matrixStack();
                matrices.push();
                matrices.translate(-camPos.x, -camPos.y, -camPos.z);

                var entry = matrices.peek();
                var posMat = entry.getPositionMatrix();

                VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(WHITE_TEX));

                for (Tracer t : TRACERS) {
                    float age = nowF - t.startTick;
                    if (age < 0f) age = 0f;

                    float alphaMul;
                    if (age <= t.holdTicks) alphaMul = 1f;
                    else {
                        float f = (age - t.holdTicks) / (float) FADE_T;
                        alphaMul = 1f - MathHelper.clamp(f, 0f, 1f);
                    }

                    int a = MathHelper.clamp(Math.round(255f * BASE_ALPHA * alphaMul), 0, 255);
                    if (a <= 0) continue;

                    Vec3d A = t.a;
                    Vec3d B = t.b;

                    Vec3d line = B.subtract(A);
                    if (line.lengthSquared() < 1.0e-6) continue;
                    Vec3d lineDir = line.normalize();

                    Vec3d mid = A.add(B).multiply(0.5);
                    Vec3d viewDir = camPos.subtract(mid);
                    if (viewDir.lengthSquared() < 1.0e-6) viewDir = new Vec3d(0, 1, 0);
                    else viewDir = viewDir.normalize();

                    Vec3d right = lineDir.crossProduct(viewDir);
                    if (right.lengthSquared() < 1.0e-6) right = lineDir.crossProduct(new Vec3d(0, 1, 0));
                    if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
                    else right = right.normalize();

                    Vec3d off = right.multiply(HALF_WIDTH);

                    Vec3d p0 = A.add(off);
                    Vec3d p1 = A.subtract(off);
                    Vec3d p2 = B.subtract(off);
                    Vec3d p3 = B.add(off);

                    v(vc, posMat, p0, 0f, 0f, a);
                    v(vc, posMat, p1, 1f, 0f, a);
                    v(vc, posMat, p2, 1f, 1f, a);

                    v(vc, posMat, p0, 0f, 0f, a);
                    v(vc, posMat, p2, 1f, 1f, a);
                    v(vc, posMat, p3, 0f, 1f, a);
                }

                matrices.pop();
            });
        }

        private static void v(VertexConsumer vc, org.joml.Matrix4f mat, Vec3d p, float u, float v, int alpha) {
            vc.vertex(mat, (float)p.x, (float)p.y, (float)p.z)
                    .color(COL_R, COL_G, COL_B, alpha)
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(0f, 1f, 0f)
                    .next();
        }
    }

    /* ===================== GeckoLib ===================== */

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

            if (playFire) state.setAndContinue(FIRE);
            else if (playZoomOut) state.setAndContinue(ZOOM_OUT);
            else if (playZoomIn) state.setAndContinue(ZOOM_IN);
            else state.setAndContinue(IDLE);

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