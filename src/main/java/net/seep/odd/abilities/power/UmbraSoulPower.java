package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import net.seep.odd.abilities.astral.OddAirSwim;
import net.seep.odd.abilities.astral.OddUmbraPhase;
import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.net.UmbraNet;
import net.seep.odd.abilities.umbra.entity.ShadowKunaiEntity;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UmbraSoulPower implements Power, ChargedPower {

    @Override public String id() { return "umbra_soul"; }
    @Override public long cooldownTicks() { return 0; }

    @Override public String displayName() { return "Red Shadow"; }
    @Override public long secondaryCooldownTicks() { return 0L; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/umbra_cloud.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/umbra_kunai.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "INTO THE SHADOWS";
            case "secondary" -> "SHADOW KUNAI";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String longDescription() {
        return """
               Fade in and out of combat by turning your body itself into shadow.

               """;
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Enter a shadowy state where you swim through the air and phase through blocks.";
            case "secondary" -> "Throw a shadowy kunai that teleports you to the location it hits, or swaps you out with an entity if the kunai strikes them directly.";
            default -> "";
        };
    }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    /* ===================== SECONDARY CHARGES (kunai) ===================== */

    private static final int  KUNAI_MAX_CHARGES     = 2;
    private static final int  KUNAI_RECHARGE_TICKS  = 20 * 6;
    private static final String KUNAI_CHARGE_KEY    = "umbra_soul#secondary";

    @Override public boolean usesCharges(String slot) { return "secondary".equals(slot); }
    @Override public int maxCharges(String slot)      { return "secondary".equals(slot) ? KUNAI_MAX_CHARGES : 0; }
    @Override public long rechargeTicks(String slot)  { return "secondary".equals(slot) ? KUNAI_RECHARGE_TICKS : 0L; }

    private static int getKunaiCharges(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return 0;

        CooldownState cds = CooldownState.get(p.getServer());
        long now = p.getServerWorld().getTime();

        long fullAt = cds.getLastUse(p.getUuid(), KUNAI_CHARGE_KEY);
        if (fullAt <= now) return KUNAI_MAX_CHARGES;

        long debt = fullAt - now;
        int missing = (int) ((debt + KUNAI_RECHARGE_TICKS - 1L) / (long) KUNAI_RECHARGE_TICKS);
        int charges = KUNAI_MAX_CHARGES - missing;
        return Math.max(0, Math.min(KUNAI_MAX_CHARGES, charges));
    }

    private static boolean spendKunaiCharge(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return false;
        if (getKunaiCharges(p) <= 0) return false;

        CooldownState cds = CooldownState.get(p.getServer());
        long now = p.getServerWorld().getTime();
        long fullAt = cds.getLastUse(p.getUuid(), KUNAI_CHARGE_KEY);

        cds.setLastUse(p.getUuid(), KUNAI_CHARGE_KEY, Math.max(fullAt, now) + KUNAI_RECHARGE_TICKS);
        return true;
    }

    public static void refundKunaiCharge(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return;
        if (getKunaiCharges(p) >= KUNAI_MAX_CHARGES) return;

        CooldownState cds = CooldownState.get(p.getServer());
        long now = p.getServerWorld().getTime();
        long fullAt = cds.getLastUse(p.getUuid(), KUNAI_CHARGE_KEY);

        cds.setLastUse(p.getUuid(), KUNAI_CHARGE_KEY, Math.max(now, fullAt - KUNAI_RECHARGE_TICKS));
    }

    /* ===================== KUNAI BEHAVIOR ===================== */

    public static final int   KUNAI_MAX_LIFE_TICKS = 20 * 2;
    public static final float KUNAI_SPEED          = 2.8f;
    public static final float KUNAI_DAMAGE         = 2.0f;
    public static final float SWAP_MAX_HEALTH      = 100.0f;

    public static boolean canSwapWith(LivingEntity target) {
        if (target instanceof ServerPlayerEntity) return true;
        return target.getMaxHealth() <= SWAP_MAX_HEALTH;
    }

    public static boolean isAbilityInvisible(LivingEntity entity) {
        return entity instanceof OddUmbraPhase phase && phase.oddities$isUmbraPhasing();
    }

    /* ========================= POWERLESS gating ========================= */

    private static final Object2LongOpenHashMap<UUID> LAST_POWERLESS_MSG = new Object2LongOpenHashMap<>();

    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static boolean blockIfPowerless(ServerPlayerEntity p) {
        if (!isPowerless(p)) return false;

        long now = p.getServerWorld().getTime();
        long last = LAST_POWERLESS_MSG.getOrDefault(p.getUuid(), Long.MIN_VALUE);
        if (now - last >= 20) {
            LAST_POWERLESS_MSG.put(p.getUuid(), now);
            p.sendMessage(Text.literal("§cYou are powerless."), true);
        }
        return true;
    }

    /* ===================== SHADOW CONFIG (primary) ===================== */

    private static final int MAX_ENERGY      = 20 * 15;
    private static final int DRAIN_PER_TICK  = 8;
    private static final int REGEN_PER_TICK  = 4;
    private static final int HUD_SYNC_PERIOD = 0;

    private static final int SMOKE_PERIOD_TICKS = 3;
    private static final int SHADOW_EXIT_GRAVITY_DELAY_TICKS = 1;
    private static final int SHADOW_REGEN_DELAY_TICKS = 10;

    private static final int    SHADOW_SMOKE_PARTICLES_PER_BURST = 28;
    private static final double SHADOW_SMOKE_RADIUS_MIN = 0.35;
    private static final double SHADOW_SMOKE_RADIUS_MAX = 0.95;
    private static final double SHADOW_SMOKE_INWARD_SPEED = 0.06;

    /* ===================== STATE ===================== */

    private static final class State {
        int energy = MAX_ENERGY;
        boolean shadowActive = false;
        int hudSyncCooldown = 0;

        boolean pendingShadowGravityRestore = false;
        int shadowGravityDelayTicks = 0;

        int shadowRegenDelayTicks = 0;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static @NotNull State S(ServerPlayerEntity p) { return STATES.computeIfAbsent(p.getUuid(), k -> new State()); }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        State s = S(player);
        if (s.shadowActive) {
            stopShadow(player, s);
            return;
        }

        if (player instanceof OddAirSwim air) air.oddities$setAirSwim(false);
        if (player instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(false);
    }

    /* ===================== PRIMARY (SHADOW) ===================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (blockIfPowerless(player)) return;

        State s = S(player);
        if (!s.shadowActive) {
            if (s.energy <= 0) return;
            startShadow(player, s);
        } else {
            stopShadow(player, s);
        }
    }

    private static void startShadow(ServerPlayerEntity p, State s) {
        s.pendingShadowGravityRestore = false;
        s.shadowGravityDelayTicks = 0;
        s.shadowRegenDelayTicks = 0;

        if (p instanceof OddAirSwim air) air.oddities$setAirSwim(true);
        if (p instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(true);

        p.setSwimming(true);

        p.setInvisible(true);
        p.setInvulnerable(true);
        p.setNoGravity(true);
        p.fallDistance = 0;

        p.getWorld().playSound(null, p.getBlockPos(), ModSounds.UMBRA_SHADOW_SHIFT, SoundCategory.PLAYERS, 0.9f, 1.0f);

        s.shadowActive = true;
        UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, true);
    }

    private static void stopShadow(ServerPlayerEntity p, State s) {
        if (p instanceof OddAirSwim air) air.oddities$setAirSwim(false);
        if (p instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(false);

        p.setSwimming(false);

        s.pendingShadowGravityRestore = true;
        s.shadowGravityDelayTicks = Math.max(0, SHADOW_EXIT_GRAVITY_DELAY_TICKS);
        s.shadowRegenDelayTicks = Math.max(0, SHADOW_REGEN_DELAY_TICKS);

        p.setInvisible(false);
        p.setInvulnerable(false);
        p.fallDistance = 0;

        p.getWorld().playSound(null, p.getBlockPos(), ModSounds.UMBRA_SHADOW_SHIFT, SoundCategory.PLAYERS, 0.9f, 0.72f);

        s.shadowActive = false;
        UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, false);
    }

    /* ===================== SECONDARY (kunai) ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (blockIfPowerless(player)) return;

        if (!spendKunaiCharge(player)) {
            return;
        }

        ServerWorld sw = (ServerWorld) player.getWorld();

        ShadowKunaiEntity kunai = new ShadowKunaiEntity(sw, player);
        kunai.setMaxLifeTicks(KUNAI_MAX_LIFE_TICKS);
        kunai.setDamage(KUNAI_DAMAGE);

        kunai.setPosition(player.getX(), player.getEyeY() - 0.10, player.getZ());
        kunai.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, KUNAI_SPEED, 0.0F);

        sw.spawnEntity(kunai);

        player.swingHand(Hand.MAIN_HAND, true);
        sw.playSound(
                null,
                player.getBlockPos(),
                ModSounds.SHADOW_KUNAI_THROW,
                SoundCategory.PLAYERS,
                0.90f,
                0.50f
        );
    }

    /* ===================== SERVER TICK ===================== */

    public static void serverTick(ServerPlayerEntity p) {
        State s = S(p);

        if (!s.shadowActive && p instanceof OddUmbraPhase ph && ph.oddities$isUmbraPhasing()) {
            ph.oddities$setUmbraPhasing(false);
        }

        if (!s.shadowActive && s.pendingShadowGravityRestore) {
            if (s.shadowGravityDelayTicks > 0) {
                p.setNoGravity(true);
                s.shadowGravityDelayTicks--;
            } else {
                p.setNoGravity(false);
                s.pendingShadowGravityRestore = false;
            }
        }

        if (s.shadowActive) {
            s.energy = Math.max(0, s.energy - DRAIN_PER_TICK);

            if (p instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(true);
            p.setInvisible(true);
            p.setNoGravity(true);
            p.setInvulnerable(true);
            p.fallDistance = 0;

            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 4, true, false, false));

            if (p.age % SMOKE_PERIOD_TICKS == 0) {
                spawnShadowSmokeCloud((ServerWorld) p.getWorld(), p);
            }

            if (s.energy <= 0) stopShadow(p, s);
        } else {
            if (s.shadowRegenDelayTicks > 0) {
                s.shadowRegenDelayTicks--;
            } else if (p.isOnGround() || !p.isFallFlying()) {
                s.energy = Math.min(MAX_ENERGY, s.energy + REGEN_PER_TICK);
            }
        }

        if (s.hudSyncCooldown-- <= 0) {
            UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, s.shadowActive);
            s.hudSyncCooldown = HUD_SYNC_PERIOD;
        }
    }

    /* ===================== SMOKE HELPERS ===================== */

    private static void spawnShadowSmokeCloud(ServerWorld w, ServerPlayerEntity p) {
        var rand = w.getRandom();
        Vec3d center = new Vec3d(p.getX(), p.getBodyY(0.55), p.getZ());

        for (int i = 0; i < SHADOW_SMOKE_PARTICLES_PER_BURST; i++) {
            double dx = rand.nextDouble() * 2.0 - 1.0;
            double dy = (rand.nextDouble() * 2.0 - 1.0) * 0.65;
            double dz = rand.nextDouble() * 2.0 - 1.0;

            Vec3d dir = new Vec3d(dx, dy, dz);
            double len2 = dir.lengthSquared();
            if (len2 < 1.0e-6) continue;
            dir = dir.multiply(1.0 / Math.sqrt(len2));

            double r = SHADOW_SMOKE_RADIUS_MIN + rand.nextDouble() * (SHADOW_SMOKE_RADIUS_MAX - SHADOW_SMOKE_RADIUS_MIN);
            Vec3d pos = center.add(dir.multiply(r));
            Vec3d vel = center.subtract(pos).normalize().multiply(SHADOW_SMOKE_INWARD_SPEED);

            w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                    pos.x, pos.y, pos.z,
                    0,
                    vel.x, vel.y, vel.z,
                    1.0
            );
        }
    }

    @Nullable
    private static MobEntity findMobLookAt(@NotNull ServerPlayerEntity player, double dist) {
        World w = player.getWorld();
        Vec3d eye  = player.getCameraPosVec(1.0f);
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end  = eye.add(look.multiply(dist));
        Box box = player.getBoundingBox().stretch(look.multiply(dist)).expand(1.0, 1.0, 1.0);
        var ehr = net.minecraft.entity.projectile.ProjectileUtil.getEntityCollision(w, player, eye, end, box,
                e -> e instanceof MobEntity && e.isAlive());
        if (ehr == null) return null;
        Entity hit = ehr.getEntity();
        return (hit instanceof MobEntity m) ? m : null;
    }
}
