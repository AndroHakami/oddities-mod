package net.seep.odd.abilities.overdrive;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** All server-side gameplay for Overdrive. */
public final class OverdriveSystem {

    public enum Mode { NORMAL, ENERGIZED, OVERDRIVE }

    public static final class Data {
        public float energy = 0f;
        public Mode mode = Mode.NORMAL;
        public boolean chargingPunch = false;
        public long chargeStartTick = 0;
        public boolean relayActive = false;
        public int overdriveTicksLeft = 0;
        Vec3d lastPos = null;
    }

    private static final Map<UUID, Data> STATE = new HashMap<>();
    private static Data data(ServerPlayerEntity p){ return STATE.computeIfAbsent(p.getUuid(), u->new Data()); }

    private static final float MAX_ENERGY = 100f;
    private static final float MOVE_GAIN_PER_BLOCK = 3.0f;
    private static final float PASSIVE_GAIN_PER_TICK = 0.01f;
    private static final float HIT_GAIN = 6f;
    private static final float HURT_GAIN = 4f;

    private static final float ENERGIZED_DRAIN_TPS = 0.35f;
    private static final float RELAY_DRAIN_TPS = 0.8f;

    private static final int PUNCH_MAX_CHARGE_T = 30;
    private static final float PUNCH_ENERGY_COST = 20f;

    private static final int OVERDRIVE_BASE_T = 200;
    private static final float OVERDRIVE_MIN_EN = 100f;

    private OverdriveSystem() {}

    /* ---------- wiring ---------- */

    public static void registerServerTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!"overdrive".equals(net.seep.odd.abilities.PowerAPI.get(p))) continue;
                tickPlayer(p);
            }
        });
    }

    private static void tickPlayer(ServerPlayerEntity p) {
        Data d = data(p);

        if (d.mode != Mode.OVERDRIVE) {
            Vec3d pos = p.getPos();
            if (d.lastPos != null) {
                double dxz = pos.relativize(d.lastPos).length();
                addEnergy(d, (float)(MOVE_GAIN_PER_BLOCK * dxz));
            }
            d.lastPos = pos;
            addEnergy(d, PASSIVE_GAIN_PER_TICK);
        }

        if (d.mode == Mode.ENERGIZED) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 1, true, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 10, 0, true, false, true));
            drain(d, ENERGIZED_DRAIN_TPS);

            if (d.relayActive) { drain(d, RELAY_DRAIN_TPS); shareRelay(p); }

            if (d.energy <= 0f) { d.mode = Mode.NORMAL; d.relayActive = false; p.sendMessage(Text.literal("Energized faded"), true); }
        } else if (d.mode == Mode.OVERDRIVE) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 3, true, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 10, 2, true, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 10, 1, true, false, true));

            if (p.age % 3 == 0 && p.getWorld() instanceof ServerWorld sw)
                sw.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY()+1.0, p.getZ(), 4, 0.1,0.1,0.1, 0.01);

            if (--d.overdriveTicksLeft <= 0) endOverdrive(p, d);
        }

        /* ---- CHARGE-UP PARTICLES AROUND FIST (server-side so everyone sees it) ---- */
        if (d.chargingPunch && p.getWorld() instanceof ServerWorld sw) {
// charge progress 0..1
            float tCharge = Math.min(1f, (p.getWorld().getTime() - d.chargeStartTick) / (float) PUNCH_MAX_CHARGE_T);

// fist position: right side + slight pullback increasing with charge
            Vec3d fist = rightFistPos(p, 0.30f + 0.12f * tCharge);

// orange dust transition (deep → bright)
            var colFrom = new Vector3f(1.00f, 0.45f, 0.08f);
            var colTo = new Vector3f(1.00f, 0.80f, 0.25f);
            var ORANGE = new DustColorTransitionParticleEffect(colFrom, colTo, 1.0f);

// 1) “gathering sparkles” ring shrinking toward fist
            double r = 0.85 - 0.50 * tCharge; // 0.85 → 0.35
            int sparkleCount = 10;
            for (int i = 0; i < sparkleCount; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2.0;
                double h = (p.getRandom().nextDouble() - 0.5) * 0.30;
                double sx = fist.x + Math.cos(a) * r;
                double sy = fist.y + h;
                double sz = fist.z + Math.sin(a) * r;
                sw.spawnParticles(ParticleTypes.WAX_ON, sx, sy, sz, 1, 0.03, 0.03, 0.03, 0.0);
            }

// 2) core glow/bloom at the fist
            sw.spawnParticles(ParticleTypes.WAX_ON, fist.x, fist.y, fist.z, 8, 0.12, 0.12, 0.12, 0.0);

// 3) occasional bright flashes
            if (p.age % 6 == 0) sw.spawnParticles(ParticleTypes.FLASH, fist.x, fist.y, fist.z, 1, 0,0,0,0);

// 4) electric accents
            if (p.age % 3 == 0) sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, fist.x, fist.y, fist.z, 3, 0.06, 0.06, 0.06, 0.01);
        }

        if (p.age % 5 == 0)
            OverdriveNet.sendHud(p, d.energy, d.mode.ordinal(), d.mode == Mode.OVERDRIVE ? d.overdriveTicksLeft : 0);
    }

    private static void addEnergy(Data d, float a){ d.energy = Math.min(MAX_ENERGY, d.energy + a); }
    private static void drain(Data d, float a){ d.energy = Math.max(0f, d.energy - a); }
    public static boolean isRelayActive(ServerPlayerEntity p) { return data(p).relayActive; }
    public static boolean isInOverdrive(ServerPlayerEntity p) { return data(p).mode == Mode.OVERDRIVE; }
    public static boolean hasFullMeter(ServerPlayerEntity p) { return data(p).energy >= OVERDRIVE_MIN_EN; }

    // (keep: COMMON CPM helper if you ever want server-driven animations again)
    private static void cpmPlay(ServerPlayerEntity p, String anim, int value) {
        var common = net.seep.odd.compat.cpm.OddCpmPlugin.COMMON;
        if (common != null) common.playAnimation(PlayerEntity.class, p, anim, value);
    }

    /** Approximate right-fist position, slightly pulled back. */
    private static Vec3d rightFistPos(ServerPlayerEntity p, float pullback) {
        Vec3d fwd = p.getRotationVec(1f).normalize();
        Vec3d right = fwd.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d base = p.getPos().add(0, 1.2, 0); // ~chest height
        return base.add(right.multiply(0.45)).add(fwd.multiply(-pullback));
    }

    /* ---------- called by network ---------- */

    public static void toggleEnergized(ServerPlayerEntity p) {
        Data d = data(p);
        if (d.mode == Mode.OVERDRIVE) return;
        if (d.mode == Mode.ENERGIZED) { d.mode = Mode.NORMAL; d.relayActive = false;
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.PLAYERS, 0.3f, 0.7f); return; }
        if (d.energy > 0f) { d.mode = Mode.ENERGIZED;
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 0.6f, 1.4f); }
        else p.sendMessage(Text.literal("Not enough energy"), true);
    }

    public static void setRelay(ServerPlayerEntity p, boolean on) {
        Data d = data(p);
        if (d.mode != Mode.ENERGIZED) { d.relayActive = false; return; }
        d.relayActive = on;
        p.getWorld().playSound(null, p.getBlockPos(),
                on ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
                SoundCategory.PLAYERS, on ? 0.6f : 0.5f, on ? 1.2f : 1.0f);
    }

    public static void tryOverdrive(ServerPlayerEntity p) {
        Data d = data(p);
        if (d.mode == Mode.OVERDRIVE) return;
        if (d.energy < OVERDRIVE_MIN_EN) { p.sendMessage(Text.literal("Fill the meter to enter Overdrive!"), true); return; }
        d.mode = Mode.OVERDRIVE; d.overdriveTicksLeft = OVERDRIVE_BASE_T; d.energy = 0f; d.relayActive = false;

        if (p.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.FLASH, p.getX(), p.getY()+1.0, p.getZ(), 1, 0,0,0,0);
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY()+1.0, p.getZ(), 40, 0.4,0.4,0.4, 0.02);
            net.seep.odd.abilities.overdrive.OverdriveNet.sendOverdriveAnim(p, true);
        }
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.3f);
        p.sendMessage(Text.literal("OVERDRIVE!"), true);
    }

    public static void startPunch(ServerPlayerEntity p) {
        if (!"overdrive".equals(net.seep.odd.abilities.PowerAPI.get(p))) return; // ← add this
        Data d = data(p);
        if (d.energy < PUNCH_ENERGY_COST) {
            p.sendMessage(Text.literal("Need energy to charge punch."), true);
            return;
        }
        d.chargingPunch = true;
        d.chargeStartTick = p.getWorld().getTime();

// (animations handled client-side; here we just add audio)
        p.getWorld().playSound(null, p.getBlockPos(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.5f, 1.8f);
    }

    public static void releasePunch(ServerPlayerEntity p) {
        if (!"overdrive".equals(net.seep.odd.abilities.PowerAPI.get(p))) return; // ← add this
        Data d = data(p);
        if (!d.chargingPunch) return;
        d.chargingPunch = false;

        long held = p.getWorld().getTime() - d.chargeStartTick;
        float t = Math.min(1f, held / (float) PUNCH_MAX_CHARGE_T);

        if (d.energy < PUNCH_ENERGY_COST) {
            p.sendMessage(Text.literal("Out of energy!"), true);
            return;
        }
        drain(d, PUNCH_ENERGY_COST);

// forward dash impulse (Doomfist style), scales with charge
        Vec3d look = p.getRotationVec(1f).normalize();
        double dash = 1.15 + (2.65 - 1.15) * t;
        double yBoost = Math.max(0.12, 0.20 * t);
        p.setVelocity(look.x * dash, yBoost, look.z * dash);
        p.velocityModified = true;

// AoE (unchanged core logic)
        float radius = 2.5f + 2.5f * t, dmg = 5f + 9f * t, kb = 0.6f + 1.1f * t;
        Vec3d center = p.getPos().add(look.multiply(2.2));
        Box area = new Box(center.x - radius, center.y - 1.5, center.z - radius,
                center.x + radius, center.y + 1.5, center.z + radius);

        if (p.getWorld() instanceof ServerWorld sw) {
            for (Entity e : sw.getOtherEntities(p, area, ent -> ent instanceof LivingEntity && ent.isAlive())) {
                LivingEntity le = (LivingEntity) e;
                Vec3d to = le.getPos().subtract(p.getPos());
                if (to.normalize().dotProduct(look) < 0.15) continue;

                le.damage(sw.getDamageSources().playerAttack(p), dmg);

                double pushMag = kb;
                le.addVelocity(look.x * pushMag, 0.25 + 0.25 * t, look.z * pushMag);
                le.velocityModified = true;

                sw.spawnParticles(ParticleTypes.EXPLOSION, le.getX(), le.getY() + 0.5, le.getZ(), 1, 0,0,0,0);
            }
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.getX()+look.x, p.getY()+1.0, p.getZ()+look.z, 10, 0.6,0.2,0.6, 0.0);

            /* ---- ORANGE IMPACT BURST ---- */
            Vec3d impact = p.getPos().add(look.multiply(1.2)).add(0, 1.0, 0);
            var colFrom2 = new Vector3f(1.00f, 0.50f, 0.08f);
            var colTo2 = new Vector3f(1.00f, 0.85f, 0.30f);
            var ORANGE2 = new DustColorTransitionParticleEffect(colFrom2, colTo2, 1.2f);

// dense orange dust core
            sw.spawnParticles(ParticleTypes.WAX_ON, impact.x, impact.y, impact.z, 40, 0.35, 0.25, 0.35, 0.01);
// electric shards outward
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y, impact.z, 25, 0.40, 0.30, 0.40, 0.15);
// optional flame embers
            sw.spawnParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 12, 0.20, 0.10, 0.20, 0.005);
        }

        p.getWorld().playSound(null, p.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.6f);
    }

    /* helpers */

    private static void shareRelay(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        double R = 8.0;
        for (ServerPlayerEntity ally : sw.getPlayers()) {
            if (ally == p) continue;
            if (ally.squaredDistanceTo(p) > R*R) continue;
            ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 0, true, false, true));
            ally.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 10, 0, true, false, true));
        }
        sw.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY()+0.1, p.getZ(), 6, 1.0,0.2,1.0, 0.02);
    }

    private static void endOverdrive(ServerPlayerEntity p, Data d) {
        if (p.getWorld() instanceof ServerWorld sw) {
            double R = 6.5;
            Box area = new Box(p.getX()-R, p.getY()-1.5, p.getZ()-R, p.getX()+R, p.getY()+1.5, p.getZ()+R);
            for (Entity e : sw.getOtherEntities(p, area, ent -> ent instanceof LivingEntity && ent.isAlive())) {
                LivingEntity le = (LivingEntity) e;
                Vec3d push = le.getPos().subtract(p.getPos()).normalize().multiply(1.6).add(0, 0.35, 0);
                le.damage(sw.getDamageSources().playerAttack(p), 10f);
                le.addVelocity(push.x, push.y, push.z); le.velocityModified = true;
            }
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY()+0.5, p.getZ(), 12, 1.2,0.5,1.2, 0.0);
            net.seep.odd.abilities.overdrive.OverdriveNet.sendOverdriveAnim(p, false);

            d.mode = Mode.NORMAL; d.overdriveTicksLeft = 0; p.sendMessage(Text.literal("Overdrive ended"), true);
            sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY()+0.5, p.getZ(), 1, 0,0,0,0);
            sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.9f, 0.7f);
        }
        d.mode = Mode.NORMAL; d.overdriveTicksLeft = 0; p.sendMessage(Text.literal("Overdrive ended"), true);
    }

    /* optional hooks to feed energy from other systems */
    public static void onPlayerHit(ServerPlayerEntity p){ if ("overdrive".equals(net.seep.odd.abilities.PowerAPI.get(p))) addEnergy(data(p), HIT_GAIN); }
    public static void onPlayerHurt(ServerPlayerEntity p){ if ("overdrive".equals(net.seep.odd.abilities.PowerAPI.get(p))) addEnergy(data(p), HURT_GAIN); }
}