// FILE: src/main/java/net/seep/odd/abilities/wizard/WizardCasting.java
package net.seep.odd.abilities.wizard;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.entity.*;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WizardCasting {
    private WizardCasting() {}

    // ---- Costs ----
    public static final float COST_FIRE_NORMAL  = 10f;
    public static final float COST_FIRE_BIG     = 20f;

    public static final float COST_WATER_NORMAL = 20f;
    public static final float COST_WATER_BIG    = 35f;

    public static final float COST_AIR_NORMAL   = 5f;
    public static final float COST_AIR_BIG      = 40f;

    public static final float COST_EARTH_NORMAL = 20f;
    public static final float COST_EARTH_BIG    = 30f;

    public static final double BIG_TARGET_RANGE = 48.0;

    /* ---------- POWERLESS gate ---------- */
    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    /* =========================================================
       ✅ REAL secondary cooldown driven by the normal cooldown system
       BUT we allow ONE cast per "open session" (no blocking while selecting/aiming).

       - On open (only if not already on cooldown):
         start base cooldown (50s) AND mark a "session active" window.

       - On cast:
         allowed ONLY if session active
         then adjust remaining by +/- delta and end session.

       - On cancel:
         if session active => cooldown set to 0 and session ends.
       ========================================================= */

    private static final String CD_LAST_KEY        = "wizard#secondary";
    private static final String CD_LEN_KEY         = "wizard#secondary_len";
    private static final String SESSION_UNTIL_KEY  = "wizard#secondary_session_until";

    /** Per-combo delta applied to REMAINING cooldown after cast. (+ adds time, - reduces time) */
    public static long comboCooldownDeltaTicks(WizardCombo combo) {
        if (combo == null) return 0L;
        return switch (combo) {
            case STEAM_CLOUD      -> +(20L * 15L); // +10s
            case FIRE_TORNADO     -> +(20L * 25L);  // +6s
            case LIFE_RESTORATION -> +(20L * 20L);  // +8s
            case SONIC_SCREECH    -> +(20L * 15L);  // +2s
            case SWAPPERINO       -> +(20L * 5L); // +12s
            case METEOR_STRIKE    -> +(20L * 40L); // +18s
        };
    }

    /** Remaining based on per-player stored cooldown length. */
    public static long comboCooldownRemainingTicks(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return 0L;

        CooldownState cds = CooldownState.get(p.getServer());
        long now  = p.getServerWorld().getTime();

        long last = cds.getLastUse(p.getUuid(), CD_LAST_KEY);
        long len  = cds.getLastUse(p.getUuid(), CD_LEN_KEY);

        if (len <= 0L) return 0L;

        long dt = now - last;
        if (dt < 0L) dt = 0L;

        long left = len - dt;
        return Math.max(0L, left);
    }

    private static boolean sessionActive(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return false;
        CooldownState cds = CooldownState.get(p.getServer());
        long now = p.getServerWorld().getTime();
        return cds.getLastUse(p.getUuid(), SESSION_UNTIL_KEY) > now;
    }

    private static void endSession(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return;
        CooldownState cds = CooldownState.get(p.getServer());
        cds.setLastUse(p.getUuid(), SESSION_UNTIL_KEY, 0L);
    }

    /** Called by WizardPower when the wheel is opened (ONLY when not already on cooldown). */
    public static void startComboCooldownOnOpen(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return;

        CooldownState cds = CooldownState.get(p.getServer());
        long now = p.getServerWorld().getTime();

        long base = WizardPower.COMBO_OPEN_COOLDOWN_TICKS;

        // store per-player length and start tick
        cds.setLastUse(p.getUuid(), CD_LAST_KEY, now);
        cds.setLastUse(p.getUuid(), CD_LEN_KEY, base);

        // open a "session" long enough to aim/confirm (covers the whole base window)
        cds.setLastUse(p.getUuid(), SESSION_UNTIL_KEY, now + base + 20L);

        // keep the override variable updated too (what you wanted)
        WizardPower.COMBO_SECONDARY_COOLDOWN_TICKS = base;

        // normal cooldown bar
        PowerNetworking.sendCooldown(p, "secondary", base);
    }

    /** C2S cancel: only clears if session is active (prevents cheesing real cooldown). */
    public static void cancelComboCooldown(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return;

        if (!sessionActive(p)) return; // don't allow clearing real cooldowns

        CooldownState cds = CooldownState.get(p.getServer());
        long now = p.getServerWorld().getTime();

        cds.setLastUse(p.getUuid(), CD_LAST_KEY, now);
        cds.setLastUse(p.getUuid(), CD_LEN_KEY, 0L);
        cds.setLastUse(p.getUuid(), SESSION_UNTIL_KEY, 0L);

        WizardPower.COMBO_SECONDARY_COOLDOWN_TICKS = 0L;
        PowerNetworking.sendCooldown(p, "secondary", 0L);
    }

    /** After cast: adjust remaining by delta, restart cooldown from NOW with new remaining, and end session. */
    private static void finalizeCooldownAfterCast(ServerPlayerEntity p, WizardCombo combo) {
        if (p == null || p.getServer() == null) return;

        CooldownState cds = CooldownState.get(p.getServer());
        long now = p.getServerWorld().getTime();

        long remaining = comboCooldownRemainingTicks(p);
        long delta = comboCooldownDeltaTicks(combo);

        long newRemaining = remaining + delta;
        if (newRemaining < 0L) newRemaining = 0L;

        long max = 20L * 180L; // 3 minutes cap
        if (newRemaining > max) newRemaining = max;

        // restart from now with new remaining
        cds.setLastUse(p.getUuid(), CD_LAST_KEY, now);
        cds.setLastUse(p.getUuid(), CD_LEN_KEY, newRemaining);

        endSession(p);

        WizardPower.COMBO_SECONDARY_COOLDOWN_TICKS = newRemaining;
        PowerNetworking.sendCooldown(p, "secondary", newRemaining);
    }

    public static int chargeTicksFor(WizardElement e) {
        return switch (e) {
            case FIRE  -> 20 * 2;
            case WATER -> 20 * 1;
            case AIR   -> 20 * 3;
            case EARTH -> 20 * 3;
        };
    }

    public static void castNormal(ServerPlayerEntity p) {
        if (isPowerless(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        WizardElement e = WizardPower.getElement(p);
        float cost = switch (e) {
            case FIRE  -> COST_FIRE_NORMAL;
            case WATER -> COST_WATER_NORMAL;
            case AIR   -> COST_AIR_NORMAL;
            case EARTH -> COST_EARTH_NORMAL;
        };

        if (!trySpendMana(p, cost)) return;

        switch (e) {
            case FIRE  -> spawnFireProjectile(sw, p);
            case WATER -> spawnWaterProjectile(sw, p);
            case EARTH -> spawnEarthProjectile(sw, p);
            case AIR   -> doAirWindX(sw, p);
        }
    }

    public static void castBig(ServerPlayerEntity p) {
        if (isPowerless(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        Vec3d at = raycastCirclePos(p, BIG_TARGET_RANGE);
        if (at == null) {
            at = p.getPos().add(p.getRotationVector().normalize().multiply(6.0)).add(0, 0.01, 0);
        }

        castBigAt(p, at);
    }

    public static void castBigAt(ServerPlayerEntity p, Vec3d at) {
        if (isPowerless(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        WizardElement e = WizardPower.getElement(p);
        float cost = switch (e) {
            case FIRE  -> COST_FIRE_BIG;
            case WATER -> COST_WATER_BIG;
            case AIR   -> COST_AIR_BIG;
            case EARTH -> COST_EARTH_BIG;
        };
        if (!trySpendMana(p, cost)) return;

        switch (e) {
            case FIRE  -> doFireBigAt(sw, p, at);
            case WATER -> doWaterBigAt(sw, p, at);
            case AIR   -> spawnTornadoAt(sw, p, at, false);
            case EARTH -> spawnEarthquakeAt(sw, p, at);
        }
    }

    public static void castComboAt(ServerPlayerEntity p, WizardCombo combo, Vec3d at) {
        if (isPowerless(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        // stop confirm-click also firing normal casts
        WizardSwingSuppress.blockNormalCasts(p, 4);

        // ✅ IMPORTANT: do NOT block because cooldown is ticking (it started on open)
        // Only allow casting if the combo wheel session is active.
        if (!sessionActive(p)) return;

        boolean cast = false;

        switch (combo) {
            case STEAM_CLOUD -> {
                if (!trySpendMana(p, 0f)) return;
                spawnSteamCloud(sw, at);
                cast = true;
            }
            case FIRE_TORNADO -> {
                if (!trySpendMana(p, 0f)) return;
                spawnTornadoAt(sw, p, at, true);
                cast = true;
            }
            case LIFE_RESTORATION -> {
                if (!trySpendMana(p, 0f)) return;
                doLifeRestoration(sw, p);
                cast = true;
            }
            case SONIC_SCREECH -> {
                if (!trySpendMana(p, 0f)) return;
                doSonicScreechUp(sw, p, at);
                cast = true;
            }
            case SWAPPERINO -> {
                if (!trySpendMana(p, 0f)) return;
                doSwapperino(sw, p, at);
                cast = true;
            }
            case METEOR_STRIKE -> {
                if (!trySpendMana(p, 0f)) return;
                spawnMeteorEntity(sw, p, at);
                cast = true;
            }
            default -> {
                p.sendMessage(Text.literal("Unknown combo."), true);
                return;
            }
        }

        if (cast) finalizeCooldownAfterCast(p, combo);
    }

    /* ===================== existing spell code below (UNCHANGED) ===================== */

    private static void spawnFireProjectile(ServerWorld sw, ServerPlayerEntity p) {
        WizardFireProjectileEntity proj = new WizardFireProjectileEntity(ModEntities.WIZARD_FIRE_PROJECTILE, p, sw);
        proj.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
        proj.setVelocity(p, p.getPitch(), p.getYaw(), 0.0f, 1.7f, 0.5f);
        sw.spawnEntity(proj);

        sw.playSound(null, p.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.85f, 1.05f);
    }

    private static void spawnWaterProjectile(ServerWorld sw, ServerPlayerEntity p) {
        WizardWaterProjectileEntity proj = new WizardWaterProjectileEntity(ModEntities.WIZARD_WATER_PROJECTILE, p, sw);
        proj.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
        proj.setVelocity(p, p.getPitch(), p.getYaw(), 0.0f, 1.6f, 0.5f);
        sw.spawnEntity(proj);

        sw.playSound(null, p.getBlockPos(), SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 0.9f, 1.25f);
    }

    private static void spawnEarthProjectile(ServerWorld sw, ServerPlayerEntity p) {
        WizardEarthProjectileEntity proj = new WizardEarthProjectileEntity(ModEntities.WIZARD_EARTH_PROJECTILE, p, sw);
        proj.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
        proj.setVelocity(p, p.getPitch(), p.getYaw(), 0.0f, 1.10f, 0.25f);
        sw.spawnEntity(proj);

        sw.playSound(null, p.getBlockPos(), ModSounds.SHADOW_KUNAI_THROW, SoundCategory.PLAYERS, 3.95f, 0.1f);
    }

// in WizardCasting.java

    private static void doAirWindX(ServerWorld sw, ServerPlayerEntity p) {
        Vec3d forward = p.getRotationVector().normalize();
        Vec3d worldUp = new Vec3d(0, 1, 0);

        // Build a stable basis in the plane perpendicular to the view direction
        Vec3d right = forward.crossProduct(worldUp);
        if (right.lengthSquared() < 1.0e-6) {
            // looking straight up/down fallback (yaw-only right)
            Vec3d yawForward = Vec3d.fromPolar(0.0f, p.getYaw()).normalize();
            right = yawForward.crossProduct(worldUp);
        }
        right = right.normalize();

        Vec3d up = right.crossProduct(forward).normalize(); // "camera-up" in the view plane

        // ✅ Rotate the WHOLE shape around forward (roll)
        final double ROLL_DEG = -45.0; // <-- tweak this if you want it to “tilt” more/less
        double roll = Math.toRadians(ROLL_DEG);
        right = rotateAroundAxis(right, forward, roll);
        up    = rotateAroundAxis(up,    forward, roll);

        // ✅ Position + size of the X
        Vec3d origin = p.getPos().add(0, 1.1, 0);
        double startDist = 1.0;
        double endDist   = 6.5;   // <-- forward reach
        double halfW     = 1.35;  // <-- X width
        double halfH     = 0.95;  // <-- X height

        // Two diagonals stretched forward
        Vec3d a1 = origin.add(forward.multiply(startDist)).add(right.multiply(-halfW)).add(up.multiply(+halfH));
        Vec3d b1 = origin.add(forward.multiply(endDist))  .add(right.multiply(+halfW)).add(up.multiply(-halfH));

        Vec3d a2 = origin.add(forward.multiply(startDist)).add(right.multiply(-halfW)).add(up.multiply(-halfH));
        Vec3d b2 = origin.add(forward.multiply(endDist))  .add(right.multiply(+halfW)).add(up.multiply(+halfH));

        // ✅ Hit detection along the diagonals (same damage + same knockback)
        double hitR  = 0.95;
        double hitR2 = hitR * hitR;

        Box box = p.getBoundingBox()
                .stretch(forward.multiply(endDist))
                .expand(3.0, 2.3, 3.0);

        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != p);

        for (LivingEntity le : list) {
            Vec3d target = le.getPos().add(0, le.getHeight() * 0.5, 0);

            double d1 = distSqPointToSegment(target, a1, b1);
            double d2 = distSqPointToSegment(target, a2, b2);
            if (Math.min(d1, d2) > hitR2) continue;

            le.damage(sw.getDamageSources().magic(), 6.0f);

            // keep knockback EXACTLY the same as your original
            Vec3d kb = forward.multiply(0.85).add(0, 0.22, 0);
            le.addVelocity(kb.x, kb.y, kb.z);
            le.velocityModified = true;
        }

        // ✅ Visuals: keep the big curved arc look
        int steps = 14;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;

            Vec3d p1 = a1.lerp(b1, t);
            Vec3d p2 = a2.lerp(b2, t);

            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                    p1.x, p1.y, p1.z, 1, 0, 0, 0, 0);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                    p2.x, p2.y, p2.z, 1, 0, 0, 0, 0);

            // tiny thickness
            if ((i % 3) == 0) {
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                        p1.x, p1.y + 0.08, p1.z, 1, 0, 0, 0, 0);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                        p2.x, p2.y + 0.08, p2.z, 1, 0, 0, 0, 0);
            }
        }

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 1.25f);
    }

    // Rodrigues rotation: rotate vector v around axis (unit or not) by angle radians
    private static Vec3d rotateAroundAxis(Vec3d v, Vec3d axis, double angle) {
        Vec3d k = axis.normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        // v_rot = v*cos + (k x v)*sin + k*(k·v)*(1-cos)
        Vec3d term1 = v.multiply(cos);
        Vec3d term2 = k.crossProduct(v).multiply(sin);
        Vec3d term3 = k.multiply(k.dotProduct(v) * (1.0 - cos));
        return term1.add(term2).add(term3);
    }

    // add this helper somewhere in WizardCasting.java (private static is fine)
    private static double distSqPointToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double ab2 = ab.lengthSquared();
        if (ab2 < 1.0e-9) return p.squaredDistanceTo(a);

        double t = p.subtract(a).dotProduct(ab) / ab2;
        t = MathHelper.clamp(t, 0.0, 1.0);

        Vec3d q = a.add(ab.multiply(t));
        return p.squaredDistanceTo(q);
    }

    private static void doFireBigAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.15f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, at.x, at.y + 0.35, at.z,
                120, 1.1, 0.55, 1.1, 0.02);

        float power = 1.8f * 0.70f;
        sw.createExplosion(null, at.x, at.y, at.z, power, World.ExplosionSourceType.NONE);

        BlockPos base = BlockPos.ofFloored(at.x, at.y - 0.5, at.z);
        int r = 3;

        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            BlockPos pos = base.add(dx, 0, dz);
            if (pos.getSquaredDistance(base) > r * r) continue;

            if (sw.random.nextFloat() > 0.25f) continue;

            BlockPos above = pos.up();
            if (!sw.getBlockState(above).isAir()) continue;
            if (sw.getBlockState(pos).isAir()) continue;

            if (Blocks.FIRE.getDefaultState().canPlaceAt(sw, above)) {
                sw.setBlockState(above, Blocks.FIRE.getDefaultState(), 3);
            }
        }
    }

    private static void doWaterBigAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        LivingEntity target = raycastLiving(p, 32.0);

        if (target != null) {
            target.setFrozenTicks(220);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 90, 4, false, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 90, 1, false, true, true));

            WizardTempEntities.spawnIceCube(sw, target, 90);
        } else {
            float rad = 3.0f;
            Box box = new Box(at.x - rad, at.y - 1.0, at.z - rad, at.x + rad, at.y + 3.0, at.z + rad);
            List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != p);
            for (LivingEntity le : list) {
                if (le.squaredDistanceTo(at) > rad * rad) continue;
                le.setFrozenTicks(180);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 3, false, true, true));
                WizardTempEntities.spawnIceEruption(sw, le.getPos(), 60);
            }
        }

        sw.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0f, 1.35f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE, at.x, at.y + 0.15, at.z,
                120, 1.6, 0.35, 1.6, 0.02);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, at.x, at.y + 0.10, at.z,
                60, 1.2, 0.15, 1.2, 0.01);
    }

    private static void spawnTornadoAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at, boolean fire) {
        Vec3d dir = p.getRotationVector().normalize();

        if (!fire) {
            WizardTornadoEntity t = new WizardTornadoEntity(ModEntities.WIZARD_TORNADO, sw);
            t.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), p.getPitch());
            t.setDirection(dir);
            t.setOwnerId(p.getUuid());
            sw.spawnEntity(t);
        } else {
            WizardFireTornadoEntity t = new WizardFireTornadoEntity(ModEntities.WIZARD_FIRE_TORNADO, sw);
            t.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), p.getPitch());
            t.setDirection(dir);
            t.setOwnerId(p.getUuid());
            sw.spawnEntity(t);

            sw.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.AMBIENT, 0.9f, 0.85f);
        }
    }

    private static void spawnEarthquakeAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        WizardEarthquakeEntity eq = new WizardEarthquakeEntity(ModEntities.WIZARD_EARTHQUAKE, sw);
        eq.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        eq.setOwnerId(p.getUuid());
        sw.spawnEntity(eq);

        sw.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.PLAYERS, 1.1f, 0.65f);
    }

    private static void spawnSteamCloud(ServerWorld sw, Vec3d pos) {
        BlockPos base = BlockPos.ofFloored(pos);
        int r = 3;

        sw.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0f, 1.0f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.25, pos.z,
                40, 1.2, 0.4, 1.2, 0.02);

        for (int i = 0; i < 5; i++) {
            int ox = sw.random.nextInt(r * 2 + 1) - r;
            int oz = sw.random.nextInt(r * 2 + 1) - r;

            BlockPos p = base.add(ox, 0, oz);

            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
            if (bolt == null) continue;

            bolt.refreshPositionAfterTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            sw.spawnEntity(bolt);
        }
    }

    private static void doLifeRestoration(ServerWorld sw, ServerPlayerEntity p) {
        int r = 12;

        sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING,
                p.getX(), p.getY() + 1.0, p.getZ(),
                140, 1.2, 0.9, 1.2, 0.02);

        Box box = p.getBoundingBox().expand(r, 5, r);
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
        for (LivingEntity le : list) {
            if (le.squaredDistanceTo(p) > r * r) continue;
            if (!p.canSee(le)) continue;
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1, false, true, true));
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0, false, true, true));
        }
    }

    private static void doSonicScreechUp(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 1.10f);

        for (int i = 0; i < 26; i++) {
            double yy = at.y + 0.15 + i * 0.22;
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, at.x, yy, at.z, 3, 0.12, 0.02, 0.12, 0.01);
            if ((i % 4) == 0) {
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.SONIC_BOOM, at.x, yy, at.z, 1, 0, 0, 0, 0);
            }
        }

        float radius = 3.5f;
        Box box = new Box(at.x - radius, at.y - 1.0, at.z - radius, at.x + radius, at.y + 4.0, at.z + radius);
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());

        for (LivingEntity le : list) {
            if (le == p) continue;
            if (le.squaredDistanceTo(at) > radius * radius) continue;

            le.damage(sw.getDamageSources().magic(), 12.0f);
            le.addVelocity(0.0, 0.55, 0.0);
            le.velocityModified = true;
        }
    }

    private static void doSwapperino(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        double r = 3.0;

        Vec3d aCenter = p.getPos();
        Vec3d bCenter = at;
        Vec3d delta = bCenter.subtract(aCenter);

        Box boxA = new Box(aCenter.x - r, aCenter.y - 2, aCenter.z - r, aCenter.x + r, aCenter.y + 3, aCenter.z + r);
        Box boxB = new Box(bCenter.x - r, bCenter.y - 2, bCenter.z - r, bCenter.x + r, bCenter.y + 3, bCenter.z + r);

        List<LivingEntity> listA = sw.getEntitiesByClass(LivingEntity.class, boxA, WizardCasting::canSwapperinoTeleport);
        List<LivingEntity> listB = sw.getEntitiesByClass(LivingEntity.class, boxB, WizardCasting::canSwapperinoTeleport);

        Set<UUID> aIds = new HashSet<>();
        aIds.add(p.getUuid());
        for (LivingEntity e : listA) aIds.add(e.getUuid());

        Set<UUID> bIds = new HashSet<>();
        for (LivingEntity e : listB) bIds.add(e.getUuid());

        for (UUID id : aIds) {
            Entity e = sw.getEntity(id);
            if (e == null) continue;
            Vec3d np = e.getPos().add(delta);
            teleportEntity(sw, e, np.x, np.y, np.z);
        }

        for (UUID id : bIds) {
            if (aIds.contains(id)) continue;
            Entity e = sw.getEntity(id);
            if (e == null) continue;
            Vec3d np = e.getPos().subtract(delta);
            teleportEntity(sw, e, np.x, np.y, np.z);
        }

        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.35f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 90, 1.2, 0.8, 1.2, 0.15);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 90, 1.2, 0.8, 1.2, 0.15);
    }

    private static boolean canSwapperinoTeleport(LivingEntity le) {
        if (le == null || !le.isAlive()) return false;
        if (le.isInvulnerable()) return false;

        if (le instanceof ServerPlayerEntity sp) {
            if (sp.isCreative() || sp.isSpectator()) return false;
        }

        return le.getMaxHealth() <= 60.0f;
    }

    private static Vec3d findSafeAbove(ServerWorld sw, Entity e, double x, double y, double z) {
        double baseY = Math.floor(y) + 1.05;

        var dims = e.getDimensions(e.getPose());
        for (int i = 0; i < 8; i++) {
            double yy = baseY + i;
            Box box = dims.getBoxAt(x, yy, z);
            if (sw.isSpaceEmpty(e, box)) return new Vec3d(x, yy, z);
        }
        return new Vec3d(x, y + 1.2, z);
    }

    private static void spawnMeteorEntity(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);

        WizardMeteorEntity m = new WizardMeteorEntity(ModEntities.WIZARD_METEOR, sw);
        m.setOwnerId(p.getUuid());
        m.setTarget(at);

        m.refreshPositionAndAngles(at.x, at.y + 46.0, at.z, 0f, 0f);
        sw.spawnEntity(m);
    }

    private static void teleportEntity(ServerWorld sw, Entity e, double x, double y, double z) {
        Vec3d safe = findSafeAbove(sw, e, x, y, z);

        if (e instanceof ServerPlayerEntity sp) {
            sp.teleport(sw, safe.x, safe.y, safe.z, sp.getYaw(), sp.getPitch());
            sp.setVelocity(Vec3d.ZERO);
        } else {
            e.requestTeleport(safe.x, safe.y, safe.z);
            e.setVelocity(Vec3d.ZERO);
        }
    }

    public static CapybaraFamiliarEntity getOrEnsureFamiliar(ServerWorld sw, ServerPlayerEntity owner) {
        UUID id = owner.getUuid();

        Box box = owner.getBoundingBox().expand(96.0);
        List<CapybaraFamiliarEntity> list =
                sw.getEntitiesByClass(CapybaraFamiliarEntity.class, box, e -> e.isAlive() && id.equals(e.getOwnerUuidSafe()));

        if (!list.isEmpty()) {
            for (int i = 1; i < list.size(); i++) list.get(i).discard();
            return list.get(0);
        }

        CapybaraFamiliarEntity fam = new CapybaraFamiliarEntity(ModEntities.CAPYBARA_FAMILIAR, sw);
        fam.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), owner.getPitch());
        fam.setOwnerUuid(id);
        sw.spawnEntity(fam);
        return fam;
    }

    public static void despawnFamiliar(ServerWorld sw, UUID ownerId) {
        BlockPos spawn = sw.getSpawnPos();
        Box box = new Box(spawn).expand(512.0);

        List<CapybaraFamiliarEntity> list =
                sw.getEntitiesByClass(CapybaraFamiliarEntity.class, box, e -> ownerId.equals(e.getOwnerUuidSafe()));

        for (CapybaraFamiliarEntity e : list) e.discard();
    }

    public static LivingEntity raycastLiving(ServerPlayerEntity p, double range) {
        Vec3d start = p.getCameraPosVec(1f);
        Vec3d end = start.add(p.getRotationVector().multiply(range));

        Box box = p.getBoundingBox().stretch(p.getRotationVector().multiply(range)).expand(1.0);
        EntityHitResult ehr = ProjectileUtil.raycast(
                p, start, end, box,
                e -> (e instanceof LivingEntity le) && le.isAlive() && e != p,
                range * range
        );

        if (ehr != null && ehr.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    public static Vec3d raycastPos(ServerPlayerEntity p, double range) {
        Vec3d start = p.getCameraPosVec(1f);
        Vec3d end = start.add(p.getRotationVector().multiply(range));
        HitResult hr = p.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                p
        ));
        return hr.getPos();
    }

    private static Vec3d raycastCirclePos(ServerPlayerEntity p, double range) {
        HitResult hr = p.raycast(range, 1.0f, false);
        if (hr.getType() != HitResult.Type.BLOCK) return null;

        BlockPos bp = ((BlockHitResult) hr).getBlockPos();
        return new Vec3d(bp.getX() + 0.5, bp.getY() + 1.01, bp.getZ() + 0.5);
    }

    private static boolean trySpendMana(ServerPlayerEntity p, float cost) {
        if (isPowerless(p)) return false;

        float mana = WizardPower.getMana(p);
        if (mana < cost) {
            p.sendMessage(Text.literal("Not enough mana."), true);
            return false;
        }
        WizardPower.setMana(p, mana - cost);
        return true;
    }
}