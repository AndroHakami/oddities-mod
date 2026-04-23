package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.Explosion;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.zerosuit.ZeroSuitCPM;
import net.seep.odd.abilities.zerosuit.ZeroSuitNet;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.zerosuit.ZeroBeamEntity;
import net.seep.odd.entity.zerosuit.ZeroGrenadeEntity;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.*;

public final class ZeroSuitPower implements Power, ChargedPower {

    /* ======================= PRIMARY: frag grenade ======================= */
    private static final int   GRENADE_MAX_CHARGES      = 2;
    private static final int   GRENADE_RECHARGE_T       = 20 * 6;
    private static final float GRENADE_THROW_SPEED      = 1.05f;
    private static final float GRENADE_THROW_UP_BIAS    = 0.18f;
    private static final float GRENADE_THROW_SOUND_VOL  = 1.0f;


    /* ======================= SECONDARY: blast ======================= */
    private static final int   BLAST_CHARGE_MAX_T  = 20 * 4;
    private static final float BLAST_MAX_DAMAGE    = 12.0f;
    private static final int   BLAST_RANGE_BLOCKS  = 68;
    private static final int   BLAST_VIS_TICKS     = 18;

    private static final double BLAST_RADIUS_MIN    = 0.65;
    private static final double BLAST_RADIUS_MAX    = 2.40;
    private static final double BLAST_CORE_FRACTION = 0.55;

    private static final double BLAST_KB_BASE       = 1.55;
    private static final double BLAST_KB_MAX_ADD    = 7.25;
    private static final double BLAST_KB_CORE_MULT  = 1.35;
    private static final double BLAST_KB_UP_BASE    = 0.06;
    private static final double BLAST_KB_UP_MAX_ADD = 0.16;

    private static final double SELF_RECOIL_BASE    = 0.28;
    private static final double SELF_RECOIL_MAX_ADD = 0.95;

    /**
     * ✅ Now used as: "how long after getting blasted you are immune to FALL damage"
     * (instead of slow-falling / gravity changes)
     */
    private static final int BEAM_NOFALL_TICKS = 20 * 12;

    private static final int HUD_SYNC_EVERY   = 2;

    /* ======================= mobility support ======================= */
    private static final int JETPACK_FUEL_MAX          = 20;
    private static final int JETPACK_DRAIN_PER_TICK    = 4;   // while holding space
    private static final int JETPACK_RECHARGE_PER_TICK = 1;   // while not thrusting
    private static final double JETPACK_IMPULSE        = 0.12; // velocity add per tick
    private static final double JETPACK_MAX_SPEED      = 1.55; // clamp speed
    private static final int JETPACK_HUD_SYNC_EVERY    = 2;
    private static final int JETPACK_FLY_SOUND_EVERY   = 5;

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

    @Override public String id() { return "zero_suit"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 20 * 15 ; }
    @Override public boolean usesCharges(String slot) { return "primary".equals(slot); }
    @Override public int maxCharges(String slot) { return GRENADE_MAX_CHARGES; }
    @Override public long rechargeTicks(String slot) { return GRENADE_RECHARGE_T; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_gravity.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_blast.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() { return "Throw sticky frag grenades with a 2-charge primary and unleash a charged blast as your secondary."; }
    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "ZERO FRAG";
            case "secondary" -> "ZERO BLAST";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Throw a sticky frag grenade with a real arc. It arms on first bounce or on sticking to a target, then explodes 0.75 seconds later.";
            case "secondary" ->
                    "Charge up a powerful laser attack that blasts away anything in its way!";
            default -> "";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/zero_suit.png");
    }

    /* ======================= FALL DAMAGE CANCEL (blast victims) ======================= */

    private static final Object2LongOpenHashMap<UUID> NOFALL_UNTIL_TICK = new Object2LongOpenHashMap<>();
    private static boolean NOFALL_HOOK_INIT = false;

    private static boolean isFallDamage(DamageSource src) {
        if (src == null) return false;
        try {
            return src.isOf(DamageTypes.FALL);
        } catch (Throwable ignored) {}
        try {
            return "fall".equals(src.getName());
        } catch (Throwable ignored) {}
        return false;
    }

    private static void ensureNoFallHook() {
        if (NOFALL_HOOK_INIT) return;
        NOFALL_HOOK_INIT = true;

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity == null) return true;
            if (!(entity.getWorld() instanceof ServerWorld sw)) return true;

            if (!isFallDamage(source)) return true;

            UUID id = entity.getUuid();
            long until = NOFALL_UNTIL_TICK.getOrDefault(id, Long.MIN_VALUE);
            if (until == Long.MIN_VALUE) return true;

            long now = sw.getTime();
            if (now <= until) {
                entity.fallDistance = 0f; // extra safety
                return false; // ✅ cancel fall damage
            }

            // expired -> cleanup
            NOFALL_UNTIL_TICK.removeLong(id);
            return true;
        });
    }

    private static void grantNoFall(LivingEntity e, ServerWorld sw, int ticks) {
        if (e == null || sw == null) return;
        ensureNoFallHook();

        long until = sw.getTime() + Math.max(1, ticks);
        UUID id = e.getUuid();

        long cur = NOFALL_UNTIL_TICK.getOrDefault(id, Long.MIN_VALUE);
        if (until > cur) NOFALL_UNTIL_TICK.put(id, until);

        e.fallDistance = 0f;
    }

    /* ======================= SERVER STATE ======================= */

    private static final class St {
        boolean charging;
        int chargeTicks;
        int lastHudCharge = -1;
        boolean lastHudActive = false;

        boolean jetpackEnabled = false;
        int jetpackFuel = JETPACK_FUEL_MAX;

        long lastJetpackThrustTick = Long.MIN_VALUE;
        int lastJetpackHudFuel = -9999;
        boolean lastJetpackHudEnabled = false;
        boolean lastJetpackHudThrusting = false;
    }

    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof ZeroSuitPower;
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        if (player == null) return;
        St st = S(player);
        if (st.charging) stopCharge(player, st, false);
        if (st.jetpackEnabled) {
            st.jetpackEnabled = false;
            ZeroSuitNet.sendJetpackHud(player, false, st.jetpackFuel, JETPACK_FUEL_MAX, false);
        }
    }

    /* ======================= PRIMARY: frag grenade ======================= */

    private static void throwGrenade(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector().normalize();
        Vec3d spawnPos = eye.add(look.multiply(0.8)).add(0.0, -0.15, 0.0);

        ZeroGrenadeEntity grenade = ModEntities.ZERO_GRENADE.create(sw);
        if (grenade == null) return;

        grenade.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, p.getYaw(), p.getPitch());
        grenade.setOwner(p);

        Vec3d launchVel = look.multiply(GRENADE_THROW_SPEED)
                .add(0.0, GRENADE_THROW_UP_BIAS, 0.0)
                .add(p.getVelocity().multiply(0.35));
        grenade.setLaunchVelocity(launchVel);

        sw.spawnEntity(grenade);

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                ModSounds.SHADOW_KUNAI_THROW, SoundCategory.PLAYERS, GRENADE_THROW_SOUND_VOL, 1.0f);
    }

    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (blockIfPowerless(p)) return;

        S(p); // keep state initialized for the rest of the power
        throwGrenade(p);
    }

    /* ======================= SECONDARY: blast ======================= */

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (blockIfPowerless(p)) return;

        St st = S(p);

        if (!st.charging) {
            st.charging = true;
            st.chargeTicks = 0;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 30, 2, true, false, false));
            ZeroSuitCPM.playBlastCharge(p);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.55f, 0.75f);
        } else {
            stopCharge(p, st, false);
        }
    }

    public static void onClientRequestedFire(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (blockIfPowerless(p)) return;
        St st = S(p);
        if (st.charging) fireBlast(p, st);
    }

    private static void stopCharge(ServerPlayerEntity p, St st, boolean fired) {
        st.charging = false;
        st.chargeTicks = 0;
        ZeroSuitCPM.stopBlastCharge(p);
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        if (!fired) {
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.8f);
        }
        ZeroSuitNet.sendHud(p, false, 0, BLAST_CHARGE_MAX_T);
    }

    private static float pitchForBlast(float ratio) {
        return MathHelper.lerp(MathHelper.clamp(ratio, 0f, 1f), 1.15f, 0.90f);
    }

    private static void fireBlast(ServerPlayerEntity src, St st) {
        ServerWorld sw = (ServerWorld) src.getWorld();

        float ratio  = MathHelper.clamp(st.chargeTicks / (float) BLAST_CHARGE_MAX_T, 0f, 1f);
        float growth = (float)Math.pow(ratio, 1.35f);

        float dmg = BLAST_MAX_DAMAGE * ratio;

        double radius     = MathHelper.lerp(growth, BLAST_RADIUS_MIN, BLAST_RADIUS_MAX);
        double hitRadius  = radius;
        double coreRadius = radius * BLAST_CORE_FRACTION;

        Vec3d eye  = src.getEyePos();
        Vec3d look = src.getRotationVector().normalize();
        Vec3d start = eye.add(look.multiply(0.35)).add(0, -0.35, 0);

        double max = BLAST_RANGE_BLOCKS;
        BlockHitResult bhr = sw.raycast(new RaycastContext(
                start, start.add(look.multiply(max)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                src));

        boolean hitBlock = (bhr.getType() != BlockHitResult.Type.MISS);
        double hitDist = hitBlock ? start.distanceTo(Vec3d.ofCenter(bhr.getBlockPos())) : max;
        Vec3d impact = start.add(look.multiply(hitDist));

        Set<UUID> hitOnce = new HashSet<>();
        Box sweep = new Box(start, start.add(look.multiply(hitDist))).expand(hitRadius);
        List<LivingEntity> mobs = sw.getEntitiesByClass(LivingEntity.class, sweep, e -> e.isAlive() && e != src);

        for (LivingEntity e : mobs) {
            Vec3d c = e.getBoundingBox().getCenter();
            double t = MathHelper.clamp(c.subtract(start).dotProduct(look), 0.0, hitDist);
            Vec3d closest = start.add(look.multiply(t));

            double dist = c.distanceTo(closest);
            if (dist > hitRadius) continue;
            if (!hitOnce.add(e.getUuid())) continue;

            if (dmg > 0.001f) e.damage(src.getDamageSources().playerAttack(src), dmg);

            double falloff = MathHelper.clamp(1.0 - (dist / hitRadius), 0.0, 1.0);
            double kb = BLAST_KB_BASE + (BLAST_KB_MAX_ADD * ratio);
            kb *= (0.30 + 0.70 * falloff);
            if (dist <= coreRadius) kb *= BLAST_KB_CORE_MULT;

            double up = (BLAST_KB_UP_BASE + BLAST_KB_UP_MAX_ADD * ratio) * (0.30 + 0.70 * falloff);

            Vec3d kbVec = look.multiply(kb);
            kbVec = new Vec3d(kbVec.x, kbVec.y + up, kbVec.z);

            e.addVelocity(kbVec.x, kbVec.y, kbVec.z);
            e.velocityModified = true;

            // ✅ NO gravity-changing effects
            // ✅ No fall damage after landing:
            grantNoFall(e, sw, BEAM_NOFALL_TICKS);
        }

        if (hitDist > 0.1) {
            ZeroBeamEntity beam = ModEntities.ZERO_BEAM.create(sw);
            if (beam != null) {
                beam.init(start, look, hitDist, radius, BLAST_VIS_TICKS);
                sw.spawnEntity(beam);
            }
        }

        if (ratio > 0.05f && hitBlock) {
            float strength = (float) MathHelper.lerp(ratio, (float)BLAST_RADIUS_MIN , (float)BLAST_RADIUS_MAX);
            Explosion.DestructionType mode =
                    (ratio >= 0.999f) ? Explosion.DestructionType.DESTROY : Explosion.DestructionType.KEEP;

            Explosion ex = new Explosion(
                    sw, src, null, null,
                    impact.x, impact.y, impact.z,
                    strength, false, mode
            );
            ex.collectBlocksAndDamageEntities();
            ex.affectWorld(true);
        }

        sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);

        Vec3d selfImpulse = look.multiply(-(SELF_RECOIL_BASE + SELF_RECOIL_MAX_ADD * ratio));
        src.addVelocity(selfImpulse.x, selfImpulse.y * 0.65, selfImpulse.z);
        src.velocityModified = true;

        sw.playSound(null, src.getX(), src.getY(), src.getZ(),
                ModSounds.ZERO_BLAST, SoundCategory.PLAYERS, 1.0f, pitchForBlast(ratio));

        ZeroSuitNet.sendBlastFireShake(src, ratio);

        ZeroSuitCPM.playBlastFire(src);
        stopCharge(src, st, true);
    }

    /* ======================= mobility support ======================= */



    /** C2S: called every tick while SPACE is held (client sends movement direction). */
    public static void onClientJetpackThrust(ServerPlayerEntity p, float dx, float dy, float dz) {
        if (!isCurrent(p)) return;
        if (blockIfPowerless(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S(p);
        if (!st.jetpackEnabled) return;
        if (st.jetpackFuel <= 0) return;

        Vec3d dir = new Vec3d(dx, dy, dz);
        if (dir.lengthSquared() < 1.0e-6) return;
        dir = dir.normalize();

        st.jetpackFuel = Math.max(0, st.jetpackFuel - JETPACK_DRAIN_PER_TICK);
        st.lastJetpackThrustTick = sw.getTime();

        Vec3d vel = p.getVelocity().add(dir.multiply(JETPACK_IMPULSE));
        double sp = vel.length();
        if (sp > JETPACK_MAX_SPEED) vel = vel.multiply(JETPACK_MAX_SPEED / sp);

        p.setVelocity(vel);
        p.velocityModified = true;
        p.fallDistance = 0f;

        // play to everyone EXCEPT the pilot (pilot uses local loop)
        if ((p.age % JETPACK_FLY_SOUND_EVERY) == 0) {
            sw.playSound(p, p.getX(), p.getY(), p.getZ(),
                    ModSounds.JETPACK_FLYING, SoundCategory.PLAYERS, 0.75f, 1.0f);
        }

        if ((p.age & 1) == 0) {
            Vec3d back = p.getRotationVector().multiply(-0.35);
            Vec3d pos = p.getPos().add(0, 0.9, 0).add(back);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                    pos.x, pos.y, pos.z, 2, 0.06, 0.06, 0.06, 0.01);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                    pos.x, pos.y, pos.z, 1, 0.06, 0.06, 0.06, 0.005);
        }
    }

    /* ======================= SERVER TICK ======================= */

    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        // ensure hook exists even if first interaction is "landing"
        ensureNoFallHook();

        St st = S(p);


        if (isPowerless(p)) {
            if (st.charging) stopCharge(p, st, false);
            if (st.jetpackEnabled) {
                st.jetpackEnabled = false;
                ZeroSuitNet.sendJetpackHud(p, false, st.jetpackFuel, JETPACK_FUEL_MAX, false);
            }
        }

        if (st.charging) {
            if (st.chargeTicks < BLAST_CHARGE_MAX_T) st.chargeTicks++;

            if ((p.age % 15) == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false, false));
            }

            // Remote charge audio is now handled as a tracked client loop via charge_on/charge_off.
            // Do not spam long world sounds here, or remote listeners can get stuck hearing it after fire/cancel.

            if ((p.age % HUD_SYNC_EVERY) == 0) {
                if (st.lastHudCharge != st.chargeTicks || !st.lastHudActive) {
                    st.lastHudCharge = st.chargeTicks;
                    st.lastHudActive = true;
                    ZeroSuitNet.sendHud(p, true, st.chargeTicks, BLAST_CHARGE_MAX_T);
                }
            }
        } else if ((p.age % HUD_SYNC_EVERY) == 0 && st.lastHudActive) {
            st.lastHudActive = false;
            ZeroSuitNet.sendHud(p, false, 0, BLAST_CHARGE_MAX_T);
        }

        boolean thrusting = (sw.getTime() - st.lastJetpackThrustTick) <= 1;

        if (!thrusting && st.jetpackFuel < JETPACK_FUEL_MAX) {
            int add = JETPACK_RECHARGE_PER_TICK;
            if (p.isOnGround()) add += 1;
            st.jetpackFuel = Math.min(JETPACK_FUEL_MAX, st.jetpackFuel + add);
        }

        if ((p.age % JETPACK_HUD_SYNC_EVERY) == 0) {
            if (st.lastJetpackHudFuel != st.jetpackFuel
                    || st.lastJetpackHudEnabled != st.jetpackEnabled
                    || st.lastJetpackHudThrusting != thrusting) {
                st.lastJetpackHudFuel = st.jetpackFuel;
                st.lastJetpackHudEnabled = st.jetpackEnabled;
                st.lastJetpackHudThrusting = thrusting;
                ZeroSuitNet.sendJetpackHud(p, st.jetpackEnabled, st.jetpackFuel, JETPACK_FUEL_MAX, thrusting);
            }
        }
    }

    /* ======================= CLIENT HUD hooks (blast only) ======================= */
    @Environment(EnvType.CLIENT)
    public static final class ClientHud {
        private ClientHud() {}

        private static boolean lastAttackDown = false;

        public static boolean consumeAttackEdge() {
            boolean now = net.minecraft.client.MinecraftClient.getInstance().options.attackKey.isPressed();
            boolean edge = now && !lastAttackDown;
            lastAttackDown = now;
            return edge;
        }

        public static void onHud(boolean active, int charge, int max) {
            net.seep.odd.abilities.zerosuit.client.ZeroBlastChargeFx.onHud(active, charge, max);
        }

        public static boolean isCharging() {
            return net.seep.odd.abilities.zerosuit.client.ZeroBlastChargeFx.isActive();
        }

        public static void init() {
            net.seep.odd.abilities.zerosuit.client.ZeroBlastChargeFx.init();
        }
    }
}