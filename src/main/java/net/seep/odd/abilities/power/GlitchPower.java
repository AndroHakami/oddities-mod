// src/main/java/net/seep/odd/abilities/power/GlitchPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;

import net.minecraft.network.PacketByteBuf;

import net.minecraft.particle.ParticleTypes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.sound.SoundCategory;

import net.minecraft.text.Text;

import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import net.minecraft.world.World;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.particles.OddParticles;
import net.seep.odd.sound.ModSounds;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Glitch:
 *  PRIMARY (HOLD): TELEKINESIS
 *  SECONDARY (HOLD/RELEASE): GLITCH WALL
 *
 * Wall orientation while aiming:
 *  - A/D: rotate around itself (yaw) in 45° steps
 *  - W/S: tilt around its own head (pitch) in 45° steps (can lay flat at 90°)
 *
 * Movement feel while active:
 *  - Strong momentum damping (~90% per tick in air) to stop drifting.
 */
public final class GlitchPower implements Power, HoldReleasePower {

    public static final GlitchPower INSTANCE = new GlitchPower();

    /* =================== id / slots / cooldowns =================== */

    @Override public String id() { return "glitch"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 20 * 25; }

    /* =================== UI: icons / descriptions =================== */

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/glitch_telekinesis.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/glitch_wall.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String longDescription() {
        return "Telekinesis: hold to seize an entity and drag it around using a draining meter. " +
                "Glitch Wall: hold to aim a circular wall of glitch blocks, tilt/rotate it with WASD, release to place for 10 seconds.";
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "TELEKINESIS";
            case "secondary" -> "GLITCH WALL";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Hold to mark an entity and move it with smoothing.\n" +
                            "Meter drains while active.\n" +
                            "MAX HP > 60: heavier control.\n" +
                            "MAX HP > 100: only slows.\n" +
                            "Scroll up/down to push/pull distance.\n" +
                            "While active: heavy momentum damping + slight zoom.";
            case "secondary" ->
                    "Hold: freeze midair + preview a circular wall (radius 4).\n" +
                            "Scroll up/down moves placement distance (1..16).\n" +
                            "A/D rotates (yaw) 45° steps.\n" +
                            "W/S tilts (pitch) 45° steps (can lay flat at 90°).\n" +
                            "Release: place wall for 10s; it then breaks into glitch particles.";
            default -> "Glitch";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/glitch_portrait.png");
    }

    /* =================== tuning =================== */

    // Telekinesis
    private static final double RAYCAST_RANGE = 32.0;

    private static final double MIN_HOLD_DISTANCE = 2.0;
    private static final double MAX_HOLD_DISTANCE = 24.0;

    private static final float ENERGY_MAX = 200.0f;
    private static final float ENERGY_REGEN_PER_TICK = 0.9f;
    private static final float BASE_DRAIN_PER_TICK = 1.0f;

    private static final double BASE_STRENGTH = 0.28;
    private static final double BASE_MAX_SPEED = 0.95;
    private static final double TARGET_LERP = 0.25;
    private static final double VEL_BLEND = 0.35;

    private static final int AURA_PARTICLES_PER_TICK = 2;
    private static final int GRAB_BURST_COUNT = 28;

    // Player “stop drifting” feel (TK + wall aim):
    private static final double DAMP_AIR = 0.10;      // keep 10% velocity (90% stop)
    private static final double DAMP_GROUND = 0.20;   // still heavy on ground
    private static final double DAMP_EPS_SQ = 0.00008;

    // still apply slowness so input acceleration is small
    private static final int PLAYER_SLOWNESS_AMP = 3;      // Slowness IV
    private static final int PLAYER_SLOWNESS_TICKS = 8;    // refreshed

    // Glitch Wall
    private static final int WALL_RADIUS = 4;
    private static final int WALL_DISTANCE_MIN = 1;
    private static final int WALL_DISTANCE_MAX = 16;
    private static final int WALL_LIFETIME_TICKS = 20 * 10;

    private static final int WALL_PLACE_PARTICLES = 80;
    private static final int WALL_BREAK_PARTICLES = 90;

    // rotation in 45° steps
    private static final int YAW_STEPS_MOD = 8;        // 0..7 => 0..315
    private static final int PITCH_MIN_STEPS = -2;     // -90
    private static final int PITCH_MAX_STEPS = 2;      // +90
    private static final float ROT_STEP_DEG = 45.0f;

    /* =================== networking ids =================== */

    private static final Identifier S2C_TK_HUD        = new Identifier("odd", "glitch_tk_hud");
    private static final Identifier S2C_WALL_PREVIEW  = new Identifier("odd", "glitch_wall_preview");
    private static final Identifier C2S_GLITCH_SCROLL = new Identifier("odd", "glitch_scroll");
    private static final Identifier C2S_WALL_ORIENT   = new Identifier("odd", "glitch_wall_orient");

    /* =================== state =================== */

    private static final class TKState {
        final int entityId;
        double distance;
        Vec3d smoothedTarget;
        boolean toggleMode = false;
        long lastAppliedTick = -1;
        long lastHeldSeenTick = -1;

        TKState(int entityId) { this.entityId = entityId; }
    }

    private static final class WallAimState {
        int distanceBlocks = 6;

        // orientation:
        int yawSteps = 0;    // wraps 0..7
        int pitchSteps = 0;  // clamps -2..2

        boolean toggleMode = false;
        boolean oldNoGravity = false;
        long lastHeldSeenTick = -1;
    }

    private static final class PlacedWall {
        final UUID owner;
        final net.minecraft.registry.RegistryKey<World> dimension;
        final long expiresAt;
        final List<BlockPos> blocks;
        final Vec3d center;

        PlacedWall(UUID owner, net.minecraft.registry.RegistryKey<World> dimension, long expiresAt, List<BlockPos> blocks, Vec3d center) {
            this.owner = owner;
            this.dimension = dimension;
            this.expiresAt = expiresAt;
            this.blocks = blocks;
            this.center = center;
        }
    }

    private static final Map<UUID, TKState> TK = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, Float> ENERGY = new Object2ObjectOpenHashMap<>();

    private static final Map<UUID, WallAimState> WALL_AIM = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, PlacedWall>  WALLS_PLACED = new Object2ObjectOpenHashMap<>();

    private static boolean isCurrent(ServerPlayerEntity p) {
        Power pow = Powers.get(PowerAPI.get(p));
        return pow instanceof GlitchPower;
    }

    /* =================== lifecycle hooks =================== */

    static {
        ServerTickEvents.END_SERVER_TICK.register(GlitchPower::serverTick);

        ServerPlayNetworking.registerGlobalReceiver(C2S_GLITCH_SCROLL, (server, player, handler, buf, responder) -> {
            final float scrollY = buf.readFloat();
            server.execute(() -> onScroll(player, scrollY));
        });

        // orientation packet: (deltaYawSteps, deltaPitchSteps)
        ServerPlayNetworking.registerGlobalReceiver(C2S_WALL_ORIENT, (server, player, handler, buf, responder) -> {
            final int dYaw = buf.readInt();
            final int dPitch = buf.readInt();
            server.execute(() -> onWallOrient(player, dYaw, dPitch));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            TK.remove(id);
            ENERGY.remove(id);
            WALL_AIM.remove(id);
        });
    }

    @Override
    public void onAssigned(ServerPlayerEntity player) {
        ENERGY.put(player.getUuid(), ENERGY_MAX);
        sendHud(player, false, ENERGY_MAX, ENERGY_MAX);
        sendWallPreview(player, false, 6, 0, 0);
    }

    /* =================== PRIMARY press fallback =================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        if (TK.containsKey(player.getUuid())) {
            stopTK(player);
            return;
        }

        if (WALL_AIM.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("Finish Glitch Wall first."), true);
            return;
        }

        boolean started = startTK(player, true);
        if (!started) sendHud(player, false, getEnergy(player), ENERGY_MAX);
    }

    /* =================== SECONDARY press fallback =================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        WallAimState st = WALL_AIM.get(player.getUuid());
        if (st == null) {
            startWallAim(player, true);
            return;
        }

        if (st.toggleMode) {
            placeWallFromAim(player, st);
            stopWallAim(player);
        } else {
            player.sendMessage(Text.literal("Hold secondary to place Glitch Wall."), true);
        }
    }

    /* =================== HOLD hooks =================== */

    @Override
    public void onHoldStart(ServerPlayerEntity player, String slot) {
        if (!isCurrent(player)) return;

        if ("primary".equals(slot)) {
            if (TK.containsKey(player.getUuid())) return;
            if (WALL_AIM.containsKey(player.getUuid())) return;

            boolean started = startTK(player, false);
            if (!started) sendHud(player, false, getEnergy(player), ENERGY_MAX);
            return;
        }

        if ("secondary".equals(slot)) {
            if (WALL_AIM.containsKey(player.getUuid())) return;
            if (TK.containsKey(player.getUuid())) {
                player.sendMessage(Text.literal("Finish Telekinesis first."), true);
                return;
            }
            startWallAim(player, false);
        }
    }

    @Override
    public void onHoldTick(ServerPlayerEntity player, String slot, int heldTicks) {
        if (!isCurrent(player)) return;

        if ("primary".equals(slot)) {
            TKState st = TK.get(player.getUuid());
            if (st != null) telekinesisTickOnce(player, st);
            return;
        }

        if ("secondary".equals(slot)) {
            WallAimState st = WALL_AIM.get(player.getUuid());
            if (st != null) wallAimTick(player, st);
        }
    }

    @Override
    public void onHoldRelease(ServerPlayerEntity player, String slot, int heldTicks, boolean canceled) {
        if (!isCurrent(player)) return;

        if ("primary".equals(slot)) {
            TKState st = TK.get(player.getUuid());
            if (st != null && !st.toggleMode) stopTK(player);
            return;
        }

        if ("secondary".equals(slot)) {
            WallAimState st = WALL_AIM.get(player.getUuid());
            if (st == null) return;

            if (!canceled) placeWallFromAim(player, st);
            stopWallAim(player);
        }
    }

    /* =================== TELEKINESIS =================== */

    private static boolean startTK(ServerPlayerEntity player, boolean toggleMode) {
        float energy = getEnergy(player);
        if (energy <= 1.0f) {
            player.sendMessage(Text.literal("Telekinesis exhausted."), true);
            return false;
        }

        Entity target = raycastEntity(player, RAYCAST_RANGE);
        if (target == null || target == player) {
            player.sendMessage(Text.literal("No target."), true);
            return false;
        }

        TKState st = new TKState(target.getId());
        st.toggleMode = toggleMode;
        st.distance = MathHelper.clamp(player.distanceTo(target), MIN_HOLD_DISTANCE, MAX_HOLD_DISTANCE);
        st.smoothedTarget = target.getPos();
        st.lastHeldSeenTick = player.getWorld().getTime();

        TK.put(player.getUuid(), st);

        player.playSound(ModSounds.TELEKINESIS_GRAB, SoundCategory.PLAYERS, 1.0f, 1.0f);
        spawnGrabBurst(player, target);

        sendHud(player, true, energy, ENERGY_MAX);
        return true;
    }

    private static void stopTK(ServerPlayerEntity player) {
        player.playSound(ModSounds.TELEKINESIS_EN, SoundCategory.PLAYERS, 0.9f, 1.0f);
        TK.remove(player.getUuid());
        sendHud(player, false, getEnergy(player), ENERGY_MAX);
    }

    private static void telekinesisTickOnce(ServerPlayerEntity player, TKState st) {
        long nowTick = player.getWorld().getTime();
        if (st.lastAppliedTick == nowTick) return;
        st.lastAppliedTick = nowTick;

        float energy = getEnergy(player);

        Entity ent = player.getWorld().getEntityById(st.entityId);
        if (ent == null || !ent.isAlive()) {
            stopTK(player);
            return;
        }

        // kill drift HARD
        applyPlayerSlow(player);
        dampPlayerVelocity(player);

        spawnAura(player, ent);

        double maxHp = (ent instanceof LivingEntity le) ? le.getMaxHealth() : 0.0;

        float drain = BASE_DRAIN_PER_TICK;
        if (maxHp > 60.0) {
            float heavy01 = (float) MathHelper.clamp((maxHp - 60.0) / 40.0, 0.0, 1.0);
            drain += 0.5f + heavy01;
        }
        if (maxHp > 100.0) drain = 0.8f;

        energy -= drain;
        setEnergy(player, energy);

        if (energy <= 0.0f) {
            player.sendMessage(Text.literal("Telekinesis exhausted."), true);
            stopTK(player);
            return;
        }

        if (maxHp > 100.0) {
            applySlowOnly(ent);
            sendHud(player, true, energy, ENERGY_MAX);
            return;
        }

        Vec3d desired = aimPoint(player, st.distance);
        if (st.smoothedTarget == null) st.smoothedTarget = desired;
        st.smoothedTarget = st.smoothedTarget.lerp(desired, TARGET_LERP);

        Vec3d delta = st.smoothedTarget.subtract(ent.getPos());

        double strength = BASE_STRENGTH;
        double maxSpeed = BASE_MAX_SPEED;

        if (maxHp > 60.0) {
            double heavy01 = MathHelper.clamp((maxHp - 60.0) / 40.0, 0.0, 1.0);
            strength *= (1.0 - 0.65 * heavy01);
            maxSpeed *= (1.0 - 0.55 * heavy01);
            delta = new Vec3d(delta.x, delta.y * (1.0 - 0.75 * heavy01), delta.z);
        }

        Vec3d wantedVel = delta.multiply(strength);
        double maxSpeedSq = maxSpeed * maxSpeed;
        if (wantedVel.lengthSquared() > maxSpeedSq) {
            wantedVel = wantedVel.normalize().multiply(maxSpeed);
        }

        Vec3d blended = ent.getVelocity().multiply(1.0 - VEL_BLEND).add(wantedVel.multiply(VEL_BLEND));
        ent.setVelocity(blended);

        if (ent instanceof LivingEntity le) le.fallDistance = 0.0f;

        sendHud(player, true, energy, ENERGY_MAX);
    }

    /* =================== GLITCH WALL =================== */

    private static void startWallAim(ServerPlayerEntity player, boolean toggleMode) {
        WallAimState st = new WallAimState();
        st.toggleMode = toggleMode;
        st.distanceBlocks = 6;
        st.yawSteps = 0;
        st.pitchSteps = 0;
        st.oldNoGravity = player.hasNoGravity();
        st.lastHeldSeenTick = player.getWorld().getTime();

        WALL_AIM.put(player.getUuid(), st);
        sendWallPreview(player, true, st.distanceBlocks, st.yawSteps, st.pitchSteps);
    }

    private static void stopWallAim(ServerPlayerEntity player) {
        WallAimState st = WALL_AIM.remove(player.getUuid());
        if (st != null) {
            player.setNoGravity(st.oldNoGravity);
        }
        sendWallPreview(player, false, 0, 0, 0);
    }

    private static void wallAimTick(ServerPlayerEntity player, WallAimState st) {
        // suspend + kill drift (feels like “held in place”)
        player.setNoGravity(true);
        player.fallDistance = 0.0f;

        applyPlayerSlow(player);
        dampPlayerVelocity(player);
    }

    private static void placeWallFromAim(ServerPlayerEntity player, WallAimState st) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        // remove old wall for this player
        PlacedWall prev = WALLS_PLACED.get(player.getUuid());
        if (prev != null) {
            breakWall(sw.getServer(), prev);
            WALLS_PLACED.remove(player.getUuid());
        }

        int dist = MathHelper.clamp(st.distanceBlocks, WALL_DISTANCE_MIN, WALL_DISTANCE_MAX);

        Vec3d centerVec = aimPoint(player, dist);
        BlockPos center = BlockPos.ofFloored(centerVec);

        float yawDeg = player.getYaw() + st.yawSteps * ROT_STEP_DEG;     // A/D
        float pitchDeg = st.pitchSteps * ROT_STEP_DEG;                   // W/S

        List<BlockPos> positions = computeWallDiskOriented(center, yawDeg, pitchDeg, WALL_RADIUS);

        int placed = 0;
        for (BlockPos p : positions) {
            if (!sw.isChunkLoaded(p)) continue;

            var cur = sw.getBlockState(p);
            if (!cur.isAir() && !cur.isReplaceable()) continue;

            if (sw.setBlockState(p, ModBlocks.GLITCH_BLOCK.getDefaultState())) {
                placed++;
            }
        }

        if (placed <= 0) return;

        sw.spawnParticles(
                OddParticles.TELEKINESIS,
                centerVec.x, centerVec.y, centerVec.z,
                WALL_PLACE_PARTICLES,
                2.2, 2.2, 2.2,
                0.10
        );

        long now = sw.getTime();
        WALLS_PLACED.put(player.getUuid(), new PlacedWall(
                player.getUuid(),
                sw.getRegistryKey(),
                now + WALL_LIFETIME_TICKS,
                positions,
                centerVec
        ));
    }

    /**
     * Vertical disk by default (pitch=0), can be yaw-rotated (A/D) and pitch-tilted (W/S).
     * Pitch of +/-90 makes it lay flat (horizontal).
     */
    private static List<BlockPos> computeWallDiskOriented(BlockPos center, float yawDeg, float pitchDeg, int radius) {
        int r2 = radius * radius;

        // Base at block center
        Vec3d base = new Vec3d(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);

        // Normal from yaw (MC convention)
        double yawRad = Math.toRadians(yawDeg);
        Vec3d normal = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad)).normalize();

        // Right axis in XZ plane
        Vec3d right = new Vec3d(-normal.z, 0.0, normal.x).normalize();

        // Tilt: rotate normal around RIGHT axis by pitch
        double pitchRad = Math.toRadians(pitchDeg);
        Vec3d tiltedNormal = rotateAroundAxis(normal, right, pitchRad).normalize();

        // Up axis in the plane (perpendicular to right, in the wall plane)
        Vec3d up = tiltedNormal.crossProduct(right).normalize();

        HashSet<Long> seen = new HashSet<>();
        List<BlockPos> out = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if ((dx * dx + dy * dy) > r2) continue;

                Vec3d pos = base.add(right.multiply(dx)).add(up.multiply(dy));
                BlockPos bp = BlockPos.ofFloored(pos);

                if (seen.add(bp.asLong())) out.add(bp);
            }
        }

        return out;
    }

    private static Vec3d rotateAroundAxis(Vec3d v, Vec3d axisUnit, double angleRad) {
        // Rodrigues' rotation formula
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        Vec3d a = axisUnit;
        Vec3d term1 = v.multiply(cos);
        Vec3d term2 = a.crossProduct(v).multiply(sin);
        Vec3d term3 = a.multiply(a.dotProduct(v) * (1.0 - cos));
        return term1.add(term2).add(term3);
    }

    private static void breakWall(MinecraftServer server, PlacedWall wall) {
        ServerWorld world = server.getWorld(wall.dimension);
        if (world == null) return;

        world.spawnParticles(
                OddParticles.TELEKINESIS,
                wall.center.x, wall.center.y, wall.center.z,
                WALL_BREAK_PARTICLES,
                2.3, 2.3, 2.3,
                0.12
        );

        for (BlockPos p : wall.blocks) {
            if (!world.isChunkLoaded(p)) continue;
            if (world.getBlockState(p).isOf(ModBlocks.GLITCH_BLOCK)) {
                world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState());
            }
        }
    }

    /* =================== server tick =================== */

    private static void serverTick(MinecraftServer server) {
        long now = -1;

        // wall expiry pass
        Iterator<Map.Entry<UUID, PlacedWall>> it = WALLS_PLACED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlacedWall> e = it.next();
            PlacedWall wall = e.getValue();

            if (now < 0) {
                ServerWorld any = server.getOverworld();
                now = (any != null) ? any.getTime() : 0;
            }

            if (now >= wall.expiresAt) {
                breakWall(server, wall);
                it.remove();
            }
        }

        // per-player tick
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID uuid = p.getUuid();

            if (!isCurrent(p)) {
                if (WALL_AIM.containsKey(uuid)) stopWallAim(p);
                TK.remove(uuid);
                continue;
            }

            // TK
            TKState tk = TK.get(uuid);
            if (tk != null) {
                long t = p.getWorld().getTime();
                if (!tk.toggleMode) {
                    if (PowerAPI.isHeld(p, "primary")) {
                        tk.lastHeldSeenTick = t;
                    } else if (tk.lastHeldSeenTick >= 0 && (t - tk.lastHeldSeenTick) > 6) {
                        stopTK(p);
                        continue;
                    }
                }
                telekinesisTickOnce(p, tk);
            } else {
                float e2 = getEnergy(p);
                if (e2 < ENERGY_MAX) setEnergy(p, e2 + ENERGY_REGEN_PER_TICK);
            }

            // Wall aim
            WallAimState wa = WALL_AIM.get(uuid);
            if (wa != null) {
                long t = p.getWorld().getTime();
                if (!wa.toggleMode) {
                    if (PowerAPI.isHeld(p, "secondary")) {
                        wa.lastHeldSeenTick = t;
                    } else if (wa.lastHeldSeenTick >= 0 && (t - wa.lastHeldSeenTick) > 6) {
                        stopWallAim(p);
                        continue;
                    }
                }
                wallAimTick(p, wa);
            }
        }
    }

    /* =================== scroll handling =================== */

    private static void onScroll(ServerPlayerEntity player, float scrollY) {
        if (!isCurrent(player)) return;

        // Wall aim: scroll adjusts wall distance (1..16)
        WallAimState wa = WALL_AIM.get(player.getUuid());
        if (wa != null) {
            int dir = (scrollY > 0) ? 1 : (scrollY < 0 ? -1 : 0);
            if (dir != 0) {
                wa.distanceBlocks = MathHelper.clamp(wa.distanceBlocks + dir, WALL_DISTANCE_MIN, WALL_DISTANCE_MAX);
                sendWallPreview(player, true, wa.distanceBlocks, wa.yawSteps, wa.pitchSteps);
            }
            return;
        }

        // TK: scroll adjusts target distance
        TKState tk = TK.get(player.getUuid());
        if (tk == null) return;

        Entity ent = player.getWorld().getEntityById(tk.entityId);
        if (ent == null || !ent.isAlive()) return;

        double maxHp = (ent instanceof LivingEntity le) ? le.getMaxHealth() : 0.0;
        if (maxHp > 100.0) return;

        float clamped = MathHelper.clamp(scrollY, -3.0f, 3.0f);
        tk.distance = MathHelper.clamp(tk.distance + (clamped * 1.25), MIN_HOLD_DISTANCE, MAX_HOLD_DISTANCE);

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d impulse = look.multiply(0.30 * clamped);
        ent.addVelocity(impulse.x, impulse.y, impulse.z);
    }

    /* =================== wall orient handling =================== */

    private static void onWallOrient(ServerPlayerEntity player, int dYawSteps, int dPitchSteps) {
        if (!isCurrent(player)) return;

        WallAimState wa = WALL_AIM.get(player.getUuid());
        if (wa == null) return;

        // yaw wraps
        if (dYawSteps != 0) {
            int d = MathHelper.clamp(dYawSteps, -2, 2);
            wa.yawSteps = floorMod(wa.yawSteps + d, YAW_STEPS_MOD);
        }

        // pitch clamps
        if (dPitchSteps != 0) {
            int d = MathHelper.clamp(dPitchSteps, -2, 2);
            wa.pitchSteps = MathHelper.clamp(wa.pitchSteps + d, PITCH_MIN_STEPS, PITCH_MAX_STEPS);
        }

        sendWallPreview(player, true, wa.distanceBlocks, wa.yawSteps, wa.pitchSteps);
    }

    private static int floorMod(int a, int m) {
        int r = a % m;
        return (r < 0) ? r + m : r;
    }

    /* =================== particles =================== */

    private static void spawnGrabBurst(ServerPlayerEntity player, Entity target) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        double x = target.getX();
        double y = target.getBodyY(0.55);
        double z = target.getZ();

        sw.spawnParticles(
                OddParticles.TELEKINESIS,
                x, y, z,
                GRAB_BURST_COUNT,
                target.getWidth() * 0.6, target.getHeight() * 0.6, target.getWidth() * 0.6,
                0.20
        );

        sw.spawnParticles(
                ParticleTypes.FIREWORK,
                x, y, z,
                6,
                target.getWidth() * 0.3, target.getHeight() * 0.3, target.getWidth() * 0.3,
                0.05
        );
    }

    private static void spawnAura(ServerPlayerEntity player, Entity ent) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        sw.spawnParticles(
                OddParticles.TELEKINESIS,
                ent.getX(), ent.getBodyY(0.55), ent.getZ(),
                AURA_PARTICLES_PER_TICK,
                ent.getWidth() * 0.45, ent.getHeight() * 0.55, ent.getWidth() * 0.45,
                0.02
        );
    }

    /* =================== mechanics helpers =================== */

    private static void applyPlayerSlow(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                PLAYER_SLOWNESS_TICKS,
                PLAYER_SLOWNESS_AMP,
                false,
                false,
                false
        ));
    }

    /** Strongly kills drift/momentum. In air it's ~90% stop per tick. */
    private static void dampPlayerVelocity(ServerPlayerEntity player) {
        Vec3d v = player.getVelocity();
        double f = player.isOnGround() ? DAMP_GROUND : DAMP_AIR;

        Vec3d nv = v.multiply(f);
        if (nv.lengthSquared() < DAMP_EPS_SQ) nv = Vec3d.ZERO;

        player.setVelocity(nv);
        player.velocityModified = true;
    }

    private static void applySlowOnly(Entity ent) {
        ent.setVelocity(ent.getVelocity().multiply(0.55));
        if (ent instanceof LivingEntity le) {
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 3, false, false, true));
        }
    }

    private static Vec3d aimPoint(ServerPlayerEntity player, double distance) {
        Vec3d origin = player.getCameraPosVec(1.0f);
        Vec3d look = player.getRotationVec(1.0f);
        return origin.add(look.multiply(distance));
    }

    private static Entity raycastEntity(ServerPlayerEntity player, double range) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(range));

        Box searchBox = player.getBoundingBox().stretch(look.multiply(range)).expand(1.25);
        Predicate<Entity> pred = e -> e != player && e.isAlive() && !e.isSpectator();

        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, searchBox, pred, range * range);
        return hit != null ? hit.getEntity() : null;
    }

    /* =================== meter helpers =================== */

    private static float getEnergy(ServerPlayerEntity player) {
        return ENERGY.getOrDefault(player.getUuid(), ENERGY_MAX);
    }

    private static void setEnergy(ServerPlayerEntity player, float value) {
        ENERGY.put(player.getUuid(), MathHelper.clamp(value, 0.0f, ENERGY_MAX));
    }

    /* =================== net sync =================== */

    private static void sendHud(ServerPlayerEntity player, boolean active, float energy, float max) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeFloat(energy);
        buf.writeFloat(max);
        ServerPlayNetworking.send(player, S2C_TK_HUD, buf);
    }

    private static void sendWallPreview(ServerPlayerEntity player, boolean active, int distanceBlocks, int yawSteps, int pitchSteps) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeInt(distanceBlocks);
        buf.writeInt(yawSteps);
        buf.writeInt(pitchSteps);
        ServerPlayNetworking.send(player, S2C_WALL_PREVIEW, buf);
    }

    /* =================== client =================== */

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean tkActive = false;
        private static float tkEnergy = 0.0f;
        private static float tkMax = 1.0f;

        private static boolean wallAimActive = false;
        private static int wallAimDistance = 6;
        private static int wallAimYawSteps = 0;
        private static int wallAimPitchSteps = 0;

        private static boolean scrollHooked = false;
        private static GLFWScrollCallbackI previousScroll;

        private static TelekinesisLoopSound loopSound = null;

        public static void init() {
            // HUD state (TK)
            ClientPlayNetworking.registerGlobalReceiver(S2C_TK_HUD, (client, handler, buf, responder) -> {
                boolean active = buf.readBoolean();
                float energy = buf.readFloat();
                float max = buf.readFloat();
                client.execute(() -> {
                    tkActive = active;
                    tkEnergy = energy;
                    tkMax = Math.max(1.0f, max);
                });
            });

            // Wall preview state
            ClientPlayNetworking.registerGlobalReceiver(S2C_WALL_PREVIEW, (client, handler, buf, responder) -> {
                boolean active = buf.readBoolean();
                int dist = buf.readInt();
                int yawSteps = buf.readInt();
                int pitchSteps = buf.readInt();
                client.execute(() -> {
                    wallAimActive = active;
                    wallAimDistance = MathHelper.clamp(dist, WALL_DISTANCE_MIN, WALL_DISTANCE_MAX);
                    wallAimYawSteps = floorMod(yawSteps, YAW_STEPS_MOD);
                    wallAimPitchSteps = MathHelper.clamp(pitchSteps, PITCH_MIN_STEPS, PITCH_MAX_STEPS);
                });
            });

            // overlay + meter
            HudRenderCallback.EVENT.register((DrawContext ctx, float td) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;
                if (mc.options.hudHidden) return;

                int sw = ctx.getScaledWindowWidth();
                int sh = ctx.getScaledWindowHeight();

                if (isGlitchVisualActive()) {
                    renderGlitchOverlay(ctx, sw, sh, mc.player.age, td);
                }

                if (!tkActive) return;

                int barW = 120;
                int barH = 8;
                int x = (sw / 2) - (barW / 2);
                int y = sh - 56;

                ctx.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xAA000000);

                float pct = MathHelper.clamp(tkEnergy / tkMax, 0.0f, 1.0f);
                int fillW = (int) (barW * pct);

                int gold = 0xFFFFC800;
                ctx.fill(x, y, x + fillW, y + barH, gold);
                ctx.fill(x, y, x + fillW, y + 1, 0x66FFFFFF);
            });

            // install scroll hook after client start
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> installScrollHook());

            // looping sound while TK active
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client == null || client.player == null) return;

                if (tkActive) {
                    if (loopSound == null) {
                        loopSound = new TelekinesisLoopSound();
                        client.getSoundManager().play(loopSound);
                    }
                } else {
                    if (loopSound != null) {
                        client.getSoundManager().stop(loopSound);
                        loopSound = null;
                    }
                }
            });

            // WASD wall orientation while aiming (discrete presses)
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client == null || client.player == null) return;
                if (!wallAimActive) return;
                if (client.currentScreen != null) return;

                int dYaw = 0;
                int dPitch = 0;

                // A/D = yaw
                if (client.options.leftKey.wasPressed())  dYaw -= 1;
                if (client.options.rightKey.wasPressed()) dYaw += 1;

                // W/S = pitch (tilt)
                if (client.options.forwardKey.wasPressed()) dPitch += 1;
                if (client.options.backKey.wasPressed())    dPitch -= 1;

                if (dYaw != 0 || dPitch != 0) {
                    PacketByteBuf out = PacketByteBufs.create();
                    out.writeInt(dYaw);
                    out.writeInt(dPitch);
                    ClientPlayNetworking.send(C2S_WALL_ORIENT, out);
                }
            });
        }

        private static void installScrollHook() {
            if (scrollHooked) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            long handle = mc.getWindow().getHandle();

            previousScroll = GLFW.glfwSetScrollCallback(handle, (window, xoff, yoff) -> {
                if (previousScroll != null) previousScroll.invoke(window, xoff, yoff);

                if (!isGlitchVisualActive()) return;
                if (mc.currentScreen != null) return;
                if (mc.player == null) return;
                if (yoff == 0.0) return;

                PacketByteBuf out = PacketByteBufs.create();
                out.writeFloat((float) yoff);
                ClientPlayNetworking.send(C2S_GLITCH_SCROLL, out);
            });

            scrollHooked = true;
        }

        public static boolean isGlitchVisualActive() {
            return tkActive || wallAimActive;
        }

        public static boolean isWallAimActive() {
            return wallAimActive;
        }

        public static int wallAimDistanceBlocks() {
            return wallAimDistance;
        }

        public static int wallAimYawSteps() {
            return wallAimYawSteps;
        }

        public static int wallAimPitchSteps() {
            return wallAimPitchSteps;
        }

        /** Used by your GameRenderer FOV mixin. */
        public static double zoomMultiplier() {
            return isGlitchVisualActive() ? 0.92 : 1.0;
        }

        private static void renderGlitchOverlay(DrawContext ctx, int sw, int sh, int tick, float tickDelta) {
            boolean flicker = (tick % 40) < 2;
            int yellow = 0x00FFC800;

            int baseA = flicker ? 0x24 : 0x18;
            ctx.fill(0, 0, sw, sh, (baseA << 24) | yellow);

            int bands = flicker ? 10 : 7;
            for (int i = 0; i < bands; i++) {
                int s = mix(tick * 73 + i * 199 + (int) (tickDelta * 10));
                int y = floorMod(s, sh);
                int h = 2 + floorMod(s >>> 8, 6);
                int shift = -6 + floorMod(s >>> 16, 13);
                int a = (flicker ? 0x2E : 0x22) + floorMod(s >>> 24, 0x10);

                int y2 = Math.min(sh, y + h);
                ctx.fill(shift, y, shift + sw, y2, (a << 24) | yellow);
            }

            int blocks = flicker ? 14 : 10;
            for (int i = 0; i < blocks; i++) {
                int s = mix(tick * 997 + i * 911 + 0x9E3779B9);
                int w = 8 + floorMod(s >>> 8, 26);
                int h = 2 + floorMod(s >>> 16, 10);
                int x = floorMod(s, sw);
                int y = floorMod(s >>> 20, sh);
                int shift = -8 + floorMod(s >>> 12, 17);
                int a = (flicker ? 0x30 : 0x20) + floorMod(s >>> 24, 0x12);

                int x1 = x + shift;
                int y1 = y;
                int x2 = Math.min(sw, x1 + w);
                int y2 = Math.min(sh, y1 + h);

                if (x2 > 0 && y2 > 0 && x1 < sw && y1 < sh) {
                    ctx.fill(x1, y1, x2, y2, (a << 24) | yellow);
                }
            }
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

        /** Simple looping hum while TK is active (non-positional / relative). */
        private static final class TelekinesisLoopSound extends AbstractSoundInstance {
            TelekinesisLoopSound() {
                super(ModSounds.TELEKINESIS, SoundCategory.PLAYERS, Random.create());
                this.volume = 0.65f;
                this.pitch = 1.0f;

                this.repeat = true;
                this.repeatDelay = 0;

                this.relative = true;
                this.attenuationType = SoundInstance.AttenuationType.NONE;
            }
        }
    }
}
