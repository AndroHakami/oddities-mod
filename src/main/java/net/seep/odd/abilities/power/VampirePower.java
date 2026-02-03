// src/main/java/net/seep/odd/abilities/power/VampirePower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.RaycastContext;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.vampire.CpmVampireHooks;
import net.seep.odd.abilities.vampire.VampireUtil;
import net.seep.odd.abilities.vampire.entity.BloodCrystalProjectileEntity;

import java.util.UUID;

public final class VampirePower implements Power {

    @Override public String id() { return "vampire"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    @Override public long cooldownTicks() { return 40; }
    @Override public long secondaryCooldownTicks() { return 20 * 3; }
    @Override public long thirdCooldownTicks() { return 20 * 45; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/vampire_blood_suck.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/vampire_blood_crystal.png");
            case "third"     -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/vampire_frenzy.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    /* ---------------- Blood gauge values ---------------- */

    public static final float BLOOD_MAX = 100f;

    /* ---------------- Network IDs ---------------- */

    public static final Identifier S2C_VAMPIRE_FLAG = new Identifier(Oddities.MOD_ID, "vampire/flag");
    public static final Identifier S2C_BLOOD_SYNC   = new Identifier(Oddities.MOD_ID, "vampire/blood_sync");
    public static final Identifier S2C_FRENZY_STATE = new Identifier(Oddities.MOD_ID, "vampire/frenzy_state");

    private static void sendClientFlag(ServerPlayerEntity p, boolean has) {
        var b = PacketByteBufs.create();
        b.writeBoolean(has);
        ServerPlayNetworking.send(p, S2C_VAMPIRE_FLAG, b);
    }

    private static void sendBloodHud(ServerPlayerEntity p, boolean hasVampire, boolean showHudBar, float blood, float max) {
        var b = PacketByteBufs.create();
        b.writeBoolean(hasVampire);
        b.writeBoolean(showHudBar);
        b.writeFloat(blood);
        b.writeFloat(max);
        ServerPlayNetworking.send(p, S2C_BLOOD_SYNC, b);
    }

    private static void sendFrenzy(ServerPlayerEntity p, boolean on) {
        var b = PacketByteBufs.create();
        b.writeBoolean(on);
        ServerPlayNetworking.send(p, S2C_FRENZY_STATE, b);
    }

    /* ---------------- Tuning ---------------- */

    // Primary (suck)
    public static final int SUCK_MAX_TICKS = 20 * 3;      // 3 seconds
    public static final double SUCK_RANGE = 3.0;

    public static final float SUCK_GAIN_PER_TICK = 0.8f;
    public static final float SUCK_SAT_GAIN_PER_TICK = 0.85f;

    // 4 hearts TOTAL across full channel:
    public static final int   SUCK_DAMAGE_INTERVAL_TICKS = 15;
    public static final float SUCK_DAMAGE_PER_HIT = 2.0f; // 1 heart

    public static final float SUCK_FLIP_DEGREES = 90f;
    public static final int SUCK_FLIP_EASE_TICKS = 8;

    // ✅ If target has >= 40 hearts (>= 80 max health), use "leech" mode instead of stun/grab.
    public static final float LEECH_TARGET_MAX_HEALTH_THRESHOLD = 80.0f;

    // Leech latch tuning (how the vampire sticks to the big target)
    public static final double LEECH_BACK_OFFSET = 0.55;
    public static final double LEECH_SIDE_OFFSET = 0.18;
    public static final double LEECH_UP_OFFSET_MIN = 0.45;
    public static final double LEECH_UP_OFFSET_MAX = 1.05;

    // Secondary (crystal)
    public static final float CRYSTAL_COST = 18f;

    // Third (frenzy)
    public static final float FRENZY_DRAIN_PER_TICK = 1.35f;

    // potion buffs (guaranteed visible)
    public static final int FRENZY_SPEED_AMP = 1;     // Speed II
    public static final int FRENZY_STRENGTH_AMP = 0;  // Strength I
    public static final int FRENZY_EFFECT_T = 12;     // refresh duration (ticks)

    public static final UUID FRENZY_SPEED_ID  = UUID.fromString("b7b4e0e6-5c1b-4f3c-8a0b-0b6bb5040a91");
    public static final UUID FRENZY_DAMAGE_ID = UUID.fromString("f1fbd0d8-0b7a-4b85-a5f5-8f76a2b0c2d2");
    public static final double FRENZY_SPEED_ADD = 0.10;
    public static final double FRENZY_DAMAGE_ADD = 3.0;

    // Passive stealth sense
    public static final int STEALTH_PARTICLE_INTERVAL = 5;
    public static final double STEALTH_SENSE_RANGE = 100.0;
    public static final int STEALTH_MAX_TARGETS = 8;

    // stealth stillness (position-based)
    public static final int STEALTH_STILL_REQUIRED_TICKS = 4;

    // ✅ stealth break cooldown
    public static final int STEALTH_BREAK_COOLDOWN_TICKS = 20 * 3; // 3 seconds

    // Blood HUD sync
    public static int BLOOD_SYNC_INTERVAL = 5;
    public static float BLOOD_SYNC_EPS = 0.25f;

    /* ---------------- Runtime state ---------------- */

    private static final Object2ObjectOpenHashMap<UUID, SuckSession> SUCKING = new Object2ObjectOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> FRENZY_ON = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> LAST_STEALTH_PARTICLES = new Object2LongOpenHashMap<>();

    private static final Object2FloatOpenHashMap<UUID> LAST_SENT_BLOOD = new Object2FloatOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_SENT_TICK  = new Object2LongOpenHashMap<>();

    private static final Object2ObjectOpenHashMap<UUID, Vec3d> LAST_POS = new Object2ObjectOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> STILL_TICKS = new Object2LongOpenHashMap<>();

    // ✅ stealth state + cooldown
    private static final Object2LongOpenHashMap<UUID> STEALTH_ACTIVE = new Object2LongOpenHashMap<>();         // 1 = active
    private static final Object2LongOpenHashMap<UUID> STEALTH_COOLDOWN_UNTIL = new Object2LongOpenHashMap<>(); // world time ticks

    private static final class SuckSession {
        UUID target;

        Vec3d playerAnchor;

        float targetYaw0;
        float targetPitch0;
        boolean targetNoGravity0;
        boolean playerNoGravity0;

        boolean leechMode;

        int ticksLeft;
        int age;

        SuckSession(UUID target, Vec3d playerAnchor,
                    float yaw0, float pitch0, boolean noGrav0,
                    boolean playerNoGrav0,
                    boolean leechMode) {
            this.target = target;
            this.playerAnchor = playerAnchor;
            this.targetYaw0 = yaw0;
            this.targetPitch0 = pitch0;
            this.targetNoGravity0 = noGrav0;
            this.playerNoGravity0 = playerNoGrav0;
            this.leechMode = leechMode;
            this.ticksLeft = SUCK_MAX_TICKS;
            this.age = 0;
        }
    }

    /* ---------------- Riding rules while sucking ---------------- */

    private static void forceDismount(Entity e) {
        if (e == null) return;

        if (e.hasVehicle()) e.stopRiding();

        if (e.hasPassengers()) {
            for (Entity passenger : e.getPassengerList()) {
                passenger.stopRiding();
            }
        }
    }

    /* ---------------- Stealth break helpers ---------------- */

    private static boolean stealthOnCooldown(ServerWorld sw, UUID id) {
        long until = STEALTH_COOLDOWN_UNTIL.getOrDefault(id, 0L);
        return sw.getTime() < until;
    }

    private static void startStealthCooldown(ServerWorld sw, UUID id) {
        STEALTH_COOLDOWN_UNTIL.put(id, sw.getTime() + STEALTH_BREAK_COOLDOWN_TICKS);
    }

    private static void breakStealth(ServerWorld sw, ServerPlayerEntity p, boolean applyCooldown) {
        UUID id = p.getUuid();

        p.removeStatusEffect(StatusEffects.INVISIBILITY);
        STEALTH_ACTIVE.removeLong(id);
        STILL_TICKS.put(id, 0L);

        if (applyCooldown) startStealthCooldown(sw, id);
    }

    /* ---------------- Abilities ---------------- */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!VampireUtil.isVampire(player)) return;

        UUID id = player.getUuid();
        if (SUCKING.containsKey(id)) {
            stopSuck(player, true);
            return;
        }

        LivingEntity target = findTargetInFront(player);
        if (target == null) {
            player.sendMessage(Text.literal("No target."), true);
            return;
        }

        boolean leech = target.getMaxHealth() >= LEECH_TARGET_MAX_HEALTH_THRESHOLD;

        forceDismount(player);
        forceDismount(target);

        Vec3d anchor = player.getPos();
        SUCKING.put(id, new SuckSession(
                target.getUuid(),
                anchor,
                target.getYaw(), target.getPitch(), target.hasNoGravity(),
                player.hasNoGravity(),
                leech
        ));

        player.setSprinting(false);

        if (!leech) {
            player.setVelocity(Vec3d.ZERO);
            player.velocityModified = true;
        }

        CpmVampireHooks.onStartBloodSuck(player);
        player.sendMessage(Text.literal(leech ? "Blood Leech" : "Blood Suck"), true);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!VampireUtil.isVampire(player)) return;

        if (!drainBloodLikeHunger(player, CRYSTAL_COST)) {
            player.sendMessage(Text.literal("Not enough Blood."), true);
            return;
        }

        BloodCrystalProjectileEntity proj = new BloodCrystalProjectileEntity(player.getWorld(), player);
        Vec3d look = player.getRotationVector().normalize();

        proj.setPosition(player.getX(), player.getEyeY() - 0.10, player.getZ());
        proj.setVelocity(look.x, look.y, look.z, 1.65f, 0.6f);

        player.getWorld().spawnEntity(proj);
        player.sendMessage(Text.literal("Blood Crystal"), true);
    }

    @Override
    public void activateThird(ServerPlayerEntity player) {
        if (!VampireUtil.isVampire(player)) return;

        UUID id = player.getUuid();
        boolean on = FRENZY_ON.getOrDefault(id, 0L) != 0L;
        if (on) {
            setFrenzy(player, false);
            player.sendMessage(Text.literal("Frenzy: OFF"), true);
        } else {
            if (getBlood(player) <= 0.5f) {
                player.sendMessage(Text.literal("Not enough Blood."), true);
                return;
            }
            setFrenzy(player, true);
            player.sendMessage(Text.literal("Frenzy: ON"), true);
        }
    }

    /* ---------------- Power gained/lost ---------------- */

    public static void onPowerGained(ServerPlayerEntity p) {
        float b = getBlood(p);
        if (b <= 0f) setBlood(p, BLOOD_MAX);

        enforceNoHunger(p);

        sendClientFlag(p, true);
        sendBloodHud(p, true, true, getBlood(p), BLOOD_MAX);
        sendFrenzy(p, false);

        p.sendMessage(Text.literal("Vampire awakened 🩸"), true);
    }

    public static void onPowerLost(ServerPlayerEntity p) {
        stopSuck(p, false);
        setFrenzy(p, false);

        sendClientFlag(p, false);
        sendBloodHud(p, false, false, 0f, BLOOD_MAX);
        sendFrenzy(p, false);

        UUID id = p.getUuid();
        SUCKING.remove(id);
        FRENZY_ON.removeLong(id);
        LAST_STEALTH_PARTICLES.removeLong(id);
        LAST_SENT_BLOOD.removeFloat(id);
        LAST_SENT_TICK.removeLong(id);
        LAST_POS.remove(id);
        STILL_TICKS.removeLong(id);

        STEALTH_ACTIVE.removeLong(id);
        STEALTH_COOLDOWN_UNTIL.removeLong(id);
    }

    public static void addBloodExhaustion(ServerPlayerEntity p, float amount) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        BloodState st = BloodState.of(sw);

        float ex = st.getEx(p.getUuid());
        ex = Math.min(40f, ex + Math.max(0f, amount));
        st.setEx(p.getUuid(), ex);
    }

    /* ---------------- Register tick ---------------- */

    private static boolean REGISTERED = false;

    public static void register() {
        if (REGISTERED) return;
        REGISTERED = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!VampireUtil.isVampire(p)) continue;
                serverTick(p);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (p == null) return;
            if (!VampireUtil.isVampire(p)) return;

            enforceNoHunger(p);

            sendClientFlag(p, true);
            sendBloodHud(p, true, true, getBlood(p), BLOOD_MAX);
            sendFrenzy(p, FRENZY_ON.getOrDefault(p.getUuid(), 0L) != 0L);
        });

        // ✅ Break stealth on attack + apply 3s cooldown.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!VampireUtil.isVampire(sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            // Only break if we were actually stealthing / invisible.
            if (STEALTH_ACTIVE.getOrDefault(sp.getUuid(), 0L) != 0L || sp.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                breakStealth(sw, sp, true);
            }
            return ActionResult.PASS;
        });
    }

    private static void serverTick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        enforceNoHunger(p);

        tickBloodLikeHunger(sw, p);

        tickSuck(sw, p);
        tickFrenzy(sw, p);
        tickStealth(sw, p);

        maybeSyncHud(sw, p, getBlood(p));
    }

    private static void enforceNoHunger(ServerPlayerEntity p) {
        p.getHungerManager().setFoodLevel(17);
        p.getHungerManager().setSaturationLevel(0.0f);
    }

    private static void maybeSyncHud(ServerWorld sw, ServerPlayerEntity p, float blood) {
        UUID id = p.getUuid();
        long now = sw.getTime();

        float last = LAST_SENT_BLOOD.getOrDefault(id, -9999f);
        long lastT = LAST_SENT_TICK.getOrDefault(id, -9999L);

        boolean time = (now - lastT) >= BLOOD_SYNC_INTERVAL;
        boolean changed = Math.abs(blood - last) >= BLOOD_SYNC_EPS;

        if (time || changed) {
            LAST_SENT_BLOOD.put(id, blood);
            LAST_SENT_TICK.put(id, now);
            sendBloodHud(p, true, true, blood, BLOOD_MAX);
        }
    }

    /* ---------------- Blood like Hunger (sat + exhaustion) ---------------- */

    private static void tickBloodLikeHunger(ServerWorld sw, ServerPlayerEntity p) {
        UUID id = p.getUuid();
        BloodState st = BloodState.of(sw);

        float blood = st.get(id);
        float sat   = st.getSat(id);
        float ex    = st.getEx(id);

        while (ex > 4.0f) {
            ex -= 4.0f;
            if (sat > 0.0f) sat = Math.max(0.0f, sat - 5.0f);
            else            blood = Math.max(0.0f, blood - 5.0f);
        }

        if (sat > blood) sat = blood;

        st.put(id, blood);
        st.setSat(id, sat);
        st.setEx(id, ex);
    }

    private static boolean drainBloodLikeHunger(ServerPlayerEntity p, float amount) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return false;
        UUID id = p.getUuid();
        BloodState st = BloodState.of(sw);

        float blood = st.get(id);
        float sat = st.getSat(id);

        if (blood < amount) return false;

        float fromSat = Math.min(sat, amount);
        sat -= fromSat;
        amount -= fromSat;

        if (amount > 0f) blood = Math.max(0f, blood - amount);

        if (sat > blood) sat = blood;

        st.put(id, blood);
        st.setSat(id, sat);
        return true;
    }

    private static void addBloodAndSat(ServerPlayerEntity p, float addBlood, float addSat) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        UUID id = p.getUuid();
        BloodState st = BloodState.of(sw);

        float blood = st.get(id);
        float sat = st.getSat(id);

        blood = Math.min(BLOOD_MAX, blood + addBlood);
        sat = Math.min(blood, sat + addSat);

        st.put(id, blood);
        st.setSat(id, sat);
    }

    /* ---------------- Primary (suck) ticking ---------------- */

    private static void tickSuck(ServerWorld sw, ServerPlayerEntity p) {
        UUID id = p.getUuid();
        SuckSession s = SUCKING.get(id);
        if (s == null) return;

        if (!p.isAlive() || p.isDead() || p.getHealth() <= 0.0f) {
            stopSuck(p, false);
            return;
        }

        LivingEntity target = (LivingEntity) sw.getEntity(s.target);
        if (target == null || !target.isAlive()) {
            stopSuck(p, true);
            return;
        }

        // ✅ While sucking is active: deny riding for both by constantly enforcing dismount
        forceDismount(p);
        forceDismount(target);

        s.age++;

        if (s.leechMode) {
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
            p.fallDistance = 0f;

            p.setNoGravity(true);

            float yaw = target.getYaw();
            Vec3d forward = Vec3d.fromPolar(0.0f, yaw).normalize();

            Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
            Vec3d backOffset = forward.multiply(-LEECH_BACK_OFFSET).add(right.multiply(LEECH_SIDE_OFFSET));

            double up = MathHelper.clamp(target.getHeight() * 0.65, LEECH_UP_OFFSET_MIN, LEECH_UP_OFFSET_MAX);

            Vec3d latchPos = target.getPos().add(backOffset).add(0.0, up, 0.0);
            p.requestTeleport(latchPos.x, latchPos.y, latchPos.z);

            if ((s.age % SUCK_DAMAGE_INTERVAL_TICKS) == 0) {
                target.damage(sw.getDamageSources().playerAttack(p), SUCK_DAMAGE_PER_HIT);
            }

            Vec3d c = target.getPos().add(0, target.getHeight() * 0.6, 0);
            sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 8, 0.20, 0.20, 0.20, 0.0);
            sw.spawnParticles(ParticleTypes.CRIMSON_SPORE,     c.x, c.y, c.z, 5, 0.15, 0.15, 0.15, 0.0);

            addBloodAndSat(p, SUCK_GAIN_PER_TICK, SUCK_SAT_GAIN_PER_TICK);

        } else {
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
            p.fallDistance = 0f;
            p.requestTeleport(s.playerAnchor.x, s.playerAnchor.y, s.playerAnchor.z);

            Vec3d front = p.getRotationVector().normalize().multiply(1.15);
            Vec3d holdPos = p.getPos().add(front).add(0, 0.65, 0);

            target.setVelocity(Vec3d.ZERO);
            target.velocityModified = true;
            target.fallDistance = 0f;
            target.setNoGravity(true);
            target.requestTeleport(holdPos.x, holdPos.y, holdPos.z);

            float t = MathHelper.clamp(s.age / (float)SUCK_FLIP_EASE_TICKS, 0f, 1f);
            t = t * t * (3f - 2f * t);

            target.setYaw(s.targetYaw0);
            target.setPitch(s.targetPitch0 + SUCK_FLIP_DEGREES * t); // ✅ was setYaw by mistake

            if ((s.age % SUCK_DAMAGE_INTERVAL_TICKS) == 0) {
                target.damage(sw.getDamageSources().playerAttack(p), SUCK_DAMAGE_PER_HIT);
            }

            Vec3d c = target.getPos().add(0, target.getHeight() * 0.6, 0);
            sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 8, 0.20, 0.20, 0.20, 0.0);
            sw.spawnParticles(ParticleTypes.CRIMSON_SPORE,     c.x, c.y, c.z, 5, 0.15, 0.15, 0.15, 0.0);

            addBloodAndSat(p, SUCK_GAIN_PER_TICK, SUCK_SAT_GAIN_PER_TICK);
        }

        s.ticksLeft--;
        if (s.ticksLeft <= 0) stopSuck(p, true);
    }

    private static void stopSuck(ServerPlayerEntity p, boolean message) {
        UUID id = p.getUuid();
        SuckSession s = SUCKING.remove(id);
        if (s == null) return;

        p.setNoGravity(s.playerNoGravity0);

        if (!s.leechMode && p.getWorld() instanceof ServerWorld sw) {
            LivingEntity target = (LivingEntity) sw.getEntity(s.target);
            if (target != null) {
                target.setNoGravity(s.targetNoGravity0);
                target.setYaw(s.targetYaw0);
                target.setPitch(s.targetPitch0);
            }
        }

        CpmVampireHooks.onStopBloodSuck(p);
        if (message) p.sendMessage(Text.literal("Blood Suck ended"), true);
    }

    private static LivingEntity findTargetInFront(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld)p.getWorld();

        Vec3d start = p.getCameraPosVec(1f);
        Vec3d dir = p.getRotationVector().normalize();
        Vec3d end = start.add(dir.multiply(SUCK_RANGE));

        HitResult block = w.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                p
        ));

        double max = SUCK_RANGE;
        if (block != null && block.getType() != HitResult.Type.MISS) {
            max = block.getPos().distanceTo(start);
        }

        Box box = p.getBoundingBox().stretch(dir.multiply(max)).expand(0.9);
        LivingEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        for (LivingEntity le : w.getEntitiesByClass(LivingEntity.class, box, ent ->
                ent.isAlive() && ent != p && !ent.isSpectator())) {

            Vec3d c = le.getBoundingBox().getCenter();
            double d2 = c.squaredDistanceTo(start);
            if (d2 < bestD2) {
                Vec3d to = c.subtract(start).normalize();
                double dot = to.dotProduct(dir);
                if (dot > 0.78) {
                    bestD2 = d2;
                    best = le;
                }
            }
        }

        return best;
    }

    /* ---------------- Third (frenzy) ticking ---------------- */

    private static void tickFrenzy(ServerWorld sw, ServerPlayerEntity p) {
        UUID id = p.getUuid();
        boolean on = FRENZY_ON.getOrDefault(id, 0L) != 0L;
        if (!on) return;

        if (!drainBloodLikeHunger(p, FRENZY_DRAIN_PER_TICK)) {
            setFrenzy(p, false);
            p.sendMessage(Text.literal("Frenzy ended (Blood empty)"), true);
            return;
        }

        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, FRENZY_EFFECT_T, FRENZY_SPEED_AMP, false, false, true));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, FRENZY_EFFECT_T, FRENZY_STRENGTH_AMP, false, false, true));

        applyFrenzyModifiers(p, true);
    }

    private static void setFrenzy(ServerPlayerEntity p, boolean on) {
        UUID id = p.getUuid();
        if (on) {
            FRENZY_ON.put(id, 1L);
            applyFrenzyModifiers(p, true);
        } else {
            FRENZY_ON.removeLong(id);
            applyFrenzyModifiers(p, false);
        }
        sendFrenzy(p, on);
    }

    private static void applyFrenzyModifiers(ServerPlayerEntity p, boolean on) {
        var sp = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        var ad = p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (sp == null || ad == null) return;

        sp.removeModifier(FRENZY_SPEED_ID);
        ad.removeModifier(FRENZY_DAMAGE_ID);

        if (on) {
            sp.addPersistentModifier(new EntityAttributeModifier(
                    FRENZY_SPEED_ID, "VampireFrenzySpeed", FRENZY_SPEED_ADD, EntityAttributeModifier.Operation.ADDITION));
            ad.addPersistentModifier(new EntityAttributeModifier(
                    FRENZY_DAMAGE_ID, "VampireFrenzyDamage", FRENZY_DAMAGE_ADD, EntityAttributeModifier.Operation.ADDITION));
        }
    }

    /* ---------------- Passive stealth + sense ---------------- */

    private static void tickStealth(ServerWorld sw, ServerPlayerEntity p) {
        UUID id = p.getUuid();
        long nowT = sw.getTime();

        // track movement
        Vec3d now = p.getPos();
        Vec3d last = LAST_POS.get(id);
        LAST_POS.put(id, now);

        if (last == null) {
            STILL_TICKS.put(id, 0L);
            return;
        }

        double dx = now.x - last.x;
        double dz = now.z - last.z;
        double dy = now.y - last.y;

        boolean sneaking = p.isSneaking();
        boolean still = (dx*dx + dz*dz) < 0.0002 && Math.abs(dy) < 0.04;

        long stillTicks = STILL_TICKS.getOrDefault(id, 0L);
        stillTicks = (sneaking && still) ? (stillTicks + 1L) : 0L;
        STILL_TICKS.put(id, stillTicks);

        // ✅ Always allow the “hunt trails” while sneaking (even when moving).
        long lastTrail = LAST_STEALTH_PARTICLES.getOrDefault(id, Long.MIN_VALUE);
        if (sneaking && (nowT - lastTrail) >= STEALTH_PARTICLE_INTERVAL) {
            LAST_STEALTH_PARTICLES.put(id, nowT);
            VampireUtil.spawnSenseTrails(sw, p, STEALTH_SENSE_RANGE, STEALTH_MAX_TARGETS);
        }

        boolean wasStealth = STEALTH_ACTIVE.getOrDefault(id, 0L) != 0L;

        // If not sneaking, and we were stealthing, break + cooldown.
        if (!sneaking) {
            if (wasStealth) breakStealth(sw, p, true);
            return;
        }

        // If on cooldown, we can still show trails, but invis cannot re-apply.
        if (stealthOnCooldown(sw, id)) {
            if (wasStealth) breakStealth(sw, p, false);
            return;
        }

        // ✅ Invisibility requires stillness buildup
        boolean eligibleInvis = stillTicks >= STEALTH_STILL_REQUIRED_TICKS;

        if (eligibleInvis) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 10, 0, false, false, true));
            STEALTH_ACTIVE.put(id, 1L);
        } else {
            // If we *were* invisible/stealthing and lost it (moved), apply 3s cooldown.
            if (wasStealth || p.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                breakStealth(sw, p, true);
            }
        }
    }

    /* ---------------- Blood persistence ---------------- */

    public static float getBlood(PlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return BLOOD_MAX;
        return BloodState.of(sw).get(p.getUuid());
    }

    public static void setBlood(PlayerEntity p, float v) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        UUID id = p.getUuid();
        BloodState st = BloodState.of(sw);
        float b = MathHelper.clamp(v, 0f, BLOOD_MAX);
        st.put(id, b);

        float s = st.getSat(id);
        if (s > b) st.setSat(id, b);
    }

    private static final class BloodState extends PersistentState {
        private final Object2FloatOpenHashMap<UUID> blood = new Object2FloatOpenHashMap<>();
        private final Object2FloatOpenHashMap<UUID> sat  = new Object2FloatOpenHashMap<>();
        private final Object2FloatOpenHashMap<UUID> ex   = new Object2FloatOpenHashMap<>();

        float get(UUID id) { return blood.getOrDefault(id, BLOOD_MAX); }
        void put(UUID id, float v) { blood.put(id, v); markDirty(); }

        float getSat(UUID id) { return sat.getOrDefault(id, 0f); }
        void setSat(UUID id, float v) { sat.put(id, Math.max(0f, v)); markDirty(); }

        float getEx(UUID id) { return ex.getOrDefault(id, 0f); }
        void setEx(UUID id, float v) { ex.put(id, Math.max(0f, v)); markDirty(); }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (var e : blood.object2FloatEntrySet()) {
                UUID id = e.getKey();
                NbtCompound c = new NbtCompound();
                c.putUuid("id", id);
                c.putFloat("b", e.getFloatValue());
                c.putFloat("s", getSat(id));
                c.putFloat("e", getEx(id));
                list.add(c);
            }
            nbt.put("list", list);
            return nbt;
        }

        static BloodState fromNbt(NbtCompound nbt) {
            BloodState s = new BloodState();
            NbtList list = nbt.getList("list", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                UUID id = c.getUuid("id");
                s.blood.put(id, c.getFloat("b"));
                if (c.contains("s", NbtElement.FLOAT_TYPE)) s.sat.put(id, c.getFloat("s"));
                if (c.contains("e", NbtElement.FLOAT_TYPE)) s.ex.put(id, c.getFloat("e"));
            }
            return s;
        }

        static BloodState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(BloodState::fromNbt, BloodState::new, "odd_vampire_blood");
        }
    }
}
