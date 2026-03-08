// FILE: src/main/java/net/seep/odd/abilities/gamble/item/GambleRevolverItem.java
package net.seep.odd.abilities.gamble.item;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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

import net.seep.odd.abilities.gamble.GambleMode;
import net.seep.odd.abilities.power.GamblePower;
import net.seep.odd.sound.ModSounds;

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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GambleRevolverItem extends Item implements GeoItem {

    public GambleRevolverItem(Settings s) {
        super(s.maxCount(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /* ===== Tunables ===== */
    public static final int    MAG_SIZE            = 6;
    private static final double RANGE               = 36.0;
    private static final float  BASE_DAMAGE         = 5.0f;
    private static final float  BIG_DAMAGE          = 18.0f;      // 1/10 jackpot
    private static final float  BUFF_HEAL           = 3.0f;
    private static final float  DEBUFF_PING         = 2.0f;

    private static final int    RELOAD_STEP_T       = 5;          // 0.25s per bullet (20 tps)
    private static final float  HEARTS_PER_BULLET   = 1.0f;       // costs 1 heart => 2.0 health

    /** 0.2s = 4 ticks */
    public static final int    FIRE_COOLDOWN_T     = 11;
    private static final String NBT_LAST_FIRE_T     = "gamble_lastFire";

    private static final Object2LongOpenHashMap<UUID> PLAYER_FIRE_UNTIL = new Object2LongOpenHashMap<>();
    private static final double HIT_RADIUS          = 0.85;

    public static final int FIRE_ANIM_TICKS   = 7;
    public static final int RELOAD_ANIM_TICKS = 35;

    private static final float HEART_SCALE_SMALL = 0.60f;

    /* ---------- Networking ---------- */
    public static final Identifier PKT_FIRE        = new Identifier("odd", "gamble/fire");         // C->S
    public static final Identifier PKT_RELOAD      = new Identifier("odd", "gamble/reload");       // C->S
    public static final Identifier PKT_RELOAD_ANIM = new Identifier("odd", "gamble/reload_anim");  // S->C

    /* ===== client-only animation counters (NO client classes referenced here) ===== */
    private static int fireMainTicks   = 0;
    private static int fireOffTicks    = 0;
    private static int reloadMainTicks = 0;
    private static int reloadOffTicks  = 0;

    /** Called from client-only hook class when S2C says “play reload anim”. */
    public static void clientStartReloadAnim(int handOrd) {
        if (handOrd == Hand.MAIN_HAND.ordinal()) reloadMainTicks = RELOAD_ANIM_TICKS;
        else reloadOffTicks = RELOAD_ANIM_TICKS;
    }

    /** Called from client-only hook class when we locally predict firing. */
    public static void clientStartFireAnim(Hand hand) {
        if (hand == Hand.MAIN_HAND) fireMainTicks = FIRE_ANIM_TICKS;
        else fireOffTicks = FIRE_ANIM_TICKS;
    }

    /** Called each client tick (from client-only hook class). */
    public static void clientTickDownAnimCounters() {
        if (fireMainTicks   > 0) fireMainTicks--;
        if (fireOffTicks    > 0) fireOffTicks--;
        if (reloadMainTicks > 0) reloadMainTicks--;
        if (reloadOffTicks  > 0) reloadOffTicks--;
    }

    static {
        // Server receivers (gameplay)
        ServerPlayNetworking.registerGlobalReceiver(PKT_FIRE, (server, player, handler, buf, response) -> {
            final int handOrd = buf.readVarInt();
            server.execute(() -> {
                ServerPlayerEntity sp = player;

                if (GamblePower.isPowerless(sp)) {
                    GamblePower.warnPowerlessOncePerSec(sp);
                    return;
                }

                Hand hand = (handOrd == Hand.OFF_HAND.ordinal()) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = sp.getStackInHand(hand);
                if (stack.getItem() instanceof GambleRevolverItem item) item.tryFire(sp, stack, hand);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PKT_RELOAD, (server, player, handler, buf, response) -> {
            final int handOrd = buf.readVarInt();
            server.execute(() -> {
                ServerPlayerEntity sp = player;

                if (GamblePower.isPowerless(sp)) {
                    GamblePower.warnPowerlessOncePerSec(sp);
                    return;
                }

                Hand hand = (handOrd == Hand.OFF_HAND.ordinal()) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = sp.getStackInHand(hand);
                if (stack.getItem() instanceof GambleRevolverItem item) item.requestReload(sp, stack, hand);
            });
        });

        // Cancel vanilla melee/mining while holding (but allow while POWERLESS)
        AttackBlockCallback.EVENT.register((player2, world, hand, pos, dir) -> {
            ItemStack s = player2.getStackInHand(hand);
            if (!(s.getItem() instanceof GambleRevolverItem)) return ActionResult.PASS;
            if (GamblePower.isPowerless(player2)) return ActionResult.PASS;
            return ActionResult.FAIL;
        });
        AttackEntityCallback.EVENT.register((player2, world, hand, target, hit) -> {
            ItemStack s = player2.getStackInHand(hand);
            if (!(s.getItem() instanceof GambleRevolverItem)) return ActionResult.PASS;
            if (GamblePower.isPowerless(player2)) return ActionResult.PASS;
            return ActionResult.FAIL;
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

        if (isReloading(stack) && GamblePower.isPowerless(sp)) {
            stopReload(sp, stack);
            GamblePower.warnPowerlessOncePerSec(sp);
            return;
        }

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
        if (GamblePower.isPowerless(sp)) {
            if (isReloading(stack)) stopReload(sp, stack);
            GamblePower.warnPowerlessOncePerSec(sp);
            return;
        }

        if (isReloading(stack)) return;

        final long now = sp.getWorld().getTime();
        final UUID id = sp.getUuid();
        final Item itemKey = stack.getItem();

        if (sp.getItemCooldownManager().isCoolingDown(itemKey)) return;

        long until = PLAYER_FIRE_UNTIL.getOrDefault(id, 0L);
        if (now < until) return;

        long last = stack.getOrCreateNbt().getLong(NBT_LAST_FIRE_T);
        if (now - last < FIRE_COOLDOWN_T) return;

        if (getAmmo(stack) <= 0) {
            sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 0.9f);
            return;
        }

        sp.getItemCooldownManager().set(itemKey, FIRE_COOLDOWN_T);
        PLAYER_FIRE_UNTIL.put(id, now + FIRE_COOLDOWN_T);
        stack.getOrCreateNbt().putLong(NBT_LAST_FIRE_T, now);

        fire(sp, stack, hand);
    }

    private void fire(ServerPlayerEntity sp, ItemStack stack, Hand hand) {
        setAmmo(stack, getAmmo(stack) - 1);

        sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                ModSounds.GAMBLE_FIRE, SoundCategory.PLAYERS, 0.8f, 1.2f);

        GambleMode mode = GamblePower.getMode(sp);
        ServerWorld sw = (ServerWorld) sp.getWorld();
        Random r = sw.random;

        Vec3d eye  = sp.getEyePos();
        Vec3d look = sp.getRotationVec(1f).normalize();
        Vec3d end  = eye.add(look.multiply(RANGE));

        BlockHitResult bhr = sw.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, sp));
        double maxHit = (bhr.getType() == HitResult.Type.MISS) ? RANGE : eye.distanceTo(bhr.getPos());
        Vec3d stop = eye.add(look.multiply(maxHit));

        LivingEntity target = findFirstHitEntity(sw, sp, eye, stop, HIT_RADIUS);

        boolean bigShot = false;
        HeartTint tint;

        if (target != null && target.isAlive()) {
            switch (mode) {
                case DEBUFF -> {
                    target.damage(sw.getDamageSources().playerAttack(sp), DEBUFF_PING);
                    tint = applyRandomDebuff(r, sw, sp, target);
                }
                case BUFF -> {
                    target.heal(BUFF_HEAL);
                    tint = applyRandomBuff(r, sw, sp, target);
                }
                default -> {
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
    public static void initClientHooks() {
        // call ONLY from client initializer
        net.seep.odd.abilities.gamble.item.client.GambleRevolverClient.init();
    }

    private static void spawnHeartTrail(ServerWorld sw, Vec3d from, Vec3d to, HeartTint tint) {
        ParticleEffect heart = heartEffect(tint);
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

    private static HeartTint applyRandomBuff(Random r, ServerWorld sw, ServerPlayerEntity shooter, LivingEntity target) {
        int pick = r.nextInt(8);
        switch (pick) {
            case 0 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20*6, 4, true, true, true), shooter); return HeartTint.TEAL; }
            case 1 -> {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20*10, 1, true, true, true), shooter);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,   20*10, 0, true, true, true), shooter);
                return HeartTint.PINK;
            }
            case 2 -> { swapPositions(sw, shooter, target); return HeartTint.PURPLE; }
            case 3 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20*5, 2, true, true, true), shooter); return HeartTint.ORANGE; }
            case 4 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20*8, 1, true, true, true), shooter); return HeartTint.RED; }
            case 5 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20*10, 0, true, true, true), shooter); return HeartTint.GOLD; }
            case 6 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20*10, 3, true, true, true), shooter); return HeartTint.GOLD; }
            default -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 20*15, 0, true, true, true), shooter); return HeartTint.LIME; }
        }
    }

    private static HeartTint applyRandomDebuff(Random r, ServerWorld sw, ServerPlayerEntity shooter, LivingEntity target) {
        int pick = r.nextInt(8);
        switch (pick) {
            case 0 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20*15, 0, true, true, true), shooter); return HeartTint.WHITE; }
            case 1 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20*3, 0, true, true, true), shooter); return HeartTint.BLUE; }
            case 2 -> {
                Vec3d away = target.getPos().subtract(shooter.getPos()).normalize();
                target.addVelocity(away.x * 1.4, 0.55, away.z * 1.4);
                target.velocityModified = true;
                return HeartTint.ORANGE;
            }
            case 3 -> { teleportPlayerToEntity(sw, shooter, target); return HeartTint.PURPLE; }
            case 4 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20*5, 3, true, true, true), shooter); return HeartTint.BLUE; }
            case 5 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 20*8, 0, true, true, true), shooter); return HeartTint.WHITE; }
            case 6 -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20*8, 1, true, true, true), shooter); return HeartTint.RED; }
            default -> { target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20*6, 0, true, true, true), shooter); return HeartTint.LIME; }
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
        if (GamblePower.isPowerless(sp)) {
            if (isReloading(stack)) stopReload(sp, stack);
            GamblePower.warnPowerlessOncePerSec(sp);
            return;
        }

        if (getAmmo(stack) >= MAG_SIZE) {
            sp.sendMessage(Text.literal("Gamble: magazine full"), true);
            return;
        }
        if (!isReloading(stack)) startReload(sp, stack, hand);
    }

    private void startReload(ServerPlayerEntity sp, ItemStack stack, Hand hand) {
        stack.getOrCreateNbt().putBoolean("gamble_reloading", true);
        stack.getOrCreateNbt().putInt("gamble_reloadT", 0);

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

    /* ---------- GeckoLib ---------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE   = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation FIRE   = RawAnimation.begin().thenPlay("fire");
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay("reload");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrs) {
        ctrs.add(new AnimationController<>(this, "main", state -> {
            // Try to get player, but DON'T require it.
            Object ent = state.getData(DataTickets.ENTITY);
            PlayerEntity p = (ent instanceof PlayerEntity pe) ? pe : null;

            // If we can’t identify the hand reliably, still animate if ANY counter is active.
            boolean anyReload = (reloadMainTicks > 0) || (reloadOffTicks > 0);
            boolean anyFire   = (fireMainTicks > 0)   || (fireOffTicks > 0);

            // If we do have a player, prefer correct hand selection
            if (p != null && p.getWorld() != null && p.getWorld().isClient) {
                ItemStack renderStack = state.getData(DataTickets.ITEMSTACK);

                boolean mainHeld = p.getMainHandStack().isOf(this);
                boolean offHeld  = p.getOffHandStack().isOf(this);

                boolean isMain = false, isOff = false;
                if (renderStack != null) {
                    if (mainHeld && ItemStack.areEqual(renderStack, p.getMainHandStack())) isMain = true;
                    if (offHeld  && ItemStack.areEqual(renderStack, p.getOffHandStack()))  isOff  = true;
                }
                if (!isMain && !isOff) {
                    if (mainHeld && !offHeld) isMain = true;
                    else if (offHeld && !mainHeld) isOff = true;
                    else if (mainHeld) isMain = true;
                }

                boolean playReload = false, playFire = false;
                if (isMain) {
                    playReload = reloadMainTicks > 0;
                    playFire   = fireMainTicks > 0 && !playReload;
                } else if (isOff) {
                    playReload = reloadOffTicks > 0;
                    playFire   = fireOffTicks > 0 && !playReload;
                } else {
                    playReload = anyReload;
                    playFire   = anyFire && !playReload;
                }

                if (playReload)      state.setAndContinue(RELOAD);
                else if (playFire)   state.setAndContinue(FIRE);
                else                 state.setAndContinue(IDLE);

                return PlayState.CONTINUE;
            }

            // Fallback (no player): animate if counters are active
            if (anyReload)      state.setAndContinue(RELOAD);
            else if (anyFire)   state.setAndContinue(FIRE);
            else                state.setAndContinue(IDLE);

            return PlayState.CONTINUE;
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ✅ renderer hooks are client-only (stripped on dedicated server)
    @Environment(EnvType.CLIENT)
    private Supplier<Object> renderProvider;

    @Environment(EnvType.CLIENT)
    @Override public Supplier<Object> getRenderProvider() {
        if (renderProvider == null) renderProvider = GeoItem.makeRenderer(this);
        return renderProvider;
    }

    @Environment(EnvType.CLIENT)
    @Override public double getTick(Object animatable) { return RenderUtils.getCurrentTick(); }

    @Environment(EnvType.CLIENT)
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

    private static ParticleEffect heartEffect(HeartTint tint) {
        try {
            Class<?> c = Class.forName("net.seep.odd.particles.OddParticles");
            Field f = c.getField(tint.field);
            Object v = f.get(null);
            if (v instanceof ParticleEffect pe) return pe;
        } catch (Throwable ignored) {}
        return ParticleTypes.HEART;
    }
}