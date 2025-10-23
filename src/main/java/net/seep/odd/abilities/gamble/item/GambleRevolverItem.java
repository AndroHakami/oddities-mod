package net.seep.odd.abilities.gamble.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
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
import net.minecraft.world.explosion.Explosion;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.gamble.GambleMode;
import net.seep.odd.abilities.power.GamblePower;

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

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GambleRevolverItem extends Item implements GeoItem {

    public GambleRevolverItem(Settings s) {
        super(s.maxCount(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /* ===== Tunables ===== */
    private static final int    MAG_SIZE            = 6;
    private static final double RANGE               = 36.0;
    private static final float  BASE_DAMAGE         = 5.0f;
    private static final float  BIG_DAMAGE          = 18.0f;      // 1/10 jackpot
    private static final float  BUFF_HEAL           = 3.0f;
    private static final float  DEBUFF_PING         = 2.0f;

    private static final int    RELOAD_STEP_T       = 5;          // 0.25s per bullet (20 tps)
    private static final float  HEARTS_PER_BULLET   = 1.0f;       // costs 1 heart => 2.0 health

    private static final int    FIRE_COOLDOWN_T     = 4;          // 0.2s
    private static final String NBT_LAST_FIRE_T     = "gamble_lastFire";

    // Forgiving hit capsule
    private static final double HIT_RADIUS          = 0.85;

    // GeckoLib animation lengths (seconds * 20tps)
    private static final int FIRE_ANIM_TICKS   = 7;   // ~0.36s
    private static final int RELOAD_ANIM_TICKS = 35;  // ~1.72s

    // Small heart scale (if your custom heart particles read "speed" as scale)
    private static final float HEART_SCALE_SMALL = 0.60f; // 40% smaller

    /* ---------- Networking ---------- */
    private static final Identifier PKT_FIRE        = new Identifier("odd", "gamble/fire");         // C->S
    private static final Identifier PKT_RELOAD      = new Identifier("odd", "gamble/reload");       // C->S (kept for completeness)
    private static final Identifier PKT_RELOAD_ANIM = new Identifier("odd", "gamble/reload_anim");  // S->C (play animation)

    static {
        // Server receivers (gameplay)
        ServerPlayNetworking.registerGlobalReceiver(PKT_FIRE, (server, player, handler, buf, response) -> {
            final int handOrd = buf.readVarInt();
            server.execute(() -> {
                ServerPlayerEntity sp = player;
                Hand hand = (handOrd == Hand.OFF_HAND.ordinal()) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = sp.getStackInHand(hand);
                if (stack.getItem() instanceof GambleRevolverItem item) item.tryFire(sp, stack, hand);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PKT_RELOAD, (server, player, handler, buf, response) -> {
            final int handOrd = buf.readVarInt();
            server.execute(() -> {
                ServerPlayerEntity sp = player;
                Hand hand = (handOrd == Hand.OFF_HAND.ordinal()) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = sp.getStackInHand(hand);
                if (stack.getItem() instanceof GambleRevolverItem item) item.requestReload(sp, stack, hand);
            });
        });

        // Cancel vanilla melee/mining while holding
        AttackBlockCallback.EVENT.register((player2, world, hand, pos, dir) -> {
            ItemStack s = player2.getStackInHand(hand);
            return s.getItem() instanceof GambleRevolverItem ? ActionResult.FAIL : ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player2, world, hand, target, hit) -> {
            ItemStack s = player2.getStackInHand(hand);
            return s.getItem() instanceof GambleRevolverItem ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    /* ---------- Client: LMB edge + 0.2s throttle ---------- */
    @Environment(EnvType.CLIENT) private static boolean lastAttackDown = false;
    @Environment(EnvType.CLIENT) private static int fireMainTicks   = 0;
    @Environment(EnvType.CLIENT) private static int fireOffTicks    = 0;
    @Environment(EnvType.CLIENT) private static int reloadMainTicks = 0;
    @Environment(EnvType.CLIENT) private static int reloadOffTicks  = 0;
    @Environment(EnvType.CLIENT) private static int clientCooldown  = 0;
    @Environment(EnvType.CLIENT) private static boolean s2cRegistered = false;

    @Environment(EnvType.CLIENT)
    public static void initClientHooks() {
        if (!s2cRegistered) {
            s2cRegistered = true;
            ClientPlayNetworking.registerGlobalReceiver(PKT_RELOAD_ANIM, (client, handler, buf, response) -> {
                final int handOrd = buf.readVarInt();
                client.execute(() -> {
                    if (handOrd == Hand.MAIN_HAND.ordinal()) reloadMainTicks = RELOAD_ANIM_TICKS;
                    else reloadOffTicks = RELOAD_ANIM_TICKS;
                });
            });
        }

        // Register HUD once
        Hud.initOnce();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (fireMainTicks   > 0) fireMainTicks--;
            if (fireOffTicks    > 0) fireOffTicks--;
            if (reloadMainTicks > 0) reloadMainTicks--;
            if (reloadOffTicks  > 0) reloadOffTicks--;
            if (clientCooldown  > 0) clientCooldown--;

            boolean holding = client.player.getMainHandStack().getItem() instanceof GambleRevolverItem
                    || client.player.getOffHandStack().getItem()  instanceof GambleRevolverItem;
            if (!holding) { lastAttackDown = false; return; }

            boolean now = client.options.attackKey.isPressed();
            boolean edge = now && !lastAttackDown;
            lastAttackDown = now;

            if (edge && clientCooldown <= 0) {
                clientCooldown = FIRE_COOLDOWN_T;

                Hand hand = client.player.getMainHandStack().getItem() instanceof GambleRevolverItem ? Hand.MAIN_HAND : Hand.OFF_HAND;

                // local fire prediction for animation
                if (hand == Hand.MAIN_HAND) fireMainTicks = FIRE_ANIM_TICKS; else fireOffTicks = FIRE_ANIM_TICKS;

                // send fire to server
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(hand.ordinal());
                ClientPlayNetworking.send(PKT_FIRE, buf);

                if (client.interactionManager != null) client.interactionManager.cancelBlockBreaking();
            }
        });
    }

    /* ---------- No vanilla “use” ---------- */
    @Override public int getMaxUseTime(ItemStack stack) { return 0; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    /* ---------- Server tick: reload adds bullets ---------- */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity sp)) return;

        if (isReloading(stack)) {
            int t = stack.getOrCreateNbt().getInt("gamble_reloadT") + 1;
            stack.getOrCreateNbt().putInt("gamble_reloadT", t);

            if (t % RELOAD_STEP_T == 0) {
                if (getAmmo(stack) >= MAG_SIZE) { stopReload(sp, stack); return; }
                if (sp.getHealth() > HEARTS_PER_BULLET * 2.0f) {
                    sp.damage(world.getDamageSources().generic(), HEARTS_PER_BULLET * 2.0f);
                    setAmmo(stack, getAmmo(stack) + 1);
                    world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                            SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 0.35f, 1.6f);
                } else {
                    stopReload(sp, stack);
                }
            }
        }
    }

    /* ---------- Fire helpers (server) ---------- */
    private void tryFire(ServerPlayerEntity sp, ItemStack stack, Hand hand) {
        if (isReloading(stack)) return;

        // 0.2s fire rate
        long now = sp.getWorld().getTime();
        long last = stack.getOrCreateNbt().getLong(NBT_LAST_FIRE_T);
        if (now - last < FIRE_COOLDOWN_T) return;
        stack.getOrCreateNbt().putLong(NBT_LAST_FIRE_T, now);

        if (getAmmo(stack) <= 0) {
            sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 0.9f);
            return;
        }
        fire(sp, stack, hand);
    }

    private void fire(ServerPlayerEntity sp, ItemStack stack, Hand hand) {
        setAmmo(stack, getAmmo(stack) - 1);

        // SFX
        sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.2f);

        GambleMode mode = GamblePower.getMode(sp);
        ServerWorld sw = (ServerWorld) sp.getWorld();
        Random r = sw.random;

        Vec3d eye  = sp.getEyePos();
        Vec3d look = sp.getRotationVec(1f).normalize();
        Vec3d end  = eye.add(look.multiply(RANGE));

        // stop on blocks
        BlockHitResult bhr = sw.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, sp));
        double maxHit = (bhr.getType() == HitResult.Type.MISS) ? RANGE : eye.distanceTo(bhr.getPos());
        Vec3d stop = eye.add(look.multiply(maxHit));

        // forgiving entity hit
        LivingEntity target = findFirstHitEntity(sw, sp, eye, stop, HIT_RADIUS);

        // decide effect & trail tint
        boolean bigShot = false;
        HeartTint tint;

        if (target != null && target.isAlive()) {
            switch (mode) {
                case DEBUFF -> {
                    target.damage(sw.getDamageSources().playerAttack(sp), DEBUFF_PING);
                    tint = applyRandomDebuff(r, sw, sp, target);   // returns tint used
                }
                case BUFF -> {
                    target.heal(BUFF_HEAL);
                    tint = applyRandomBuff(r, sw, sp, target);
                }
                default -> { // SHOOT
                    bigShot = (r.nextInt(10) == 0);
                    float dmg = bigShot ? BIG_DAMAGE : BASE_DAMAGE;
                    DamageSource src = sw.getDamageSources().playerAttack(sp);
                    target.damage(src, dmg);

                    if (bigShot) {
                        Explosion ex = new Explosion(sw, sp, null, null,
                                target.getX(), target.getY(), target.getZ(),
                                2.6f, false, Explosion.DestructionType.KEEP);
                        ex.collectBlocksAndDamageEntities();
                        ex.affectWorld(true);

                        Vec3d recoil = look.multiply(-0.6);
                        sp.addVelocity(recoil.x, recoil.y * 0.15, recoil.z);
                        sp.velocityModified = true;

                        sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.9f, 1.4f);
                    }
                    tint = bigShot ? HeartTint.GOLD : HeartTint.RED;
                }
            }
        } else {
            // no living hit: still color by mode/bigShot for visuals
            if (mode == GambleMode.SHOOT) {
                bigShot = (r.nextInt(10) == 0);
                if (bigShot) {
                    Explosion ex = new Explosion(sw, sp, null, null,
                            stop.x, stop.y, stop.z,
                            2.2f, false, Explosion.DestructionType.KEEP);
                    ex.collectBlocksAndDamageEntities();
                    ex.affectWorld(true);
                    Vec3d recoil = look.multiply(-0.55);
                    sp.addVelocity(recoil.x, recoil.y * 0.12, recoil.z);
                    sp.velocityModified = true;
                    sw.playSound(null, stop.x, stop.y, stop.z,
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8f, 1.5f);
                }
            }
            tint = switch (mode) {
                case BUFF   -> HeartTint.PINK;
                case DEBUFF -> HeartTint.PURPLE;
                default     -> (bigShot ? HeartTint.GOLD : HeartTint.RED);
            };
        }

        Vec3d impact = (target != null && target.isAlive()) ? target.getBoundingBox().getCenter() : stop;
        spawnHeartTrail(sw, eye, impact, tint);
    }

    /** Capsule ray vs expanded AABB; picks first hit along the segment. */
    private static LivingEntity findFirstHitEntity(ServerWorld sw, PlayerEntity owner, Vec3d start, Vec3d end, double radius) {
        Box sweep = new Box(start, end).expand(radius);
        double bestDist2 = Double.POSITIVE_INFINITY;
        LivingEntity best = null;

        for (Entity e : sw.getOtherEntities(owner, sweep, ent -> ent instanceof LivingEntity le && ent.isAlive() && ent != owner)) {
            LivingEntity le = (LivingEntity) e;
            Box box = le.getBoundingBox().expand(radius);
            Optional<Vec3d> hit = box.raycast(start, end);
            if (hit.isPresent()) {
                double d2 = hit.get().squaredDistanceTo(start);
                if (d2 < bestDist2) { bestDist2 = d2; best = le; }
            }
        }
        return best;
    }

    /* ---------- Colored heart trails (no smoke) ---------- */
    private static void spawnHeartTrail(ServerWorld sw, Vec3d from, Vec3d to, HeartTint tint) {
        ParticleEffect heart = heartEffect(tint); // custom colored heart if present; else vanilla HEART
        Vec3d d = to.subtract(from);
        double len = d.length();
        if (len < 0.01) return;

        Vec3d step = d.normalize().multiply(0.6);
        int n = (int)Math.ceil(len / 0.6);
        Vec3d p = from;

        for (int i = 0; i < n; i++) {
            p = p.add(step);
            sw.spawnParticles(heart, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, HEART_SCALE_SMALL);
        }
    }

    /* ---------- Buff/Debuff pools -> also return HeartTint for the trail ---------- */

    private static HeartTint applyRandomBuff(Random r, ServerWorld sw, ServerPlayerEntity shooter, LivingEntity target) {
        int pick = r.nextInt(8);  // 4 requested + 4 extra
        switch (pick) {
            case 0 -> { // Speed V for 6s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20*6, 4, true, true, true), shooter);
                return HeartTint.TEAL;
            }
            case 1 -> { // Regen + Resistance for 10s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20*10, 1, true, true, true), shooter);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,   20*10, 0, true, true, true), shooter);
                return HeartTint.PINK; // light pink for regen
            }
            case 2 -> { // Swap places with the target
                swapPositions(sw, shooter, target);
                return HeartTint.PURPLE; // swap = purple
            }
            case 3 -> { // Haste 5s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20*5, 2, true, true, true), shooter);
                return HeartTint.ORANGE;
            }
            case 4 -> { // Strength II 8s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20*8, 1, true, true, true), shooter);
                return HeartTint.RED;
            }
            case 5 -> { // Fire Resistance 10s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20*10, 0, true, true, true), shooter);
                return HeartTint.GOLD;
            }
            case 6 -> { // Absorption IV 10s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20*10, 3, true, true, true), shooter);
                return HeartTint.GOLD;
            }
            default -> { // Luck 15s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 20*15, 0, true, true, true), shooter);
                return HeartTint.LIME;
            }
        }
    }

    private static HeartTint applyRandomDebuff(Random r, ServerWorld sw, ServerPlayerEntity shooter, LivingEntity target) {
        int pick = r.nextInt(8);  // 5 requested + 3 extra
        switch (pick) {
            case 0 -> { // Glowing 15s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20*15, 0, true, true, true), shooter);
                return HeartTint.WHITE;
            }
            case 1 -> { // Levitation 3s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20*3, 0, true, true, true), shooter);
                return HeartTint.BLUE;
            }
            case 2 -> { // Super knockback
                Vec3d away = target.getPos().subtract(shooter.getPos()).normalize();
                target.addVelocity(away.x * 1.4, 0.55, away.z * 1.4);
                target.velocityModified = true;
                return HeartTint.ORANGE;
            }
            case 3 -> { // Teleport you to the target
                teleportPlayerToEntity(sw, shooter, target);
                return HeartTint.PURPLE;
            }
            case 4 -> { // Strong slowness 5s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20*5, 3, true, true, true), shooter);
                return HeartTint.BLUE;
            }
            case 5 -> { // Blindness 8s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 20*8, 0, true, true, true), shooter);
                return HeartTint.WHITE;
            }
            case 6 -> { // Weakness 8s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20*8, 1, true, true, true), shooter);
                return HeartTint.RED;
            }
            default -> { // Nausea 6s
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20*6, 0, true, true, true), shooter);
                return HeartTint.LIME;
            }
        }
    }

    private static void swapPositions(ServerWorld sw, ServerPlayerEntity a, LivingEntity b) {
        Vec3d pa = a.getPos();
        Vec3d pb = b.getPos();
        a.teleport(sw, pb.x, pb.y, pb.z, a.getYaw(), a.getPitch());
        if (b instanceof ServerPlayerEntity spb) {
            spb.teleport(sw, pa.x, pa.y, pa.z, spb.getYaw(), spb.getPitch());
        } else {
            b.refreshPositionAndAngles(pa.x, pa.y, pa.z, b.getYaw(), b.getPitch());
        }
        sw.playSound(null, pb.x, pb.y, pb.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.6f);
        sw.playSound(null, pa.x, pa.y, pa.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.6f);
    }

    private static void teleportPlayerToEntity(ServerWorld sw, ServerPlayerEntity player, LivingEntity target) {
        Vec3d p = target.getPos();
        player.teleport(sw, p.x, p.y, p.z, player.getYaw(), player.getPitch());
        sw.playSound(null, p.x, p.y, p.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.4f);
    }

    /* ---------- Reload flow ---------- */
    public void requestReload(ServerPlayerEntity sp, ItemStack stack, Hand hand) {
        if (getAmmo(stack) >= MAG_SIZE) {
            sp.sendMessage(Text.literal("Gamble: magazine full"), true);
            return;
        }
        if (!isReloading(stack)) startReload(sp, stack, hand);
    }

    private void startReload(ServerPlayerEntity sp, ItemStack stack, Hand hand) {
        stack.getOrCreateNbt().putBoolean("gamble_reloading", true);
        stack.getOrCreateNbt().putInt("gamble_reloadT", 0);

        // tell client to play reload anim for the correct hand
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(hand.ordinal());
        ServerPlayNetworking.send(sp, PKT_RELOAD_ANIM, out);

        sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 0.5f, 0.9f);
    }
    private void stopReload(ServerPlayerEntity sp, ItemStack stack) {
        stack.getOrCreateNbt().putBoolean("gamble_reloading", false);
    }
    private static boolean isReloading(ItemStack s) {
        return s.hasNbt() && s.getNbt().getBoolean("gamble_reloading");
    }

    /* ---------- Ammo NBT ---------- */
    public static int getAmmo(ItemStack stack) { return stack.getOrCreateNbt().getInt("gamble_ammo"); }
    private static void setAmmo(ItemStack stack, int v) { stack.getOrCreateNbt().putInt("gamble_ammo", MathHelper.clamp(v, 0, MAG_SIZE)); }

    /* ---------- GeckoLib (GhostHand-style predicate) ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation FIRE   = RawAnimation.begin().thenPlay("fire");
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay("reload");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrs) {
        ctrs.add(new AnimationController<>(this, "main", state -> {
            PlayerEntity p = null;
            var e = state.getData(DataTickets.ENTITY);
            if (e instanceof PlayerEntity pe) p = pe; else p = getClientPlayer();

            boolean playFire = false, playReload = false;
            if (p != null && p.getWorld().isClient) {
                boolean main = p.getMainHandStack().isOf(this);
                boolean off  = p.getOffHandStack().isOf(this);

                if (main) {
                    playReload = reloadMainTicks > 0;
                    playFire   = fireMainTicks > 0 && !playReload;
                } else if (off) {
                    playReload = reloadOffTicks > 0;
                    playFire   = fireOffTicks > 0 && !playReload;
                }
            }

            if (playReload)      state.setAndContinue(RELOAD);
            else if (playFire)   state.setAndContinue(FIRE);
            else                 state.setAndContinue(IDLE);

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
                if (renderer == null)
                    renderer = new net.seep.odd.abilities.gamble.item.client.GambleRevolverRenderer();
                return renderer;
            }
        });
    }

    /* ---------- Client helper ---------- */
    @Environment(EnvType.CLIENT)
    private static PlayerEntity getClientPlayer() {
        var mc = MinecraftClient.getInstance();
        return mc != null ? mc.player : null;
    }

    /* ---------- Heart tint + reflection-based particle lookups ---------- */

    private enum HeartTint {
        PINK("HEART_PINK"),
        PURPLE("HEART_PURPLE"),
        TEAL("HEART_TEAL"),
        ORANGE("HEART_ORANGE"),
        GOLD("HEART_GOLD"),
        LIME("HEART_LIME"),
        BLUE("HEART_BLUE"),
        WHITE("HEART_WHITE"),
        RED("HEART_RED");

        final String field;
        HeartTint(String f){ this.field = f; }
    }

    /** If OddParticles.HEART_* exists, use it; otherwise fall back to vanilla HEART. */
    private static ParticleEffect heartEffect(HeartTint tint) {
        try {
            Class<?> c = Class.forName("net.seep.odd.particles.OddParticles");
            Field f = c.getField(tint.field);
            Object v = f.get(null);
            if (v instanceof ParticleEffect pe) return pe;
        } catch (Throwable ignored) {}
        return ParticleTypes.HEART;
    }

    /* ===================================================================== */
    /* =========================  REVOLVER HUD  ============================= */
    /* ===================================================================== */
    @Environment(EnvType.CLIENT)
    private static final class Hud {
        private static boolean registered = false;

        // Plug your textures here:
        private static final Identifier HUD_BG_TEX   = new Identifier(Oddities.MOD_ID, "textures/gui/hud/gamble_hud_bg.png");
        private static final Identifier HUD_ICON_TEX = new Identifier(Oddities.MOD_ID, "textures/gui/hud/gamble_revolver_icon.png");

        // Source texture sizes (update if your PNGs differ)
        private static final int BG_TEX_W   = 160, BG_TEX_H   = 64;
        private static final int ICON_TEX_W = 32,  ICON_TEX_H = 32;

        // On-screen sizes/placement
        private static final int BG_W = 160, BG_H = 64;
        private static final int ICON_W = 28, ICON_H = 28;

        private static final int PAD_LEFT   = 8;
        private static final int PAD_RIGHT  = 8;
        private static final int PAD_TOP    = 8;
        private static final int PAD_BOTTOM = 8;
        private static final int GAP_ICON_TEXT = 8;
        private static final int GAP_TEXT_V    = 4;

        // Target scales (clamped by fitters)
        private static final float SCALE_MODE_DESIRED = 1.25f;
        private static final float SCALE_AMMO_DESIRED = 1.45f;
        private static final float SCALE_MIN          = 0.60f;

        static void initOnce() {
            if (registered) return;
            registered = true;

            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                final var mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;

                // Only show while holding the revolver
                ItemStack main = mc.player.getMainHandStack();
                ItemStack off  = mc.player.getOffHandStack();
                boolean holding = main.getItem() instanceof GambleRevolverItem || off.getItem() instanceof GambleRevolverItem;
                if (!holding) return;

                // ---- make sure NOTHING draws over us ----
                com.mojang.blaze3d.systems.RenderSystem.disableScissor(); // stop chat clipping
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f,1f,1f,1f);

                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                int x = 8;
                int y = sh - BG_H - 8;

                // Push Z forward so we render above chat/other HUD
                var m = ctx.getMatrices();
                m.push();
                m.translate(0, 0, 1000); // huge Z puts us on top

                // Which hand are we showing?
                ItemStack stack = (main.getItem() instanceof GambleRevolverItem) ? main : off;
                int ammo = getAmmo(stack);
                int max  = MAG_SIZE;

                // Background
                ctx.drawTexture(HUD_BG_TEX, x, y, 0, 0, BG_W, BG_H, BG_TEX_W, BG_TEX_H);

                // Icon
                int iconX = x + PAD_LEFT;
                int iconY = y + (BG_H - ICON_H) / 2;
                ctx.drawTexture(HUD_ICON_TEX, iconX, iconY, 0, 0, ICON_W, ICON_H, ICON_TEX_W, ICON_TEX_H);

                // Text region (to the right of the icon)
                int textLeft = iconX + ICON_W + GAP_ICON_TEXT;
                int textRight = x + BG_W - PAD_RIGHT;
                int usableW = Math.max(0, textRight - textLeft);
                int usableH = BG_H - PAD_TOP - PAD_BOTTOM;

                // Content
                var mode = GamblePower.getMode(mc.player);
                String modeStr = switch (mode) {
                    case BUFF   -> "Buff";
                    case DEBUFF -> "Debuff";
                    default     -> "Shoot";
                };
                String ammoStr = ammo + "/" + max;

                int modeColor = switch (mode) {
                    case BUFF   -> 0xFF7EE08F;
                    case DEBUFF -> 0xFFC77CFF;
                    default     -> 0xFFFFC84A;
                };
                int ammoColor = (ammo > 0) ? 0xFFFFFFFF : 0xFFFF6868;

                // --- width fit
                float modeScale = fitWidthScale(mc, modeStr, SCALE_MODE_DESIRED, usableW);
                float ammoScale = fitWidthScale(mc, ammoStr, SCALE_AMMO_DESIRED, usableW);

                // --- vertical fit (prevents lines from overlapping or poking outside the BG)
                int fh = mc.textRenderer.fontHeight;
                float totalH = fh * modeScale + GAP_TEXT_V + fh * ammoScale;
                if (totalH > usableH) {
                    float shrink = Math.max(SCALE_MIN, usableH / totalH);
                    modeScale *= shrink;
                    ammoScale *= shrink;
                    totalH = fh * modeScale + GAP_TEXT_V + fh * ammoScale;
                }

                int textTop = y + (BG_H - (int)totalH) / 2;

                // Draw mode (status) on TOP of everything (slightly higher Z than ammo)
                drawScaledText(ctx, mc, modeStr, textLeft, textTop, modeColor, modeScale, 1010);

                // Draw ammo
                int modePixH = Math.round(fh * modeScale);
                drawScaledText(ctx, mc, ammoStr, textLeft, textTop + modePixH + GAP_TEXT_V, ammoColor, ammoScale, 1005);

                m.pop();
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            });
        }

        private static float fitWidthScale(MinecraftClient mc, String text, float desired, int maxPx) {
            int w = mc.textRenderer.getWidth(text);
            if (w <= 0) return desired;
            float maxScale = maxPx / (float) w;
            return Math.max(SCALE_MIN, Math.min(desired, maxScale));
        }

        private static void drawScaledText(DrawContext ctx, MinecraftClient mc,
                                           String text, int px, int py, int color, float scale, float extraZ) {
            var m = ctx.getMatrices();
            m.push();
            // keep Z very high so it’s never covered
            m.translate(px, py, extraZ);
            m.scale(scale, scale, 1f);
            // shadow helps legibility over chat
            ctx.drawText(mc.textRenderer, text, 0, 0, color, true);
            m.pop();
        }
    }

}
