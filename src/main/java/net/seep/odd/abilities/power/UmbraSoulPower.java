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
import net.seep.odd.abilities.client.hud.AstralHudOverlay;
import net.seep.odd.abilities.net.UmbraNet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Umbra Soul:
 * Primary  = Shadow Form (auto-glide toward look, drains resource).
 * Secondary = Astral Projection (creative-like flight + noclip, 200m leash, cone push/pull).
 */
public final class UmbraSoulPower implements Power {

    @Override public String id() { return "umbra_soul"; }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return  60L * 20L ; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/umbra_cloud.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/umbra_possess.png"); // set this texture
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
    private static final int MAX_ENERGY      = 20 * 20;  // 10s at 20 tps
    private static final int DRAIN_PER_TICK  = 9;        // drain while active
    private static final int REGEN_PER_TICK  = 4;        // regen while off
    private static final double H_SPEED      = 1.25;     // shadow glide
    private static final double V_SPEED      = 1.25;
    private static final double SMOOTHING    = 0.35;
    private static final int HUD_SYNC_PERIOD = 0;

    // tiny smoke while shadow
    private static final int SMOKE_PERIOD_TICKS = 3;

    /* ===================== ASTRAL CONFIG ===================== */
    private static final double ASTRAL_MAX_DISTANCE = 200.0;

    // cone push/pull
    public static final int ASTRAL_MASK_PUSH = 1;
    public static final int ASTRAL_MASK_PULL = 2;

    private static final double ASTRAL_FORCE_RANGE = 12.0;
    private static final double ASTRAL_CONE_COS    = 0.55;  // ~57°
    private static final double ASTRAL_FORCE       = 0.2;  // per tick impulse
    private static final int    INPUT_STALE_TICKS  = 6;     // ignore old masks

    /* ===================== STATE ===================== */
    private static final class State {
        // shadow
        int energy = MAX_ENERGY;
        boolean shadowActive = false;
        boolean prevAllowFlying, prevFlying;

        int hudSyncCooldown = 0;

        // astral
        boolean astralActive = false;
        Vec3d  astralAnchor = null;
        net.minecraft.registry.RegistryKey<World> astralDim = null;
        boolean prevAllowFlyingAstral, prevFlyingAstral, prevNoClipAstral;

        // client input (astral)
        int   astralMask = 0;
        int   astralMaskTick = 0;
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
        var ab = p.getAbilities();
        s.prevAllowFlying = ab.allowFlying;
        s.prevFlying      = ab.flying;

        ab.allowFlying = true;
        ab.flying      = true;
        p.sendAbilitiesUpdate();

        p.setInvisible(true);
        p.setInvulnerable(true);
        p.setNoGravity(true);
        p.fallDistance = 0;

        s.shadowActive = true;
        UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, true);
    }

    private static void stopShadow(ServerPlayerEntity p, State s) {
        var ab = p.getAbilities();
        ab.allowFlying = s.prevAllowFlying;
        ab.flying      = s.prevFlying;
        p.sendAbilitiesUpdate();

        p.setInvisible(false);
        p.setInvulnerable(false);
        p.setNoGravity(false);
        p.fallDistance = 0;

        p.setVelocity(Vec3d.ZERO);
        p.velocityModified = true;

        s.shadowActive = false;
        UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, false);
    }

    /* ===================== SECONDARY (ASTRAL) ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        State s = S(player);
        if (s.shadowActive) return; // not during shadow
        if (!s.astralActive) startAstral(player, s);
        else                 stopAstral(player, s, true);
    }

    private static void startAstral(ServerPlayerEntity p, State s) {
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
        p.noClip       = s.prevNoClipAstral;
        p.setNoGravity(false);
        p.removeStatusEffect (StatusEffects.INVISIBILITY);
        p.setInvisible(false);
        p.setInvulnerable(false);
        p.fallDistance = 0;
        p.sendAbilitiesUpdate();
        AstralInventory.exit(p);

        s.astralActive = false;
        s.astralAnchor = null;
        s.astralMask   = 0;
    
        // Start normal secondary cooldown after Astral ends (user or forced)
        {
            // Prefer reading the cooldown from the registered power; fallback to 60s if not present
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
        if (s.astralActive) {
            // snapBack = true to return to anchor, matches normal toggle stop path
            stopAstral(p, s, true);
        }
    }

    public static void serverTick(ServerPlayerEntity p) {
        State s = S(p);

        // ---- SHADOW FORM ----
        if (s.shadowActive) {
            s.energy = Math.max(0, s.energy - DRAIN_PER_TICK);

            var ab = p.getAbilities();
            ab.allowFlying = true;
            ab.flying      = true;
            p.sendAbilitiesUpdate();

            p.setInvisible(true);
            p.setInvulnerable(true);
            p.setNoGravity(true);
            p.fallDistance = 0;
            p.setOnGround(false);
            p.setSprinting(false);

            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 4, true, false, false));

            Vec3d look = p.getRotationVec(1.0f).normalize();
            Vec3d desired = new Vec3d(look.x * H_SPEED, look.y * V_SPEED, look.z * H_SPEED);
            Vec3d cur = p.getVelocity();
            Vec3d blended = cur.lerp(desired, SMOOTHING);
            p.setVelocity(blended);
            p.velocityModified = true;

            if (p.age % SMOKE_PERIOD_TICKS == 0) {
                ((ServerWorld)p.getWorld()).spawnParticles(
                        net.minecraft.particle.ParticleTypes.SMOKE,
                        p.getX(), p.getBodyY(0.5), p.getZ(),
                        2, 0.15, 0.10, 0.15, 0.01
                );
                ((ServerWorld)p.getWorld()).spawnParticles(
                        net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                        p.getX(), p.getBodyY(0.5), p.getZ(),
                        1, 0.10, 0.06, 0.10, 0.0
                );
            }

            if (s.energy <= 0) stopShadow(p, s);
        } else {
            if (p.isOnGround() || !p.isFallFlying()) {
                s.energy = Math.min(MAX_ENERGY, s.energy + REGEN_PER_TICK);
            }
        }

        // ---- ASTRAL PROJECTION ----
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
                if (p.age % 5 == 0) {
                    ((ServerWorld)p.getWorld()).spawnParticles(
                            net.minecraft.particle.ParticleTypes.ASH,
                            p.getX(), p.getEyeY() - 0.2, p.getZ(),
                            1, 0.02, 0.02, 0.02, 0.0
                    );
                }
                applyAstralPushPull(p, s);
            }
        }

        // ---- HUD SYNC (throttled) ----
        if (s.hudSyncCooldown-- <= 0) {
            UmbraNet.syncShadowHud(p, s.energy, MAX_ENERGY, s.shadowActive);
            s.hudSyncCooldown = HUD_SYNC_PERIOD;
        }
    }

    /* ===================== ASTRAL INPUT (from client) ===================== */

    public static void onAstralInput(ServerPlayerEntity player, int mask) {
        State s = S(player);
        if (!s.astralActive) return;
        s.astralMask = mask;
        s.astralMaskTick = player.age;
    }

    /* ===================== ASTRAL FORCE LOGIC ===================== */

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
            if (doPush) {
                impulse = look.multiply(ASTRAL_FORCE * falloff);
            } else {
                // pull toward the player's current position
                Vec3d pullDir = p.getPos().subtract(e.getPos()).normalize();
                impulse = pullDir.multiply(ASTRAL_FORCE * falloff);
            }

            e.addVelocity(impulse.x, impulse.y, impulse.z);
            if (e instanceof LivingEntity le) le.fallDistance = 0;
            e.velocityModified = true;
        }
    }

    /* ===================== TARGETING (kept if you ever need it) ===================== */

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
