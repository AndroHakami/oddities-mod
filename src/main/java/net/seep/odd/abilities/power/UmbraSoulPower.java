// src/main/java/net/seep/odd/abilities/power/UmbraSoulPower.java
package net.seep.odd.abilities.power;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.seep.odd.abilities.astral.AstralInventory;
import net.seep.odd.abilities.net.UmbraNet;
import net.seep.odd.abilities.astral.OddAirSwim;
import net.seep.odd.abilities.astral.OddUmbraPhase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UmbraSoulPower implements Power {

    @Override public String id() { return "umbra_soul"; }
    @Override public long cooldownTicks() { return 0; }

    @Override public String displayName() { return "Red Shadow"; }

    @Override public long secondaryCooldownTicks() { return 60L * 20L; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/umbra_cloud.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/umbra_possess.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public boolean hasSlot(String slot) {
        if ("primary".equals(slot))   return true;
        if ("secondary".equals(slot)) return true;
        return false;
    }

    /* ===================== SHADOW CONFIG ===================== */
    private static final int MAX_ENERGY      = 20 * 20;
    private static final int DRAIN_PER_TICK  = 12;
    private static final int REGEN_PER_TICK  = 4;
    private static final int HUD_SYNC_PERIOD = 0;

    private static final int SMOKE_PERIOD_TICKS = 3;

    private static final int SHADOW_EXIT_GRAVITY_DELAY_TICKS = 1;

    // >>> ADJUST THIS <<<
    // How many ticks AFTER leaving shadow before energy starts regenerating.
    // 20 = 1 second at 20 tps.
    private static final int SHADOW_REGEN_DELAY_TICKS = 10;

    /* ===================== ASTRAL CONFIG ===================== */
    private static final double ASTRAL_MAX_DISTANCE = 200.0;

    public static final int ASTRAL_MASK_PUSH = 1;
    public static final int ASTRAL_MASK_PULL = 2;

    private static final double ASTRAL_FORCE_RANGE = 12.0;
    private static final double ASTRAL_CONE_COS    = 0.55;
    private static final double ASTRAL_FORCE       = 0.2;
    private static final int    INPUT_STALE_TICKS  = 6;


    // SMOKE //
    private static final int    SHADOW_SMOKE_PARTICLES_PER_BURST = 28; // density
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

        // regen delay after shadow ends
        int shadowRegenDelayTicks = 0;

        boolean astralActive = false;
        Vec3d  astralAnchor = null;
        net.minecraft.registry.RegistryKey<World> astralDim = null;
        boolean prevAllowFlyingAstral, prevFlyingAstral, prevNoClipAstral;

        int   astralMask = 0;
        int   astralMaskTick = 0;
    }
    private static void spawnShadowSmokeCloud(ServerWorld w, ServerPlayerEntity p) {
        var rand = w.getRandom();

        // cloud centered around the player's torso
        Vec3d center = new Vec3d(p.getX(), p.getBodyY(0.55), p.getZ());

        for (int i = 0; i < SHADOW_SMOKE_PARTICLES_PER_BURST; i++) {
            // random direction around player (slightly flattened vertically so it looks like a “cloud”)
            double dx = rand.nextDouble() * 2.0 - 1.0;
            double dy = (rand.nextDouble() * 2.0 - 1.0) * 0.65; // flatter vertically
            double dz = rand.nextDouble() * 2.0 - 1.0;

            Vec3d dir = new Vec3d(dx, dy, dz);
            double len2 = dir.lengthSquared();
            if (len2 < 1.0e-6) continue;
            dir = dir.multiply(1.0 / Math.sqrt(len2));

            double r = SHADOW_SMOKE_RADIUS_MIN + rand.nextDouble() * (SHADOW_SMOKE_RADIUS_MAX - SHADOW_SMOKE_RADIUS_MIN);
            Vec3d pos = center.add(dir.multiply(r));

            // velocity toward the center = “cloud tips” aim inward
            Vec3d vel = center.subtract(pos).normalize().multiply(SHADOW_SMOKE_INWARD_SPEED);

            // count=0 = single particle with directional velocity (offsets used as velocity)
            w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                    pos.x, pos.y, pos.z,
                    0,
                    vel.x, vel.y, vel.z,
                    1.0
            );
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static @NotNull State S(ServerPlayerEntity p) { return STATES.computeIfAbsent(p.getUuid(), k -> new State()); }

    /* ===================== PRIMARY (SHADOW) ===================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        State s = S(player);
        if (!s.shadowActive) {
            if (s.energy <= 0) return;
            if (s.astralActive) stopAstral(player, s, true);
            startShadow(player, s);
        } else {
            stopShadow(player, s);
        }
    }

    private static void startShadow(ServerPlayerEntity p, State s) {
        s.pendingShadowGravityRestore = false;
        s.shadowGravityDelayTicks = 0;

        // no regen delay while actively shadowing
        s.shadowRegenDelayTicks = 0;

        if (p instanceof OddAirSwim air) air.oddities$setAirSwim(true);
        if (p instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(true); // <<< PHASE ON

        p.setSwimming(true);

        p.setInvisible(true);
        p.setInvulnerable(true);
        p.setNoGravity(true);
        p.fallDistance = 0;

        s.shadowActive = true;
        UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, true);
    }

    private static void stopShadow(ServerPlayerEntity p, State s) {
        if (p instanceof OddAirSwim air) air.oddities$setAirSwim(false);
        if (p instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(false); // <<< PHASE OFF

        p.setSwimming(false);

        s.pendingShadowGravityRestore = true;
        s.shadowGravityDelayTicks = Math.max(0, SHADOW_EXIT_GRAVITY_DELAY_TICKS);

        // start regen delay countdown on exit
        s.shadowRegenDelayTicks = Math.max(0, SHADOW_REGEN_DELAY_TICKS);

        p.setInvisible(false);
        p.setInvulnerable(false);
        p.fallDistance = 0;

        s.shadowActive = false;
        UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, false);
    }

    /* ===================== SECONDARY (ASTRAL) ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        State s = S(player);
        if (s.shadowActive) return;
        if (!s.astralActive) startAstral(player, s);
        else                 stopAstral(player, s, true);
    }

    private static void startAstral(ServerPlayerEntity p, State s) {
        s.pendingShadowGravityRestore = false;
        s.shadowGravityDelayTicks = 0;

        if (p instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(true); // <<< PHASE ON

        s.astralActive = true;
        s.astralAnchor = p.getPos();
        s.astralDim    = p.getWorld().getRegistryKey();

        var ab = p.getAbilities();
        s.prevAllowFlyingAstral = ab.allowFlying;
        s.prevFlyingAstral      = ab.flying;
        s.prevNoClipAstral      = p.noClip;

        ab.allowFlying = true;
        ab.flying      = true;

        p.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY, 9999, 0, true, false, false
        ));
        p.noClip       = true;
        p.setNoGravity(true);
        p.setInvisible(true);
        p.setInvulnerable(true);
        p.fallDistance = 0;

        AstralInventory.enter(p);
        p.sendAbilitiesUpdate();
    }

    private static void stopAstral(ServerPlayerEntity p, State s, boolean snapBack) {
        if (snapBack && s.astralAnchor != null && p.getWorld().getRegistryKey() == s.astralDim) {
            p.teleport((ServerWorld)p.getWorld(), s.astralAnchor.x, s.astralAnchor.y, s.astralAnchor.z, p.getYaw(), p.getPitch());
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
        }

        var ab = p.getAbilities();
        ab.allowFlying = s.prevAllowFlyingAstral;
        ab.flying      = s.prevFlyingAstral;

        p.noClip = s.prevNoClipAstral;
        p.setNoGravity(false);
        p.removeStatusEffect(StatusEffects.INVISIBILITY);
        p.setInvisible(false);
        p.setInvulnerable(false);
        p.fallDistance = 0;

        if (p instanceof OddUmbraPhase ph) ph.oddities$setUmbraPhasing(false); // <<< PHASE OFF

        p.sendAbilitiesUpdate();
        AstralInventory.exit(p);

        s.astralActive = false;
        s.astralAnchor = null;
        s.astralMask   = 0;

        {
            net.seep.odd.abilities.power.Power pwr =
                    net.seep.odd.abilities.power.Powers.get("umbra_soul");
            final long cd = (pwr != null)
                    ? Math.max(0L, pwr.secondaryCooldownTicks())
                    : (60L * 20L);

            if (cd > 0) {
                final long now = p.getWorld().getTime();
                final net.seep.odd.abilities.data.CooldownState cds =
                        net.seep.odd.abilities.data.CooldownState.get(p.getServer());
                final String cdKey = (pwr != null) ? (pwr.id() + "#secondary") : "umbra_soul#secondary";

                cds.setLastUse(p.getUuid(), cdKey, now);
                net.seep.odd.abilities.net.PowerNetworking.sendCooldown(p, "secondary", cd);
            }
        }
    }

    /* ===================== SERVER TICK ===================== */

    public static void forceStopAstral(ServerPlayerEntity p) {
        State s = S(p);
        if (s.astralActive) stopAstral(p, s, true);
    }

    public static void serverTick(ServerPlayerEntity p) {
        State s = S(p);

        if (!s.shadowActive && !s.astralActive && s.pendingShadowGravityRestore) {
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
            // Regen delay countdown after leaving shadow
            if (s.shadowRegenDelayTicks > 0) {
                s.shadowRegenDelayTicks--;
            } else {
                if (p.isOnGround() || !p.isFallFlying()) {
                    s.energy = Math.min(MAX_ENERGY, s.energy + REGEN_PER_TICK);
                }
            }
        }

        if (s.astralActive) {
            var ab = p.getAbilities();
            if (!ab.allowFlying) { ab.allowFlying = true; p.sendAbilitiesUpdate(); }
            if (!ab.flying)      { ab.flying = true;      p.sendAbilitiesUpdate(); }
            if (!p.noClip)       p.noClip = true;
            p.setNoGravity(true);

            boolean wrongDim = p.getWorld().getRegistryKey() != s.astralDim;
            boolean tooFar   = s.astralAnchor != null && p.squaredDistanceTo(s.astralAnchor) > (ASTRAL_MAX_DISTANCE * ASTRAL_MAX_DISTANCE);
            if (wrongDim || tooFar) {
                stopAstral(p, s, true);
            } else {

                applyAstralPushPull(p, s);
            }
        }

        if (s.hudSyncCooldown-- <= 0) {
            UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, s.shadowActive);
            s.hudSyncCooldown = HUD_SYNC_PERIOD;
        }
    }

    public static void onAstralInput(ServerPlayerEntity player, int mask) {
        State s = S(player);
        if (!s.astralActive) return;
        s.astralMask = mask;
        s.astralMaskTick = player.age;
    }

    private static void applyAstralPushPull(ServerPlayerEntity p, State s) {
        int mask = s.astralMask;
        if (mask == 0) return;
        if ((p.age - s.astralMaskTick) > INPUT_STALE_TICKS) return;

        boolean doPush = (mask & ASTRAL_MASK_PUSH) != 0;
        boolean doPull = (mask & ASTRAL_MASK_PULL) != 0;
        if (!(doPush || doPull)) return;

        Vec3d eye  = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f).normalize();

        Box search = new Box(eye, eye.add(look.multiply(ASTRAL_FORCE_RANGE))).expand(3.0);
        var list = p.getWorld().getOtherEntities(p, search,
                e -> e.isAlive() && e instanceof LivingEntity);

        for (Entity e : list) {
            Vec3d targetCenter = e.getPos().add(0, (e.getHeight() * 0.5), 0);
            Vec3d toTarget = targetCenter.subtract(eye);
            double dist = toTarget.length();
            if (dist <= 0.001 || dist > ASTRAL_FORCE_RANGE) continue;

            Vec3d dirTo = toTarget.normalize();
            double facing = dirTo.dotProduct(look);
            if (facing < ASTRAL_CONE_COS) continue;

            double falloff = 1.0 - (dist / ASTRAL_FORCE_RANGE);
            if (falloff <= 0) continue;

            Vec3d impulse;
            if (doPush) impulse = look.multiply(ASTRAL_FORCE * falloff);
            else {
                Vec3d pullDir = p.getPos().subtract(e.getPos()).normalize();
                impulse = pullDir.multiply(ASTRAL_FORCE * falloff);
            }

            e.addVelocity(impulse.x, impulse.y, impulse.z);
            if (e instanceof LivingEntity le) le.fallDistance = 0;
            e.velocityModified = true;
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
