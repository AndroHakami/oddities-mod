// src/main/java/net/seep/odd/abilities/power/AcceleratePower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;

import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import net.minecraft.network.PacketByteBuf;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;

import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import net.minecraft.world.World;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.sound.ModSounds;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accelerate:
 *  PRIMARY: SUPER SPEED (toggle, meter)
 *    - Speed 40 ALWAYS while active (amp 39)
 *    - Big jump and preserves momentum
 *    - Momentum slip: high-speed glide when you stop
 *    - Speed lines + FOV intensity scale with *actual* movement speed + build-up charge
 *    - Afterimages: visible to everyone (including your first person), fade out when toggled off
 *      - Afterimages are always offset 0.3 blocks "behind" (trailing) for readability.
 *    - Lightning: subtle + rare (Flash-ish tone but not loud/constant)
 *    - Gravity attribute: 1.5x while active (uses registry lookup for minecraft:generic.gravity).
 *
 *  SECONDARY: RECALL
 *    - Yellow/red burst at both the recall trigger position and the destination.
 */
public final class AcceleratePower implements Power {

    public static final AcceleratePower INSTANCE = new AcceleratePower();

    /* =================== id / slots / cooldowns =================== */

    @Override public String id() { return "accelerate"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    /* =================== UI: icons / descriptions =================== */

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/accelerate_speed.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/accelerate_recall.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String longDescription() {
        return "Super Speed (meter): Speed 40, huge jump, slip momentum, speed lines/FOV scale with speed, yellow afterimages (with fade-out), subtle lightning. " +
                "Recall: rewind to your position/health from 7 seconds ago and cleanse negative effects.";
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "SUPER SPEED";
            case "secondary" -> "RECALL";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Toggle Super Speed (meter).\n" +
                            "Speed 40 (constant), huge jump, keeps momentum.\n" +
                            "Speed lines + FOV scale with how fast you’re going.\n" +
                            "Yellow afterimages visible to everyone.\n" +
                            "Subtle lightning trails at high speed.\n" +
                            "Gravity 1.5x while active.";
            case "secondary" ->
                    "Recall to your location from 7 seconds ago.\n" +
                            "Heal back up to the health you had then (won’t lower current HP).\n" +
                            "Cleanses harmful potion effects.\n" +
                            "Creates a yellow/red burst at both recall points.";
            default -> "Accelerate";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/accelerate_portrait.png");
    }

    /* =================== tuning =================== */

    // meter
    private static final float ENERGY_MAX = 560.0f;
    private static final float ENERGY_DRAIN_PER_TICK = 4.15f;
    private static final float ENERGY_REGEN_PER_TICK = 3.95f;

    // Speed 40 => amplifier 39
    private static final int SPEED_AMP = 39;
    private static final int SPEED_REFRESH_TICKS = 8;

    // higher jump
    private static final int JUMP_AMP = 2;                 // Jump Boost III
    private static final int JUMP_REFRESH_TICKS = 10;

    // gravity attribute (best-effort via registry id)
    private static final Identifier GRAVITY_ATTR_ID = new Identifier("minecraft", "generic.gravity");
    private static final Identifier GRAVITY_MOD_ID  = new Identifier("odd", "accelerate_gravity");
    private static final UUID GRAVITY_MOD_UUID = UUID.fromString("b50d0e57-3f5f-4b0e-8a7f-5d1632e8b6b9");
    private static final double GRAVITY_MULT = 1.5; // total gravity *= 1.5

    // auto step assist
    private static final double STEP_MIN_HSPEED = 0.18;
    private static final double STEP_JUMP_VY = 0.64;
    private static final int STEP_COOLDOWN_TICKS = 4;

    // momentum slip
    private static final double SLIP_MIN_MOMENTUM = 0.65;
    private static final double SLIP_APPLY_WHEN_BELOW = 0.62;
    private static final double SLIP_BLEND = 0.72;
    private static final double MOMENTUM_DECAY = 0.970;

    // recall
    private static final int RECALL_TICKS = 20 * 7;

    // HUD snapshot rate
    private static final int HUD_SEND_EVERY = 2;

    // sparks (server particles)
    private static final double RED_SPARK_MIN_SPEED = 0.55;
    private static final int RED_SPARK_EVERY_TICKS = 2;
    private static final int RED_SPARK_COUNT = 10;
    private static final DustParticleEffect RED_SPARK =
            new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.05f), 1.15f);

    // recall "explosion" (yellow + red)
    private static final DustParticleEffect RECALL_YELLOW =
            new DustParticleEffect(new Vector3f(1.0f, 0.90f, 0.12f), 1.85f);
    private static final DustParticleEffect RECALL_RED =
            new DustParticleEffect(new Vector3f(1.0f, 0.12f, 0.12f), 1.85f);
    private static final int RECALL_BURST_COUNT = 80;

    // trail networking / capture
    private static final int TRAIL_FRAMES = 8;
    private static final int TRAIL_SEND_EVERY_TICKS = 1;

    // "truly idle" rule (server-side)
    private static final double IDLE_HSPEED = 0.02;
    private static final double IDLE_VYSPEED = 0.05;

    /* =================== networking ids =================== */

    private static final Identifier S2C_SPEED_HUD   = new Identifier("odd", "accelerate_speed_hud");
    private static final Identifier S2C_TRAIL_FRAME = new Identifier("odd", "accelerate_trail_frame");
    private static final Identifier S2C_TRAIL_STOP  = new Identifier("odd", "accelerate_trail_stop");
    private static final Identifier S2C_TRAIL_CLEAR = new Identifier("odd", "accelerate_trail_clear");

    /* =================== state =================== */

    private static final class SpeedState {
        boolean active = false;

        // slip momentum storage (horizontal only)
        Vec3d momentum = Vec3d.ZERO;

        int stepCooldown = 0;

        int hudSendCooldown = 0;
        float lastSentEnergy = -9999f;

        // sparks rate limit
        int redSparkCd = 0;

        // gravity modifier applied (server-side book-keeping)
        boolean gravityApplied = false;

        // trail sync
        int trailSendCd = 0;
        boolean sentIdleClear = false;
    }

    private static final class RecallSnapshot {
        final RegistryKey<World> dim;
        final Vec3d pos;
        final float yaw, pitch;
        final float health;

        RecallSnapshot(RegistryKey<World> dim, Vec3d pos, float yaw, float pitch, float health) {
            this.dim = dim;
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.health = health;
        }
    }

    private static final class RecallBuffer {
        final RecallSnapshot[] ring = new RecallSnapshot[RECALL_TICKS];
        int idx = 0;
        int filled = 0;

        void push(RecallSnapshot s) {
            ring[idx] = s;
            idx = (idx + 1) % RECALL_TICKS;
            if (filled < RECALL_TICKS) filled++;
        }

        RecallSnapshot getSevenSecondsAgoOrOldest() {
            if (filled <= 0) return null;
            if (filled < RECALL_TICKS) return ring[0];
            return ring[idx];
        }
    }

    private static final Map<UUID, SpeedState> SPEED = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, Float> ENERGY = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, RecallBuffer> RECALL = new Object2ObjectOpenHashMap<>();

    private static SpeedState S(ServerPlayerEntity p) { return SPEED.computeIfAbsent(p.getUuid(), u -> new SpeedState()); }
    private static RecallBuffer R(ServerPlayerEntity p) { return RECALL.computeIfAbsent(p.getUuid(), u -> new RecallBuffer()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        Power pow = Powers.get(PowerAPI.get(p));
        return pow instanceof AcceleratePower;
    }

    /* =================== lifecycle hooks =================== */

    static {
        ServerTickEvents.END_SERVER_TICK.register(AcceleratePower::serverTick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            trySetGravity(handler.player, false);
            broadcastTrailStop(handler.player);

            UUID id = handler.player.getUuid();
            SPEED.remove(id);
            ENERGY.remove(id);
            RECALL.remove(id);
        });
    }

    @Override
    public void onAssigned(ServerPlayerEntity player) {
        trySetGravity(player, false);

        ENERGY.put(player.getUuid(), ENERGY_MAX);
        sendHud(player, false, ENERGY_MAX, ENERGY_MAX);
        R(player);

        broadcastTrailClear(player);
    }

    /* =================== PRIMARY =================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        SpeedState st = S(player);

        if (st.active) {
            st.active = false;
            st.momentum = Vec3d.ZERO;
            sendHud(player, false, getEnergy(player), ENERGY_MAX);

            if (st.gravityApplied) {
                trySetGravity(player, false);
                st.gravityApplied = false;
            }

            playAccelerateToggleSound(player, false);
            broadcastTrailStop(player);
            return;
        }

        float e = getEnergy(player);
        if (e <= 2.0f) {
            player.sendMessage(Text.literal("Too exhausted to Accelerate."), true);
            sendHud(player, false, e, ENERGY_MAX);
            return;
        }

        st.active = true;
        st.momentum = Vec3d.ZERO;
        st.stepCooldown = 0;
        st.hudSendCooldown = 0;
        st.lastSentEnergy = -9999f;
        st.redSparkCd = 0;

        st.trailSendCd = 0;
        st.sentIdleClear = false;

        trySetGravity(player, true);
        st.gravityApplied = true;

        sendHud(player, true, e, ENERGY_MAX);

        playAccelerateToggleSound(player, true);
        broadcastTrailClear(player);
    }

    /* =================== SECONDARY (RECALL) =================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        RecallSnapshot snap = R(player).getSevenSecondsAgoOrOldest();
        if (snap == null) {
            player.sendMessage(Text.literal("Recall not ready yet."), true);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerWorld targetWorld = server.getWorld(snap.dim);
        if (targetWorld == null) {
            player.sendMessage(Text.literal("Recall failed (world missing)."), true);
            return;
        }

        if (player.getWorld().getRegistryKey() != snap.dim) {
            player.sendMessage(Text.literal("Recall blocked (different dimension)."), true);
            return;
        }

        // VFX at trigger
        Vec3d fromPos = player.getPos();
        if (player.getWorld() instanceof ServerWorld fromWorld) {
            spawnRecallExplosion(fromWorld, fromPos);
        }

        player.teleport(targetWorld, snap.pos.x, snap.pos.y, snap.pos.z, snap.yaw, snap.pitch);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0f;

        // VFX at destination
        spawnRecallExplosion(targetWorld, snap.pos);

        float current = player.getHealth();
        float want = MathHelper.clamp(snap.health, 0.0f, player.getMaxHealth());
        if (want > current) player.setHealth(want);

        List<StatusEffectInstance> effects = new ArrayList<>(player.getStatusEffects());
        for (StatusEffectInstance inst : effects) {
            if (inst != null && inst.getEffectType() != null &&
                    inst.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) {
                player.removeStatusEffect(inst.getEffectType());
            }
        }
    }

    /* =================== server tick =================== */

    private static void serverTick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID uuid = p.getUuid();

            if (!isCurrent(p)) {
                trySetGravity(p, false);

                SpeedState old = SPEED.get(uuid);
                if (old != null) broadcastTrailStop(p);

                SPEED.remove(uuid);
                continue;
            }

            trackRecall(p);

            SpeedState st = S(p);

            if (!st.active) {
                if (st.gravityApplied) {
                    trySetGravity(p, false);
                    st.gravityApplied = false;
                }
                float e = getEnergy(p);
                if (e < ENERGY_MAX) setEnergy(p, e + ENERGY_REGEN_PER_TICK);
                continue;
            }

            if (!st.gravityApplied) {
                trySetGravity(p, true);
                st.gravityApplied = true;
            }

            float e = getEnergy(p);
            e -= ENERGY_DRAIN_PER_TICK;
            setEnergy(p, e);

            if (e <= 0.0f) {
                st.active = false;
                st.momentum = Vec3d.ZERO;

                trySetGravity(p, false);
                st.gravityApplied = false;

                sendHud(p, false, 0.0f, ENERGY_MAX);

                playAccelerateToggleSound(p, false);
                broadcastTrailStop(p);
                continue;
            }

            // ALWAYS Speed 40
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, SPEED_REFRESH_TICKS, SPEED_AMP, false, false, false));

            // jump boost
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, JUMP_REFRESH_TICKS, JUMP_AMP, false, false, false));

            // momentum slip
            applySlip(p, st);

            // step assist
            autoStepJumpTick(p, st);

            // sparks
            spawnRedSparks(p, st);

            // trail sync (afterimages + lightning)
            syncTrail(p, st);

            st.hudSendCooldown--;
            if (st.hudSendCooldown <= 0) {
                st.hudSendCooldown = HUD_SEND_EVERY;
                if (Math.abs(st.lastSentEnergy - e) >= 0.9f) {
                    st.lastSentEnergy = e;
                    sendHud(p, true, e, ENERGY_MAX);
                }
            }
        }
    }

    /* =================== trail sync (server -> all clients) =================== */

    private static void syncTrail(ServerPlayerEntity p, SpeedState st) {
        Vec3d v = p.getVelocity();
        double hs = Math.sqrt(v.x * v.x + v.z * v.z);

        boolean idle = p.isOnGround() && hs <= IDLE_HSPEED && Math.abs(v.y) <= IDLE_VYSPEED;

        if (idle) {
            if (!st.sentIdleClear) {
                broadcastTrailClear(p);
                st.sentIdleClear = true;
            }
            return;
        }
        st.sentIdleClear = false;

        if (st.trailSendCd-- > 0) return;
        st.trailSendCd = TRAIL_SEND_EVERY_TICKS - 1;

        broadcastTrailFrame(p, p.getPos(), p.getYaw());
    }

    private static void broadcastTrailFrame(ServerPlayerEntity source, Vec3d pos, float yaw) {
        MinecraftServer server = source.getServer();
        if (server == null) return;

        for (ServerPlayerEntity watcher : server.getPlayerManager().getPlayerList()) {
            if (watcher.getWorld().getRegistryKey() != source.getWorld().getRegistryKey()) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(source.getUuid());
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
            buf.writeFloat(yaw);

            ServerPlayNetworking.send(watcher, S2C_TRAIL_FRAME, buf);
        }
    }

    private static void broadcastTrailStop(ServerPlayerEntity source) {
        MinecraftServer server = source.getServer();
        if (server == null) return;

        for (ServerPlayerEntity watcher : server.getPlayerManager().getPlayerList()) {
            if (watcher.getWorld().getRegistryKey() != source.getWorld().getRegistryKey()) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(source.getUuid());
            ServerPlayNetworking.send(watcher, S2C_TRAIL_STOP, buf);
        }
    }

    private static void broadcastTrailClear(ServerPlayerEntity source) {
        MinecraftServer server = source.getServer();
        if (server == null) return;

        for (ServerPlayerEntity watcher : server.getPlayerManager().getPlayerList()) {
            if (watcher.getWorld().getRegistryKey() != source.getWorld().getRegistryKey()) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(source.getUuid());
            ServerPlayNetworking.send(watcher, S2C_TRAIL_CLEAR, buf);
        }
    }

    /* =================== gravity attribute (reflective; version tolerant) =================== */

    private static void trySetGravity(ServerPlayerEntity p, boolean enable) {
        try {
            EntityAttribute gravityAttr = Registries.ATTRIBUTE.get(GRAVITY_ATTR_ID);
            if (gravityAttr == null) return;

            EntityAttributeInstance inst = p.getAttributeInstance(gravityAttr);
            if (inst == null) return;

            removeModifierAny(inst);

            if (!enable) return;

            EntityAttributeModifier.Operation op = gravityOp();
            EntityAttributeModifier mod = createGravityModifier(op);
            if (mod == null) return;

            addModifierAny(inst, mod);
        } catch (Throwable ignored) { }
    }

    private static EntityAttributeModifier.Operation gravityOp() {
        try {
            return EntityAttributeModifier.Operation.valueOf("ADD_MULTIPLIED_TOTAL");
        } catch (Throwable t) {
            return EntityAttributeModifier.Operation.valueOf("MULTIPLY_TOTAL");
        }
    }

    private static EntityAttributeModifier createGravityModifier(EntityAttributeModifier.Operation op) {
        double value = (GRAVITY_MULT - 1.0);

        try {
            Constructor<EntityAttributeModifier> c =
                    EntityAttributeModifier.class.getDeclaredConstructor(Identifier.class, double.class, EntityAttributeModifier.Operation.class);
            c.setAccessible(true);
            return c.newInstance(GRAVITY_MOD_ID, value, op);
        } catch (Throwable ignored) { }

        try {
            Constructor<EntityAttributeModifier> c =
                    EntityAttributeModifier.class.getDeclaredConstructor(UUID.class, String.class, double.class, EntityAttributeModifier.Operation.class);
            c.setAccessible(true);
            return c.newInstance(GRAVITY_MOD_UUID, "accelerate_gravity", value, op);
        } catch (Throwable ignored) { }

        return null;
    }

    private static void addModifierAny(EntityAttributeInstance inst, EntityAttributeModifier mod) throws Exception {
        try {
            Method m = inst.getClass().getMethod("addPersistentModifier", EntityAttributeModifier.class);
            m.invoke(inst, mod);
            return;
        } catch (NoSuchMethodException ignored) { }

        try {
            Method m = inst.getClass().getMethod("addTemporaryModifier", EntityAttributeModifier.class);
            m.invoke(inst, mod);
        } catch (NoSuchMethodException ignored) { }
    }

    private static void removeModifierAny(EntityAttributeInstance inst) throws Exception {
        try {
            Method m = inst.getClass().getMethod("removeModifier", Identifier.class);
            m.invoke(inst, GRAVITY_MOD_ID);
            return;
        } catch (NoSuchMethodException ignored) { }

        try {
            Method m = inst.getClass().getMethod("removeModifier", UUID.class);
            m.invoke(inst, GRAVITY_MOD_UUID);
        } catch (NoSuchMethodException ignored) { }
    }

    /* =================== sparks =================== */

    private static void spawnRedSparks(ServerPlayerEntity p, SpeedState st) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        if (st.redSparkCd > 0) { st.redSparkCd--; return; }
        st.redSparkCd = RED_SPARK_EVERY_TICKS;

        Vec3d v = p.getVelocity();
        double hs = Math.sqrt(v.x * v.x + v.z * v.z);
        if (hs < RED_SPARK_MIN_SPEED) return;

        Vec3d dir = new Vec3d(v.x, 0.0, v.z);
        if (dir.lengthSquared() < 1e-6) dir = p.getRotationVec(1.0f);
        else dir = dir.normalize();

        double bx = p.getX() - dir.x * 0.45;
        double by = p.getBodyY(0.70);
        double bz = p.getZ() - dir.z * 0.45;

        sw.spawnParticles(
                RED_SPARK,
                bx, by, bz,
                RED_SPARK_COUNT,
                0.35, 0.35, 0.35,
                0.22
        );
    }

    /* =================== recall tracking =================== */

    private static void trackRecall(ServerPlayerEntity p) {
        R(p).push(new RecallSnapshot(
                p.getWorld().getRegistryKey(),
                p.getPos(),
                p.getYaw(),
                p.getPitch(),
                p.getHealth()
        ));
    }

    /* =================== momentum slip =================== */

    private static void applySlip(ServerPlayerEntity p, SpeedState st) {
        Vec3d v = p.getVelocity();

        // when rising/jumping, never fight motion
        if (v.y > 0.10) {
            st.momentum = st.momentum.multiply(MOMENTUM_DECAY);
            return;
        }

        if (!p.isOnGround()) {
            st.momentum = st.momentum.multiply(MOMENTUM_DECAY);
            return;
        }

        Vec3d horiz = new Vec3d(v.x, 0.0, v.z);
        double hs = horiz.length();

        Vec3d mom = st.momentum;
        double ms = mom.length();

        if (hs > ms) mom = horiz;
        else mom = mom.multiply(MOMENTUM_DECAY);

        if (ms > SLIP_MIN_MOMENTUM && hs < (ms * SLIP_APPLY_WHEN_BELOW)) {
            Vec3d blended = horiz.lerp(mom, SLIP_BLEND);
            p.setVelocity(blended.x, v.y, blended.z);
            p.velocityModified = true;
        }

        st.momentum = mom;
    }

    /* =================== auto step-jump =================== */

    private static void autoStepJumpTick(ServerPlayerEntity p, SpeedState st) {
        if (st.stepCooldown > 0) st.stepCooldown--;

        Vec3d v = p.getVelocity();
        double hs = Math.sqrt(v.x * v.x + v.z * v.z);
        if (hs < STEP_MIN_HSPEED) return;
        if (st.stepCooldown > 0) return;

        if (!p.isOnGround()) return;
        if (v.y > 0.10) return;

        Vec3d dir = new Vec3d(v.x, 0.0, v.z);
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();

        if (shouldStepUp(p, dir, 0.55) || shouldStepUp(p, dir, 0.95) || shouldStepUp(p, dir, 1.35)) {
            p.setVelocity(v.x, Math.max(v.y, STEP_JUMP_VY), v.z);
            p.velocityModified = true;
            p.fallDistance = 0.0f;
            st.stepCooldown = STEP_COOLDOWN_TICKS;
        }
    }

    private static boolean shouldStepUp(ServerPlayerEntity p, Vec3d dir, double dist) {
        World w = p.getWorld();
        double fx = p.getX() + dir.x * dist;
        double fz = p.getZ() + dir.z * dist;

        BlockPos feet = BlockPos.ofFloored(fx, p.getY(), fz);
        BlockPos low  = BlockPos.ofFloored(fx, p.getY() - 0.25, fz);

        return wouldStepAt(w, feet) || wouldStepAt(w, low);
    }

    private static boolean wouldStepAt(World w, BlockPos pos) {
        var s0 = w.getBlockState(pos);
        if (s0.getCollisionShape(w, pos).isEmpty()) return false;

        BlockPos up1 = pos.up();
        if (!w.getBlockState(up1).getCollisionShape(w, up1).isEmpty()) return false;

        BlockPos up2 = pos.up(2);
        if (!w.getBlockState(up2).getCollisionShape(w, up2).isEmpty()) return false;

        return true;
    }

    /* =================== energy helpers =================== */

    private static float getEnergy(ServerPlayerEntity player) {
        return ENERGY.getOrDefault(player.getUuid(), ENERGY_MAX);
    }

    private static void setEnergy(ServerPlayerEntity player, float value) {
        ENERGY.put(player.getUuid(), MathHelper.clamp(value, 0.0f, ENERGY_MAX));
    }

    /* =================== hud sync =================== */

    private static void sendHud(ServerPlayerEntity player, boolean active, float energy, float max) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeFloat(energy);
        buf.writeFloat(max);
        ServerPlayNetworking.send(player, S2C_SPEED_HUD, buf);
    }

    /* =================== SFX / VFX helpers =================== */

    private static void playAccelerateToggleSound(ServerPlayerEntity p, boolean started) {
        if (p == null || p.getWorld() == null) return;

        float pitch = started ? 1.10f : 0.92f;

        p.getWorld().playSound(
                null,
                p.getX(), p.getY(), p.getZ(),
                ModSounds.ACCELERATE,
                SoundCategory.PLAYERS,
                1.0f,
                pitch
        );
    }

    private static void spawnRecallExplosion(ServerWorld w, Vec3d pos) {
        if (w == null || pos == null) return;

        double x = pos.x;
        double y = pos.y + 1.0;
        double z = pos.z;

        w.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 2, 0.20, 0.20, 0.20, 0.02);
        w.spawnParticles(RECALL_YELLOW, x, y, z, RECALL_BURST_COUNT, 0.75, 0.75, 0.75, 0.45);
        w.spawnParticles(RECALL_RED,    x, y, z, RECALL_BURST_COUNT, 0.75, 0.75, 0.75, 0.45);
    }

    /* =================== client: HUD + overlays + afterimages + subtle lightning =================== */

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean speedActive = false;
        private static float energy = 0.0f;
        private static float max = 1.0f;

        // LOCAL intensity scales with movement speed + build-up charge (HUD + FOV)
        private static float intensity01 = 0.0f;
        private static float charge01 = 0.0f;

        // Solid yellow afterimage: render model with white texture + vertex tint
        private static final Identifier WHITE_TEX = new Identifier("minecraft", "textures/misc/white.png");
        private static final RenderLayer AFTERIMAGE_LAYER = RenderLayer.getEntityTranslucentEmissive(WHITE_TEX);
        // If your version doesn't have getEntityTranslucentEmissive:
        // private static final RenderLayer AFTERIMAGE_LAYER = RenderLayer.getEntityTranslucent(WHITE_TEX);

        // Always lag afterimages by this much (blocks) in the facing direction
        private static final double AFTERIMAGE_LAG_BLOCKS = 0.30;

        private static final class Frame {
            final double x, y, z;
            final float yaw;
            Frame(double x, double y, double z, float yaw) {
                this.x = x; this.y = y; this.z = z; this.yaw = yaw;
            }
        }

        private static final class TrailState {
            final Deque<Frame> trail = new ArrayDeque<>();
            boolean active = false;
            int decayTicks = 0;
            float intensity01 = 0.0f;
            float charge01 = 0.0f;
            int missingTicks = 0;
        }

        private static final Map<UUID, TrailState> TRAILS = new Object2ObjectOpenHashMap<>();
        private static final int TRAIL_DECAY_MAX = 16;

        // Afterimage look
        private static final float AFTERIMAGE_ALPHA = 0.40f;
        private static final float Y_R = 1.0f, Y_G = 1.0f, Y_B = 0.0f;

        public static void init() {
            ClientPlayNetworking.registerGlobalReceiver(S2C_SPEED_HUD, (client, handler, buf, responder) -> {
                boolean a = buf.readBoolean();
                float e = buf.readFloat();
                float m = buf.readFloat();
                client.execute(() -> {
                    speedActive = a;
                    energy = e;
                    max = Math.max(1.0f, m);
                });
            });

            ClientPlayNetworking.registerGlobalReceiver(S2C_TRAIL_FRAME, (client, handler, buf, responder) -> {
                UUID id = buf.readUuid();
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float yaw = buf.readFloat();

                client.execute(() -> {
                    TrailState st = TRAILS.computeIfAbsent(id, u -> new TrailState());
                    st.active = true;
                    st.decayTicks = 0;
                    st.missingTicks = 0;

                    st.trail.addFirst(new Frame(x, y, z, yaw));
                    while (st.trail.size() > TRAIL_FRAMES) st.trail.removeLast();
                });
            });

            ClientPlayNetworking.registerGlobalReceiver(S2C_TRAIL_STOP, (client, handler, buf, responder) -> {
                UUID id = buf.readUuid();
                client.execute(() -> {
                    TrailState st = TRAILS.computeIfAbsent(id, u -> new TrailState());
                    st.active = false;
                    st.decayTicks = TRAIL_DECAY_MAX;
                });
            });

            ClientPlayNetworking.registerGlobalReceiver(S2C_TRAIL_CLEAR, (client, handler, buf, responder) -> {
                UUID id = buf.readUuid();
                client.execute(() -> {
                    TrailState st = TRAILS.computeIfAbsent(id, u -> new TrailState());
                    st.active = true;
                    st.decayTicks = 0;
                    st.trail.clear();
                });
            });

            // local intensity/charge for HUD/FOV
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client == null || client.player == null) return;

                if (!speedActive) {
                    intensity01 = MathHelper.lerp(0.35f, intensity01, 0.0f);
                    charge01 = MathHelper.lerp(0.35f, charge01, 0.0f);
                    return;
                }

                double hs = client.player.getVelocity().horizontalLength();
                float raw = (float) MathHelper.clamp(hs / 2.10, 0.0, 1.0);

                if (hs > 0.32) charge01 = MathHelper.clamp(charge01 + 0.06f + 0.08f * raw, 0.0f, 1.0f);
                else charge01 = MathHelper.clamp(charge01 - 0.12f, 0.0f, 1.0f);

                float target = MathHelper.clamp(raw * 0.70f + charge01 * 0.70f, 0.0f, 1.0f);
                intensity01 = MathHelper.lerp(0.20f, intensity01, target);
            });

            // per-player intensity + decay + cleanup
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client == null || client.world == null) return;

                List<UUID> ids = new ArrayList<>(TRAILS.keySet());
                for (UUID id : ids) {
                    TrailState st = TRAILS.get(id);
                    if (st == null) continue;

                    AbstractClientPlayerEntity pe = (AbstractClientPlayerEntity) client.world.getPlayerByUuid(id);
                    if (pe == null) {
                        st.missingTicks++;
                        if (st.missingTicks > 200) TRAILS.remove(id);
                        continue;
                    }
                    st.missingTicks = 0;

                    if (!st.active) {
                        if (st.decayTicks > 0) {
                            st.decayTicks--;
                            if (st.decayTicks == 0) st.trail.clear();
                        }
                        st.intensity01 = MathHelper.lerp(0.25f, st.intensity01, 0.0f);
                        st.charge01 = MathHelper.lerp(0.25f, st.charge01, 0.0f);
                        continue;
                    }

                    double hs = pe.getVelocity().horizontalLength();
                    float raw = (float) MathHelper.clamp(hs / 2.10, 0.0, 1.0);

                    if (hs > 0.32) st.charge01 = MathHelper.clamp(st.charge01 + 0.06f + 0.08f * raw, 0.0f, 1.0f);
                    else st.charge01 = MathHelper.clamp(st.charge01 - 0.12f, 0.0f, 1.0f);

                    float target = MathHelper.clamp(raw * 0.70f + st.charge01 * 0.70f, 0.0f, 1.0f);
                    st.intensity01 = MathHelper.lerp(0.20f, st.intensity01, target);
                }
            });

            // HUD + overlays (yellow edge speed lines)
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;
                if (mc.options.hudHidden) return;

                if (!speedActive) return;

                int sw = ctx.getScaledWindowWidth();
                int sh = ctx.getScaledWindowHeight();

                renderEdgeSpeedLines(ctx, sw, sh, mc.player.age, tickDelta, intensity01);

                int barW = 128;
                int barH = 8;
                int x = (sw / 2) - (barW / 2);
                int y = sh - 56;

                ctx.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xAA000000);

                float pct = MathHelper.clamp(energy / max, 0.0f, 1.0f);
                int fillW = (int) (barW * pct);

                int yellow = 0xFFFFC800;
                ctx.fill(x, y, x + fillW, y + barH, yellow);
                ctx.fill(x, y, x + fillW, y + 1, 0x66FFFFFF);
            });

            // Render subtle lightning + afterimages (for everyone, including first-person)
            WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null || mc.world == null) return;

                Camera cam = ctx.camera();
                Vec3d camPos = cam.getPos();

                MatrixStack matrices = ctx.matrixStack();
                VertexConsumerProvider consumers = ctx.consumers();
                float tickDelta = ctx.tickDelta();
                if (consumers == null || matrices == null) return;

                EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

                for (Map.Entry<UUID, TrailState> ent : TRAILS.entrySet()) {
                    UUID id = ent.getKey();
                    TrailState st = ent.getValue();
                    if (st == null) continue;
                    if (st.trail.size() < 2) continue;

                    AbstractClientPlayerEntity pe = (AbstractClientPlayerEntity) mc.world.getPlayerByUuid(id);
                    if (pe == null) continue;

                    float decayMul = 1.0f;
                    if (!st.active && st.decayTicks > 0) decayMul = (st.decayTicks / (float) TRAIL_DECAY_MAX);
                    if (decayMul <= 0.001f) continue;

                    // subtle + rare lightning
                    renderSubtleLightning(matrices, consumers, camPos, tickDelta, pe, st.intensity01, decayMul, id.hashCode());

                    // afterimages
                    var er = dispatcher.getRenderer(pe);
                    if (!(er instanceof PlayerEntityRenderer pr)) continue;

                    @SuppressWarnings("unchecked")
                    PlayerEntityModel<AbstractClientPlayerEntity> model =
                            (PlayerEntityModel<AbstractClientPlayerEntity>) pr.getModel();

                    float limbAngle = pe.limbAnimator.getPos(tickDelta);
                    float limbDist  = pe.limbAnimator.getSpeed(tickDelta);
                    float age = pe.age + tickDelta;
                    float headYaw = 0.0f;
                    float headPitch = pe.getPitch(tickDelta);

                    model.animateModel(pe, limbAngle, limbDist, tickDelta);
                    model.setAngles(pe, limbAngle, limbDist, age, headYaw, headPitch);

                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();

                    VertexConsumer vc = consumers.getBuffer(AFTERIMAGE_LAYER);
                    List<Frame> frames = new ArrayList<>(st.trail);

                    int i = 0;
                    for (Frame f : frames) {
                        if (i == 0) { i++; continue; } // skip newest

                        // extra constant lag behind facing direction
                        Vec3d lag = backOffsetFromYaw(f.yaw, AFTERIMAGE_LAG_BLOCKS);

                        double rx = (f.x + lag.x) - camPos.x;
                        double ry = (f.y + lag.y) - camPos.y;
                        double rz = (f.z + lag.z) - camPos.z;

                        float frameMul = MathHelper.clamp(1.0f - (i * 0.10f), 0.10f, 1.0f);
                        float a = AFTERIMAGE_ALPHA * decayMul * frameMul;

                        matrices.push();
                        matrices.translate(rx, ry, rz);

                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - f.yaw));
                        matrices.scale(-1.0f, -1.0f, 1.0f);
                        matrices.translate(0.0, -1.501, 0.0);

                        model.render(matrices, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, Y_R, Y_G, Y_B, a);

                        matrices.pop();

                        i++;
                        if (i >= TRAIL_FRAMES) break;
                    }

                    RenderSystem.disableBlend();
                }
            });
        }

        /** Use in your FOV mixin: baseFov *= AcceleratePower.Client.fovMultiplier(); */
        public static double fovMultiplier() {
            MinecraftClient mc = MinecraftClient.getInstance();
            float t = (mc.player != null) ? (mc.player.age * 0.24f) : 0.0f;
            double wobble = 0.03 * intensity01 * MathHelper.sin(t);
            return 1.0 + (0.55 * intensity01) + wobble;
        }

        public static double zoomMultiplier() { return fovMultiplier(); }

        /* ===== overlays ===== */

        private static void renderEdgeSpeedLines(DrawContext ctx, int sw, int sh, int tick, float td, float intensity) {
            int yellow = 0x00FFC800;

            int edge = 14 + (int) (26 * intensity);
            int bands = 12 + (int) (28 * intensity);

            int baseA = 0x04 + (int) (0x14 * intensity);

            ctx.fill(0, 0, sw, edge, (baseA << 24) | yellow);
            ctx.fill(0, sh - edge, sw, sh, (baseA << 24) | yellow);
            ctx.fill(0, 0, edge, sh, (baseA << 24) | yellow);
            ctx.fill(sw - edge, 0, sw, sh, (baseA << 24) | yellow);

            int spd = 28 + (int)(26 * intensity);

            for (int i = 0; i < bands; i++) {
                int s = mix(tick * 911 + i * 173 + (int) (td * 10));
                int w = 18 + floorMod(s >>> 8, 84);
                int h = 1 + floorMod(s >>> 16, 3);

                int a = 0x14 + floorMod(s >>> 24, 0x1C) + (int)(0x34 * intensity);
                a = MathHelper.clamp(a, 0x14, 0xB0);

                int x = floorMod((s + tick * spd), sw + w) - w;

                int yTop = floorMod(s >>> 4, Math.max(2, edge - 2));
                ctx.fill(x, yTop, x + w, yTop + h, (a << 24) | yellow);

                int yBot = sh - edge + floorMod(s >>> 12, Math.max(2, edge - 2));
                ctx.fill(x, yBot, x + w, yBot + h, (a << 24) | yellow);
            }

            for (int i = 0; i < bands; i++) {
                int s = mix(tick * 733 + i * 199 + 0x9E3779B9);
                int h = 18 + floorMod(s >>> 8, 84);
                int w = 1 + floorMod(s >>> 16, 3);

                int a = 0x14 + floorMod(s >>> 24, 0x1C) + (int)(0x34 * intensity);
                a = MathHelper.clamp(a, 0x14, 0xB0);

                int y = floorMod((s + tick * spd), sh + h) - h;

                int xL = floorMod(s >>> 4, Math.max(2, edge - 2));
                ctx.fill(xL, y, xL + w, y + h, (a << 24) | yellow);

                int xR = sw - edge + floorMod(s >>> 12, Math.max(2, edge - 2));
                ctx.fill(xR, y, xR + w, y + h, (a << 24) | yellow);
            }
        }

        /* ===== afterimage lag offset ===== */

        private static Vec3d backOffsetFromYaw(float yawDeg, double dist) {
            float yawRad = yawDeg * ((float)Math.PI / 180.0f);
            // forward for MC yaw: x = -sin(yaw), z = cos(yaw)
            double fx = -MathHelper.sin(yawRad);
            double fz =  MathHelper.cos(yawRad);
            // behind = -forward * dist
            return new Vec3d(-fx * dist, 0.0, -fz * dist);
        }

        /* ===== subtle lightning (rare + low alpha) ===== */

        private static void renderSubtleLightning(MatrixStack matrices,
                                                  VertexConsumerProvider consumers,
                                                  Vec3d camPos,
                                                  float tickDelta,
                                                  AbstractClientPlayerEntity pe,
                                                  float playerIntensity01,
                                                  float decayMul,
                                                  int seedBase) {
            float intensity = MathHelper.clamp(playerIntensity01 * decayMul, 0.0f, 1.0f);

            // only show when going fast enough
            if (intensity < 0.55f) return;

            // only render occasionally (rarer at lower intensity)
            int period = 7 - (int)(3 * intensity); // ~7..4
            period = MathHelper.clamp(period, 4, 7);
            if (((pe.age + (seedBase & 7)) % period) != 0) return;

            Vec3d v = pe.getVelocity();
            Vec3d back = new Vec3d(-v.x, 0.0, -v.z);
            if (back.lengthSquared() < 1.0e-6) {
                Vec3d rv = pe.getRotationVec(tickDelta).multiply(-1.0);
                back = new Vec3d(rv.x, 0.0, rv.z);
            }
            if (back.lengthSquared() < 1.0e-6) return;
            back = back.normalize();

            Vec3d up = new Vec3d(0.0, 1.0, 0.0);
            Vec3d right = back.crossProduct(up);
            if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1.0, 0.0, 0.0);
            else right = right.normalize();

            Vec3d base = pe.getPos().add(0.0, pe.getHeight() * 0.76, 0.0);
            base = base.add(back.multiply(0.35 + 0.25 * intensity));

            // much fewer bolts
            int bolts = 1 + (int)(1 * intensity); // 1..2

            double lenBase = 1.9 + 2.5 * intensity;
            double ampBase = 0.14 + 0.30 * intensity;
            double upAmpBase = 0.06 + 0.16 * intensity;

            // low visibility
            float alpha = MathHelper.clamp(0.10f + 0.10f * intensity, 0.0f, 0.20f);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
            MatrixStack.Entry entry = matrices.peek();

            float time = pe.age + tickDelta;

            for (int b = 0; b < bolts; b++) {
                int seed = seedBase ^ (b * 0x9E3779B9);

                double offSide = randSigned(seed + 17) * (0.08 + 0.06 * intensity);
                double offUp = randSigned(seed + 23) * (0.06 + 0.06 * intensity);

                Vec3d start = base
                        .add(right.multiply(offSide))
                        .add(up.multiply(offUp));

                double len = lenBase * (0.90 + 0.25 * rand01(seed + 3));
                double amp = ampBase * (0.90 + 0.25 * rand01(seed + 4));
                double upAmp = upAmpBase * (0.90 + 0.25 * rand01(seed + 5));

                drawSubtleBolt(lines, entry, camPos, start, back, right, up, len, amp, upAmp, time, seed, alpha);
            }

            RenderSystem.disableBlend();
        }

        private static void drawSubtleBolt(VertexConsumer vc,
                                           MatrixStack.Entry entry,
                                           Vec3d camPos,
                                           Vec3d startWorld,
                                           Vec3d backDir,
                                           Vec3d rightDir,
                                           Vec3d upDir,
                                           double len,
                                           double amp,
                                           double upAmp,
                                           float time,
                                           int seed,
                                           float alpha) {
            final int segs = 10;
            final double step = len / segs;

            Vec3d prev = null;

            // tiny “thickness” only (subtle)
            Vec3d offR = rightDir.multiply(0.018);
            Vec3d offU = upDir.multiply(0.018);

            for (int i = 0; i <= segs; i++) {
                float t = i / (float) segs;

                double zig = ((i & 1) == 0) ? 1.0 : -1.0;
                float wob1 = MathHelper.sin((time * 0.95f) + (i * 1.70f) + (seed * 0.001f));
                float wob2 = MathHelper.sin((time * 1.25f) + (i * 2.10f) + (seed * 0.002f));

                double side = zig * amp * (0.55 + 0.45 * wob1);
                double yoff = upAmp * wob2;

                Vec3d p = startWorld
                        .add(backDir.multiply(i * step))
                        .add(rightDir.multiply(side))
                        .add(upDir.multiply(yoff));

                if (prev != null) {
                    int m = mix((int)(time * 12) + seed + i * 97);
                    if ((m & 0x3) == 0) { // lots of gaps
                        prev = p;
                        continue;
                    }

                    Color c0 = coreColor((i - 1) / (float) segs);
                    Color c1 = coreColor(t);

                    addLineGradient(vc, entry, prev, p, camPos,
                            c0.r, c0.g, c0.b, alpha * 0.75f,
                            c1.r, c1.g, c1.b, alpha * 0.75f);

                    // very light glow
                    Color g0 = glowColor((i - 1) / (float) segs);
                    Color g1 = glowColor(t);

                    addLineGradient(vc, entry, prev.add(offR), p.add(offR), camPos,
                            g0.r, g0.g, g0.b, alpha * 0.25f,
                            g1.r, g1.g, g1.b, alpha * 0.25f);

                    addLineGradient(vc, entry, prev.add(offU), p.add(offU), camPos,
                            g0.r, g0.g, g0.b, alpha * 0.25f,
                            g1.r, g1.g, g1.b, alpha * 0.25f);
                }

                prev = p;
            }
        }

        private static void addLineGradient(VertexConsumer vc,
                                            MatrixStack.Entry entry,
                                            Vec3d aWorld,
                                            Vec3d bWorld,
                                            Vec3d camPos,
                                            float r0, float g0, float b0, float a0,
                                            float r1, float g1, float b1, float a1) {
            float ax = (float)(aWorld.x - camPos.x);
            float ay = (float)(aWorld.y - camPos.y);
            float az = (float)(aWorld.z - camPos.z);

            float bx = (float)(bWorld.x - camPos.x);
            float by = (float)(bWorld.y - camPos.y);
            float bz = (float)(bWorld.z - camPos.z);

            vc.vertex(entry.getPositionMatrix(), ax, ay, az)
                    .color(r0, g0, b0, MathHelper.clamp(a0, 0.0f, 1.0f))
                    .normal(entry.getNormalMatrix(), 0.0f, 1.0f, 0.0f)
                    .next();

            vc.vertex(entry.getPositionMatrix(), bx, by, bz)
                    .color(r1, g1, b1, MathHelper.clamp(a1, 0.0f, 1.0f))
                    .normal(entry.getNormalMatrix(), 0.0f, 1.0f, 0.0f)
                    .next();
        }

        private static final class Color {
            final float r, g, b;
            Color(float r, float g, float b) { this.r = r; this.g = g; this.b = b; }
        }

        private static Color coreColor(float t) {
            // white/yellow -> golden
            float r0 = 1.00f, g0 = 1.00f, b0 = 0.70f;
            float r1 = 1.00f, g1 = 0.68f, b1 = 0.14f;
            return new Color(lerp(r0, r1, t), lerp(g0, g1, t), lerp(b0, b1, t));
        }

        private static Color glowColor(float t) {
            float r0 = 1.00f, g0 = 0.86f, b0 = 0.22f;
            float r1 = 1.00f, g1 = 0.48f, b1 = 0.08f;
            return new Color(lerp(r0, r1, t), lerp(g0, g1, t), lerp(b0, b1, t));
        }

        /* ===== tiny deterministic RNG helpers ===== */

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static float rand01(int seed) {
            int x = mix(seed);
            return (x & 0xFFFF) / 65535.0f;
        }

        private static float randSigned(int seed) {
            return (rand01(seed) * 2.0f) - 1.0f;
        }

        private static int mix(int x) {
            x ^= (x << 13);
            x ^= (x >>> 17);
            x ^= (x << 5);
            return x;
        }

        private static int floorMod(int x, int m) {
            int r = x % m;
            return (r < 0) ? (r + m) : r;
        }
    }
}
