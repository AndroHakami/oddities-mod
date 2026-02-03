// src/main/java/net/seep/odd/abilities/power/NecromancerPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.Explosion;

import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.network.PacketByteBuf;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.necromancer.NecromancerNet;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.necromancer.AbstractCorpseEntity;
import net.seep.odd.item.ModItems;

import java.util.*;

public final class NecromancerPower implements Power {

    // ===== Minion tags =====
    private static final String TAG_SUMMONED = "odd_necro_summoned";
    private static final String TAG_OWNER_PREFIX = "odd_necro_owner:";

    // ===== Summon config =====
    private static final int SUMMON_COOLDOWN_T = 20 * 10; // 10s
    private static final int SUMMON_MIN = 5;
    private static final int SUMMON_MAX = 6;
    private static final int SUMMON_RAY_RANGE = 32;
    private static final double SUMMON_SPREAD = 3.5;

    // ===== Fear ray config =====
    private static final double FEAR_RANGE = 25.0;
    private static final int FEAR_TICKS = 20 * 3;

    // ===== Corpse config =====
    public static final int CORPSE_LIFE_T = 20 * 30;
    private static final int DETONATE_WINDUP_T = 20 * 3;
    private static final double DETONATE_SCAN_RADIUS = 30.0;
    private static final float CORPSE_EXPLOSION_STRENGTH = 2.7f;

    // ===== Undead ignore config =====
    private static final double UNDEAD_IGNORE_RADIUS = 48.0;
    private static final int UNDEAD_IGNORE_EVERY = 2;
    private static final int HIT_GRACE_T = 20 * 10;

    // ===== Horde focus config =====
    private static final int FOCUS_REFRESH_EVERY = 5;
    private static final int FOCUS_DEFAULT_T = 20 * 20;
    private static final double FOCUS_SCAN_OWNER_R = 96.0;
    private static final double FOCUS_SCAN_TARGET_R = 96.0;

    // ===== Summoning mode behavior =====
    private static final int MODE_SOFT_T = 20 * 6;   // 6s default time to cast
    private static final int MODE_HARD_T = 20 * 12;  // 12s absolute failsafe
    private static final int CAST_KEEPALIVE_T = 15;  // keepalive extension step
    private static final int CAST_SLOWNESS_AMP = 2;
    // ===== Minion tags =====


    // NEW: expiry tag for minions
    private static final String TAG_EXPIRES_PREFIX = "odd_necro_expires:";

    // 30s lifetime
    private static final int MINION_LIFE_T = 20 * 30; // slowness III

    @Override public String id() { return "necromancer"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/necro_summon.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/necro_ray.png");
            case "third" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/necro_explode.png");
            default -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() { return "Summon undead hordes, fire a fear ray, and detonate corpses."; }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Summoning Mode: press to enter. Requires Necromancer Staff equipped. LMB = zombies, RMB = skeletons. Slowness III + guard while aiming.";
            case "secondary" ->
                    "Ray Of Fear: shoots regardless of target; applies Weakness IV + Darkness + Speed I for 3 seconds if it hits.";
            case "third" ->
                    "Exploding Corpses: after a 3s wind-up, detonate all your corpses within 30 blocks.";
            default -> "";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/zero_suit.png");
    }

    /* ===================== State ===================== */

    private static final class St {
        int summonCooldown = 0;

        // mode
        boolean summonMode = false;
        long modeSoftUntil = 0; // extended by keepalive
        long modeHardUntil = 0; // never extended, absolute cap

        boolean detonating = false;
        int detonateTicks = 0;

        int undeadIgnoreTicker = 0;

        final Object2LongOpenHashMap<UUID> hitGraceUntil = new Object2LongOpenHashMap<>();

        UUID focusTarget = null;
        long focusUntil = 0;
        int focusTicker = 0;

        boolean lastSentMode = false;
    }

    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof NecromancerPower;
    }

    public static boolean isNecromancer(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof NecromancerPower;
    }

    public static boolean shouldUndeadIgnore(ServerPlayerEntity necro, UUID undeadMobId, long now) {
        St st = DATA.get(necro.getUuid());
        if (st == null) return true; // default: ignore necromancer
        long until = st.hitGraceUntil.getOrDefault(undeadMobId, -1L);
        return until < now; // true = ignore, false = can target
    }


    public static boolean isSummonModeActive(ServerPlayerEntity p) {
        St st = DATA.get(p.getUuid());
        if (st == null) return false;
        if (!(p.getWorld() instanceof ServerWorld sw)) return false;
        long now = sw.getTime();
        return st.summonMode && now <= st.modeSoftUntil && now <= st.modeHardUntil;
    }



    /* ===================== Init ===================== */

    private static boolean INITTED = false;

    public static void init() {
        if (INITTED) return;
        INITTED = true;

        ServerTickEvents.END_SERVER_TICK.register(NecromancerPower::tickServer);

        // Guard: stop breaking blocks while in summon mode
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!isCurrent(sp)) return ActionResult.PASS;
            if (!isSummonModeActive(sp)) return ActionResult.PASS;
            return ActionResult.FAIL;
        });

        // Guard: stop attacking entities while in summon mode + track hit-grace
        AttackEntityCallback.EVENT.register((player, world, hand, hitEntity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!isCurrent(sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            long now = sw.getTime();
            St st = S(sp);

            if (st.summonMode && now <= st.modeSoftUntil && now <= st.modeHardUntil) {
                return ActionResult.FAIL;
            }

            if (hitEntity instanceof LivingEntity le && le.getGroup() == EntityGroup.UNDEAD) {
                st.hitGraceUntil.put(le.getUuid(), now + HIT_GRACE_T);
            }

            return ActionResult.PASS;
        });

        // Guard: stop item use (prevents staff right-click ability from firing while aiming)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!isCurrent(sp)) return TypedActionResult.pass(player.getStackInHand(hand));
            if (!isSummonModeActive(sp)) return TypedActionResult.pass(player.getStackInHand(hand));
            return TypedActionResult.fail(player.getStackInHand(hand));
        });

        // Minion death -> corpse spawn
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.getWorld() instanceof ServerWorld sw)) return;
            if (!(entity instanceof ZombieEntity || entity instanceof SkeletonEntity)) return;
            if (!entity.getCommandTags().contains(TAG_SUMMONED)) return;

            UUID owner = ownerFromTags(entity.getCommandTags());
            if (owner == null) return;

            spawnCorpse(sw, (LivingEntity) entity, owner);
        });
    }

    private static void tickServer(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isCurrent(p)) continue;
            serverTick(p);
        }
    }

    /* ===================== Primary: enter mode (NOT toggle) ===================== */

    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        if (!holdingStaffItem(p)) {
            p.sendMessage(Text.literal("You need the Necromancer Staff equipped."), true);
            return;
        }

        St st = S(p);

        long now = sw.getTime();

        // Always ENTER/REFRESH mode (never "toggle off" via button)
        st.summonMode = true;
        st.modeSoftUntil = now + MODE_SOFT_T;
        st.modeHardUntil = now + MODE_HARD_T;

        sendSummonModeIfChanged(p, st, true);

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.35f);

        p.sendMessage(Text.literal("Summoning Mode: aim then LMB/RMB."), true);
    }

    /* ===================== Secondary: fear ray ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!holdingStaffItem(p)) {
            p.sendMessage(Text.literal("You need the Necromancer Staff equipped."), true);
            return;
        }
        fireFearRay(p);
    }

    public static void onClientFearRequest(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!holdingStaffItem(p)) return;
        fireFearRay(p);
    }

    /* ===================== Third: explode corpses ===================== */

    @Override
    public void activateThird(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!holdingStaffItem(p)) {
            p.sendMessage(Text.literal("You need the Necromancer Staff equipped."), true);
            return;
        }

        St st = S(p);

        if (!st.detonating) {
            st.detonating = true;
            st.detonateTicks = 0;

            p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.85f, 0.8f);
            p.sendMessage(Text.literal("Exploding corpses..."), true);
        } else {
            st.detonating = false;
            st.detonateTicks = 0;

            p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.9f);
            p.sendMessage(Text.literal("Cancelled."), true);
        }
    }

    /* ===================== Client -> Server keepalive/cancel ===================== */

    public static void onClientSetCasting(ServerPlayerEntity p, boolean casting) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S(p);
        long now = sw.getTime();

        if (!casting) {
            // client says “exit/cancel”
            if (st.summonMode) {
                st.summonMode = false;
                st.modeSoftUntil = 0;
                st.modeHardUntil = 0;
                sendSummonModeIfChanged(p, st, false);
            }
            return;
        }

        // keepalive only matters if mode is active
        if (!st.summonMode) return;

        // extend SOFT timer but never beyond hard cap
        long newSoft = now + CAST_KEEPALIVE_T;
        st.modeSoftUntil = Math.min(newSoft, st.modeHardUntil);
    }

    /* ===================== Summon request ===================== */

    public static void onClientSummonRequest(ServerPlayerEntity p, boolean skeleton, BlockPos groundBlockPos) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S(p);
        long now = sw.getTime();

        // Must be in mode, and not timed out
        if (!st.summonMode || now > st.modeSoftUntil || now > st.modeHardUntil) {
            // force-resync off (fixes “client ring but server says no”)
            st.summonMode = false;
            st.modeSoftUntil = 0;
            st.modeHardUntil = 0;
            sendSummonModeIfChanged(p, st, false);
            return;
        }

        if (!holdingStaffItem(p)) {
            st.summonMode = false;
            st.modeSoftUntil = 0;
            st.modeHardUntil = 0;
            sendSummonModeIfChanged(p, st, false);
            return;
        }

        if (st.summonCooldown > 0) {
            // tell them and keep mode off so it doesn’t feel broken
            p.sendMessage(Text.literal("Summon is on cooldown."), true);
            st.summonMode = false;
            st.modeSoftUntil = 0;
            st.modeHardUntil = 0;
            sendSummonModeIfChanged(p, st, false);
            return;
        }

        if (p.getBlockPos().getSquaredDistance(groundBlockPos) > (SUMMON_RAY_RANGE * SUMMON_RAY_RANGE)) {
            p.sendMessage(Text.literal("Out of range."), true);
            return; // keep mode active so they can aim closer
        }

        // EXIT mode immediately after successful cast (server-authoritative)
        st.summonMode = false;
        st.modeSoftUntil = 0;
        st.modeHardUntil = 0;
        sendSummonModeIfChanged(p, st, false);

        // burst at cast spot
        var magenta = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1f, 0f, 1f), 1.6f);
        sw.spawnParticles(magenta,
                groundBlockPos.getX() + 0.5, groundBlockPos.getY() + 1.02, groundBlockPos.getZ() + 0.5,
                60, 0.85, 0.18, 0.85, 0.0);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
                groundBlockPos.getX() + 0.5, groundBlockPos.getY() + 1.02, groundBlockPos.getZ() + 0.5,
                28, 0.6, 0.2, 0.6, 0.0);

        Random rng = sw.getRandom();
        int count = SUMMON_MIN + rng.nextInt(SUMMON_MAX - SUMMON_MIN + 1);

        String ownerTag = TAG_OWNER_PREFIX + p.getUuid();

        for (int i = 0; i < count; i++) {
            double ox = (rng.nextDouble() - 0.5) * 2.0 * SUMMON_SPREAD;
            double oz = (rng.nextDouble() - 0.5) * 2.0 * SUMMON_SPREAD;

            int sx = groundBlockPos.getX() + (int)Math.round(ox);
            int sz = groundBlockPos.getZ() + (int)Math.round(oz);

            int topY = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            int baseY = Math.max(groundBlockPos.getY() + 1, topY);
            BlockPos sp = new BlockPos(sx, baseY, sz);

            Entity e = skeleton ? EntityType.SKELETON.create(sw) : EntityType.ZOMBIE.create(sw);
            if (!(e instanceof MobEntity mob)) continue;

            mob.refreshPositionAndAngles(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5,
                    rng.nextFloat() * 360f, 0f);
            mob.initialize(sw, sw.getLocalDifficulty(sp), SpawnReason.MOB_SUMMONED, null, null);

            mob.addCommandTag(TAG_SUMMONED);
            mob.addCommandTag(ownerTag);
            mob.addCommandTag(TAG_EXPIRES_PREFIX + (sw.getTime() + MINION_LIFE_T));



            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 2, 1, true, false, true));
            sw.spawnEntity(mob);
        }

        st.summonCooldown = SUMMON_COOLDOWN_T;
        p.getItemCooldownManager().set(ModItems.NECROMANCER_STAFF, SUMMON_COOLDOWN_T);

        sw.playSound(null,
                groundBlockPos.getX() + 0.5, groundBlockPos.getY() + 1.0, groundBlockPos.getZ() + 0.5,
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.65f, skeleton ? 1.35f : 1.1f);

        p.sendMessage(Text.literal((skeleton ? "Skeleton" : "Zombie") + " horde summoned."), true);
    }

    /* ===================== Focus API (unchanged) ===================== */

    public static void setFocus(ServerPlayerEntity owner, LivingEntity target) {
        if (!isCurrent(owner)) return;
        if (!(owner.getWorld() instanceof ServerWorld sw)) return;

        St st = S(owner);
        st.focusTarget = target.getUuid();
        st.focusUntil = sw.getTime() + FOCUS_DEFAULT_T;
        st.focusTicker = 0;

        tickFocusMinions(sw, owner, st);
    }

    /* ===================== Server tick ===================== */

    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S(p);
        long now = sw.getTime();

        // cooldown tick
        if (st.summonCooldown > 0) st.summonCooldown--;

        // mode timeout -> force off + resync client
        if (st.summonMode && (now > st.modeSoftUntil || now > st.modeHardUntil || !holdingStaffItem(p))) {
            st.summonMode = false;
            st.modeSoftUntil = 0;
            st.modeHardUntil = 0;
            sendSummonModeIfChanged(p, st, false);
        }

        // slowness + “guard feel” while mode is active
        if (st.summonMode) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, CAST_SLOWNESS_AMP, true, false, false));
        }

        // undead ignore
        st.undeadIgnoreTicker++;
        if (st.undeadIgnoreTicker >= UNDEAD_IGNORE_EVERY) {
            st.undeadIgnoreTicker = 0;
            tickUndeadIgnore(sw, p, st);
        }

        // focus ticking
        st.focusTicker++;
        if (st.focusTicker >= FOCUS_REFRESH_EVERY) {
            st.focusTicker = 0;
            tickFocusMinions(sw, p, st);
        }

        // detonate wind-up
        if (st.detonating) {
            st.detonateTicks++;

            if ((p.age % 3) == 0) windupParticles(sw, p);

            if (st.detonateTicks >= DETONATE_WINDUP_T) {
                st.detonating = false;
                st.detonateTicks = 0;
                detonateCorpses(sw, p);
            }
        }
    }

    /* ===================== Focus logic ===================== */

    private static void tickFocusMinions(ServerWorld sw, ServerPlayerEntity owner, St st) {
        long now = sw.getTime();
        if (st.focusTarget == null || now > st.focusUntil) {
            st.focusTarget = null;
            return;
        }

        Entity targetEnt = sw.getEntity(st.focusTarget);
        if (!(targetEnt instanceof LivingEntity target) || !target.isAlive()) {
            st.focusTarget = null;
            return;
        }

        String ownerTag = TAG_OWNER_PREFIX + owner.getUuid();

        Box boxOwner = new Box(owner.getX() - FOCUS_SCAN_OWNER_R, owner.getY() - FOCUS_SCAN_OWNER_R, owner.getZ() - FOCUS_SCAN_OWNER_R,
                owner.getX() + FOCUS_SCAN_OWNER_R, owner.getY() + FOCUS_SCAN_OWNER_R, owner.getZ() + FOCUS_SCAN_OWNER_R);

        Box boxTarget = new Box(target.getX() - FOCUS_SCAN_TARGET_R, target.getY() - FOCUS_SCAN_TARGET_R, target.getZ() - FOCUS_SCAN_TARGET_R,
                target.getX() + FOCUS_SCAN_TARGET_R, target.getY() + FOCUS_SCAN_TARGET_R, target.getZ() + FOCUS_SCAN_TARGET_R);

        List<MobEntity> mobs = new ArrayList<>();
        mobs.addAll(sw.getEntitiesByClass(MobEntity.class, boxOwner, m -> m.isAlive() && m.getCommandTags().contains(TAG_SUMMONED) && m.getCommandTags().contains(ownerTag)));
        mobs.addAll(sw.getEntitiesByClass(MobEntity.class, boxTarget, m -> m.isAlive() && m.getCommandTags().contains(TAG_SUMMONED) && m.getCommandTags().contains(ownerTag)));

        if (mobs.isEmpty()) return;

        for (MobEntity mob : mobs) {
            if (mob.getTarget() == target) continue;

            mob.setTarget(target);
            mob.getNavigation().startMovingTo(target, 1.15);

            if (mob instanceof HostileEntity he) he.setAttacker(owner);
            if (mob instanceof Angerable ang) { ang.setAngryAt(target.getUuid()); ang.setAngerTime(200); }
        }
    }

    /* ===================== Fear ray ===================== */

    private static void fireFearRay(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) p.getWorld();

        Vec3d start = p.getEyePos();
        Vec3d dir = p.getRotationVector().normalize();
        Vec3d end = start.add(dir.multiply(FEAR_RANGE));

        var bhr = sw.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                p
        ));

        Vec3d hitPos = (bhr.getType() == HitResult.Type.MISS) ? end : bhr.getPos();

        Box box = p.getBoundingBox().stretch(dir.multiply(FEAR_RANGE)).expand(1.25);
        EntityHitResult ehr = ProjectileUtil.raycast(p, start, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != p, FEAR_RANGE * FEAR_RANGE);

        LivingEntity target = null;
        if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
            target = le;
            hitPos = le.getEyePos().subtract(0, le.getStandingEyeHeight() * 0.35, 0);
        }

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.75f, 1.25f);

        int steps = 26;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d at = start.lerp(hitPos, t);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
                    at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }

        if (target != null) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, FEAR_TICKS, 3, true, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, FEAR_TICKS, 0, true, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, FEAR_TICKS, 0, true, true, true));

            sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.4f, 1.4f);
        }
    }

    /* ===================== Corpses ===================== */

    private static void spawnCorpse(ServerWorld sw, LivingEntity dead, UUID owner) {
        AbstractCorpseEntity corpse;
        if (dead instanceof ZombieEntity) corpse = ModEntities.ZOMBIE_CORPSE.create(sw);
        else corpse = ModEntities.SKELETON_CORPSE.create(sw);

        if (corpse == null) return;

        corpse.refreshPositionAndAngles(dead.getX(), dead.getY(), dead.getZ(),
                sw.getRandom().nextFloat() * 360f, 0f);
        corpse.setOwner(owner);
        sw.spawnEntity(corpse);

        sw.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(),
                SoundEvents.BLOCK_SOUL_SOIL_BREAK, SoundCategory.PLAYERS, 0.35f, 0.9f);
    }

    private static void windupParticles(ServerWorld sw, ServerPlayerEntity owner) {
        double r = DETONATE_SCAN_RADIUS;
        Box box = new Box(owner.getX() - r, owner.getY() - r, owner.getZ() - r,
                owner.getX() + r, owner.getY() + r, owner.getZ() + r);

        List<AbstractCorpseEntity> corpses = sw.getEntitiesByClass(AbstractCorpseEntity.class, box,
                c -> c.isAlive() && owner.getUuid().equals(c.getOwner()));

        for (AbstractCorpseEntity c : corpses) {
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                    c.getX(), c.getY() + 0.15, c.getZ(),
                    2, 0.12, 0.02, 0.12, 0.0);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
                    c.getX(), c.getY() + 0.1, c.getZ(),
                    1, 0.08, 0.02, 0.08, 0.0);
        }
    }

    private static void detonateCorpses(ServerWorld sw, ServerPlayerEntity owner) {
        double r = DETONATE_SCAN_RADIUS;
        Box box = new Box(owner.getX() - r, owner.getY() - r, owner.getZ() - r,
                owner.getX() + r, owner.getY() + r, owner.getZ() + r);

        List<AbstractCorpseEntity> corpses = sw.getEntitiesByClass(AbstractCorpseEntity.class, box,
                c -> c.isAlive() && owner.getUuid().equals(c.getOwner()));

        if (corpses.isEmpty()) {
            owner.sendMessage(Text.literal("No corpses to detonate."), true);
            return;
        }

        sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.9f, 0.9f);

        for (AbstractCorpseEntity c : corpses) {
            Vec3d pos = c.getPos();

            Explosion ex = new Explosion(
                    sw, owner, null, null,
                    pos.x, pos.y + 0.05, pos.z,
                    CORPSE_EXPLOSION_STRENGTH, false,
                    Explosion.DestructionType.KEEP
            );
            ex.collectBlocksAndDamageEntities();
            ex.affectWorld(true);

            sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 0.2, pos.z, 1, 0, 0, 0, 0);

            c.discard();
        }

        owner.sendMessage(Text.literal("BOOM."), true);
    }

    /* ===================== Undead ignore ===================== */

    private static void tickUndeadIgnore(ServerWorld sw, ServerPlayerEntity p, St st) {
        double r = UNDEAD_IGNORE_RADIUS;
        Box box = new Box(p.getX() - r, p.getY() - r, p.getZ() - r,
                p.getX() + r, p.getY() + r, p.getZ() + r);

        long now = sw.getTime();

        for (var it = st.hitGraceUntil.object2LongEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getLongValue() < now) it.remove();
        }

        List<MobEntity> undead = sw.getEntitiesByClass(MobEntity.class, box,
                e -> e.isAlive() && e.getGroup() == EntityGroup.UNDEAD);

        for (MobEntity mob : undead) {
            if (mob.getTarget() != p) continue;

            long until = st.hitGraceUntil.getOrDefault(mob.getUuid(), -1L);
            if (until >= now) continue;

            mob.setTarget(null);
            mob.getNavigation().stop();

            if (mob instanceof HostileEntity he) he.setAttacker(null);
            if (mob instanceof Angerable ang) { ang.setAngryAt(null); ang.setAngerTime(0); }
        }
    }

    /* ===================== Networking helper ===================== */

    private static void sendSummonModeIfChanged(ServerPlayerEntity p, St st, boolean on) {
        if (st.lastSentMode == on) return;
        st.lastSentMode = on;

        if (!ServerPlayNetworking.canSend(p, NecromancerNet.S2C_NECRO_MODE)) return;

        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(on);
        ServerPlayNetworking.send(p, NecromancerNet.S2C_NECRO_MODE, out);
    }

    /* ===================== Staff helper ===================== */

    private static boolean holdingStaffItem(ServerPlayerEntity p) {
        return p.getMainHandStack().getItem() == ModItems.NECROMANCER_STAFF
                || p.getOffHandStack().getItem() == ModItems.NECROMANCER_STAFF;
    }

    private static UUID ownerFromTags(Set<String> tags) {
        for (String t : tags) {
            if (t.startsWith(TAG_OWNER_PREFIX)) {
                String raw = t.substring(TAG_OWNER_PREFIX.length());
                try { return UUID.fromString(raw); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
