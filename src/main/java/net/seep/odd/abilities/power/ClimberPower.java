// FILE: src/main/java/net/seep/odd/abilities/power/ClimberPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.climber.entity.ClimberRopeAnchorEntity;
import net.seep.odd.abilities.climber.entity.ClimberRopeShotEntity;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.status.ModStatusEffects;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClimberPower implements Power {

    public static final Identifier CLIMBER_CTRL_C2S = new Identifier(Oddities.MOD_ID, "climber_ctrl_c2s");

    // Input flags (client -> server)
    public static final byte IN_FORWARD = 1 << 0;
    public static final byte IN_BACK    = 1 << 1;
    public static final byte IN_LEFT    = 1 << 2;
    public static final byte IN_RIGHT   = 1 << 3;
    public static final byte IN_JUMP    = 1 << 4; // space
    public static final byte IN_SNEAK   = 1 << 5; // shift

    // per-player last input flags
    private static final Object2ByteOpenHashMap<UUID> INPUT = new Object2ByteOpenHashMap<>();

    // per-player active anchor UUID
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_ANCHOR = new Object2ObjectOpenHashMap<>();

    // per-player active PRIMARY hook shot UUID (flying/retracting)
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_PRIMARY_SHOT = new Object2ObjectOpenHashMap<>();

    // Detect respawn: ServerPlayerEntity instance changes => entity id changes.
    private static final ConcurrentHashMap<UUID, Integer> LAST_ENTITY_ID = new ConcurrentHashMap<>();

    @Override public String id() { return "climber"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 10; }
    @Override public long secondaryCooldownTicks() { return 20 * 10; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/climber_rope.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/climber_pull.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Throw forward a rope ";
            case "secondary" ->
                    "Throw a pull-rope. If it hits a living target with max HP <= 50, it tethers + pulls them to you, applies Nausea (2s) + Slowness III (1s).";
            default -> "Climber";
        };
    }

    @Override
    public String longDescription() {
        return "Climber: passive wall climbing on any surface (Space up / Shift down while against a wall). "
                + "Primary: 30m grappling hook with swing physics + adjustable rope length; tap again mid-air retracts the hook. "
                + "Secondary: tether-pull on weaker targets (max HP <= 50).";
    }

    /** Power check (server-side) */
    public static boolean hasClimber(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "climber".equals(current);
    }

    public static boolean isPowerless(ServerPlayerEntity player) {
        return player != null && player.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    /** Call once in common init. */
    public static void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(CLIMBER_CTRL_C2S, (server, player, handler, buf, responseSender) -> {
            final byte flags = buf.readByte();
            server.execute(() -> INPUT.put(player.getUuid(), flags));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            INPUT.removeByte(id);
            ACTIVE_ANCHOR.remove(id);
            ACTIVE_PRIMARY_SHOT.remove(id);
            LAST_ENTITY_ID.remove(id);
        });
    }

    /** Force-detach rope/hook and clear state (used for POWERLESS + respawn). */
    public static void forceDetach(ServerPlayerEntity player) {
        if (player == null) return;

        UUID owner = player.getUuid();
        MinecraftServer server = player.getServer();

        UUID anchorId = ACTIVE_ANCHOR.get(owner);
        if (anchorId != null && server != null) {
            ClimberRopeAnchorEntity anchor = ClimberRopeAnchorEntity.findAnchor(server, anchorId);
            if (anchor != null) anchor.discard();
        }

        UUID shotId = ACTIVE_PRIMARY_SHOT.get(owner);
        if (shotId != null && server != null) {
            ClimberRopeShotEntity shot = ClimberRopeShotEntity.findShot(server, shotId);
            if (shot != null) shot.discard();
        }

        ACTIVE_ANCHOR.remove(owner);
        ACTIVE_PRIMARY_SHOT.remove(owner);
        INPUT.removeByte(owner);
    }

    /** Your guaranteed server tick can call this (we’ll also call it from the mixin tick). */
    public static void serverTick(ServerPlayerEntity player) {
        if (!hasClimber(player)) return;

        // --- Respawn fix ---
        // After death, Minecraft creates a NEW ServerPlayerEntity with same UUID.
        // If we keep old rope state by UUID, passive climbing gets stuck disabled forever.
        int nowEntityId = player.getId();
        Integer last = LAST_ENTITY_ID.get(player.getUuid());
        if (last == null) {
            LAST_ENTITY_ID.put(player.getUuid(), nowEntityId);
        } else if (last != nowEntityId) {
            // respawn detected
            LAST_ENTITY_ID.put(player.getUuid(), nowEntityId);
            forceDetach(player);
        }

        // --- POWERLESS: detach immediately + disable everything climber-related ---
        if (isPowerless(player)) {
            forceDetach(player);
            return;
        }

        // Cleanup stale anchor pointer
        UUID anchorId = ACTIVE_ANCHOR.get(player.getUuid());
        if (anchorId != null) {
            MinecraftServer server = player.getServer();
            if (server == null) {
                ACTIVE_ANCHOR.remove(player.getUuid());
            } else {
                ClimberRopeAnchorEntity anchor = ClimberRopeAnchorEntity.findAnchor(server, anchorId);
                if (anchor == null || !anchor.isAlive()) ACTIVE_ANCHOR.remove(player.getUuid());
            }
        }

        // Cleanup stale shot pointer
        UUID shotId = ACTIVE_PRIMARY_SHOT.get(player.getUuid());
        if (shotId != null) {
            MinecraftServer server = player.getServer();
            if (server == null) {
                ACTIVE_PRIMARY_SHOT.remove(player.getUuid());
            } else {
                ClimberRopeShotEntity shot = ClimberRopeShotEntity.findShot(server, shotId);
                if (shot == null || !shot.isAlive() || shot.getMode() != ClimberRopeShotEntity.Mode.ANCHOR) {
                    ACTIVE_PRIMARY_SHOT.remove(player.getUuid());
                }
            }
        }
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;

        // POWERLESS: detach + deny
        if (isPowerless(player)) {
            forceDetach(player);
            return;
        }

        UUID owner = player.getUuid();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 1) If anchored -> detach (discard anchor)
        UUID anchorId = ACTIVE_ANCHOR.get(owner);
        if (anchorId != null) {
            ClimberRopeAnchorEntity anchor = ClimberRopeAnchorEntity.findAnchor(server, anchorId);
            if (anchor != null) anchor.discard();
            ACTIVE_ANCHOR.remove(owner);
            return;
        }

        // 2) If primary hook shot exists -> retract it (or kill if already retracting)
        UUID shotId = ACTIVE_PRIMARY_SHOT.get(owner);
        if (shotId != null) {
            ClimberRopeShotEntity shot = ClimberRopeShotEntity.findShot(server, shotId);
            if (shot != null && shot.isAlive()) {
                if (shot.isReturning()) {
                    shot.discard();
                    ACTIVE_PRIMARY_SHOT.remove(owner);
                } else {
                    shot.startReturn();
                }
                return;
            } else {
                ACTIVE_PRIMARY_SHOT.remove(owner);
            }
        }

        // 3) Spawn a new PRIMARY hook shot (ANCHOR mode) — only if none exist
        ClimberRopeShotEntity shot = new ClimberRopeShotEntity(ModEntities.CLIMBER_ROPE_SHOT, player.getWorld());
        shot.setOwner(player);
        shot.setOwnerUuid(player.getUuid());
        shot.setMode(ClimberRopeShotEntity.Mode.ANCHOR);

        Vec3d start = player.getPos().add(0, player.getStandingEyeHeight() - 0.15, 0);
        shot.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), player.getPitch());
        shot.setStartPos(start);

        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d vel = dir.multiply(1.45).add(0, 0.18, 0);
        shot.setVelocity(vel);

        player.getWorld().spawnEntity(shot);
        ACTIVE_PRIMARY_SHOT.put(owner, shot.getUuid());
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;

        // POWERLESS: detach + deny
        if (isPowerless(player)) {
            forceDetach(player);
            return;
        }

        ClimberRopeShotEntity shot = new ClimberRopeShotEntity(ModEntities.CLIMBER_ROPE_SHOT, player.getWorld());
        shot.setOwner(player);
        shot.setOwnerUuid(player.getUuid());
        shot.setMode(ClimberRopeShotEntity.Mode.PULL);

        Vec3d start = player.getPos().add(0, player.getStandingEyeHeight() - 0.15, 0);
        shot.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), player.getPitch());
        shot.setStartPos(start);

        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d vel = dir.multiply(1.55).add(0, 0.12, 0);
        shot.setVelocity(vel);

        player.getWorld().spawnEntity(shot);
    }

    /** Called by anchor entity once it’s spawned to bind it to the player for toggling. */
    public static void bindAnchor(ServerPlayerEntity owner, ClimberRopeAnchorEntity anchor) {
        UUID id = owner.getUuid();

        // kill old anchor if exists
        UUID existing = ACTIVE_ANCHOR.get(id);
        if (existing != null) {
            MinecraftServer server = owner.getServer();
            if (server != null) {
                ClimberRopeAnchorEntity prev = ClimberRopeAnchorEntity.findAnchor(server, existing);
                if (prev != null) prev.discard();
            }
        }

        // clear the "shot" slot once we successfully anchored
        ACTIVE_PRIMARY_SHOT.remove(id);

        ACTIVE_ANCHOR.put(id, anchor.getUuid());
    }

    /** Server-authoritative rope length control inputs. */
    public static byte getInputFlags(ServerPlayerEntity player) {
        return INPUT.getOrDefault(player.getUuid(), (byte)0);
    }

    public static boolean isPrimaryEngaged(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        return ACTIVE_ANCHOR.containsKey(id) || ACTIVE_PRIMARY_SHOT.containsKey(id);
    }

    public static boolean hasClimberAnySide(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) {
            return hasClimber(sp);
        }
        return net.seep.odd.abilities.climber.net.ClimberClimbNetworking.canClimbAnySide(player.getUuid());
    }

    /** Robust "touching wall" test (also used for mining-speed bypass on server). */
    public static boolean isTouchingWall(ServerPlayerEntity sp) {
        World w = sp.getWorld();
        Box bb = sp.getBoundingBox();

        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;

        // sample 3 heights (feet/mid/head)
        double y0 = bb.minY + 0.10;
        double y1 = bb.minY + sp.getHeight() * 0.55;
        double y2 = bb.maxY - 0.10;

        double eps = 0.02;

        // east/west faces
        if (solidAt(w, bb.minX - eps, y0, cz) || solidAt(w, bb.minX - eps, y1, cz) || solidAt(w, bb.minX - eps, y2, cz)) return true;
        if (solidAt(w, bb.maxX + eps, y0, cz) || solidAt(w, bb.maxX + eps, y1, cz) || solidAt(w, bb.maxX + eps, y2, cz)) return true;

        // north/south faces
        if (solidAt(w, cx, y0, bb.minZ - eps) || solidAt(w, cx, y1, bb.minZ - eps) || solidAt(w, cx, y2, bb.minZ - eps)) return true;
        if (solidAt(w, cx, y0, bb.maxZ + eps) || solidAt(w, cx, y1, bb.maxZ + eps) || solidAt(w, cx, y2, bb.maxZ + eps)) return true;

        return false;
    }

    private static boolean solidAt(World w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        BlockState st = w.getBlockState(bp);
        if (st.isAir()) return false;
        return !st.getCollisionShape(w, bp).isEmpty();
    }

    /** Waist/pants attach point. */
    public static Vec3d ropeOrigin(PlayerEntity p) {
        return p.getPos().add(0.0, p.getHeight() * 0.45, 0.0);
    }
}