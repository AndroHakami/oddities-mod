package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.abilities.climber.entity.ClimberRopeAnchorEntity;
import net.seep.odd.abilities.climber.entity.ClimberRopeShotEntity;

import java.util.UUID;

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

    // per-player active anchor UUID (for quick detach/toggle)
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_ANCHOR = new Object2ObjectOpenHashMap<>();

    @Override public String id() { return "climber"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 10; }           // 0.5s
    @Override public long secondaryCooldownTicks() { return 10; }  // 0.5s

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
                    "Throw a rope anchor (arcing). On hit: swing tether. Space shortens rope, Shift lengthens (max 20m). Tap again to detach.";
            case "secondary" ->
                    "Throw a pull-rope. If it hits a living target with max HP <= 50, it tethers + pulls them to you, applies Nausea (2s) + Slowness III (1s).";
            default -> "Climber";
        };
    }

    @Override
    public String longDescription() {
        return "Climber: passive wall climbing on any surface (Space up / Shift down while against a wall). "
                + "Primary: arcing rope anchor for realistic swinging + adjustable rope length. "
                + "Secondary: tether-pull on weaker targets (max HP <= 50).";
    }

    /** Power check (server-side) */
    public static boolean hasClimber(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "climber".equals(current);
    }

    /** Call from your mod init (common) once. */
    public static void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(CLIMBER_CTRL_C2S, (server, player, handler, buf, responseSender) -> {
            final byte flags = buf.readByte();
            server.execute(() -> INPUT.put(player.getUuid(), flags));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            INPUT.removeByte(id);
            ACTIVE_ANCHOR.remove(id);
        });
    }

    /** Your central server tick should call this for players (same style as ConquerPower.serverTick). */
    public static void serverTick(ServerPlayerEntity player) {
        if (!hasClimber(player)) return;

        // Passive wall climbing (fast + controlled)
        doWallClimb(player, INPUT.getOrDefault(player.getUuid(), (byte)0));

        // Clean up stale anchor pointer if needed
        UUID anchorId = ACTIVE_ANCHOR.get(player.getUuid());
        if (anchorId != null) {
            MinecraftServer server = player.getServer();
            if (server == null) {
                ACTIVE_ANCHOR.remove(player.getUuid());
                return;
            }
            ClimberRopeAnchorEntity anchor = ClimberRopeAnchorEntity.findAnchor(server, anchorId);
            if (anchor == null || !anchor.isAlive()) {
                ACTIVE_ANCHOR.remove(player.getUuid());
            }
        }
    }

    private static void doWallClimb(ServerPlayerEntity player, byte in) {
        // Don’t interfere with creative flight
        if (player.getAbilities().flying) return;
        if (player.isFallFlying()) return;
        if (player.isSpectator()) return;

        // Must be pressing into a wall / colliding horizontally
        if (!player.horizontalCollision) return;

        boolean jump  = (in & IN_JUMP) != 0;
        boolean sneak = (in & IN_SNEAK) != 0;

        // Create desired horizontal movement from WASD relative to view
        boolean f = (in & IN_FORWARD) != 0;
        boolean b = (in & IN_BACK) != 0;
        boolean l = (in & IN_LEFT) != 0;
        boolean r = (in & IN_RIGHT) != 0;

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d forward = new Vec3d(look.x, 0, look.z);
        if (forward.lengthSquared() > 1.0e-6) forward = forward.normalize();
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);

        Vec3d wish = Vec3d.ZERO;
        if (f) wish = wish.add(forward);
        if (b) wish = wish.subtract(forward);
        if (l) wish = wish.subtract(right);
        if (r) wish = wish.add(right);

        // “Fast and accurate” on-wall speed
        double horizSpeed = 0.22;
        Vec3d horiz = wish.lengthSquared() > 1.0e-6 ? wish.normalize().multiply(horizSpeed) : Vec3d.ZERO;

        // Vertical control
        double vy;
        if (jump && !sneak)       vy = 0.32; // climb up
        else if (sneak && !jump)  vy = -0.26; // climb down
        else                      vy = 0.0;   // stick

        Vec3d v = player.getVelocity();

        // Keep motion snappy but not jittery: blend a bit
        Vec3d target = new Vec3d(
                MathHelper.lerp(0.55, v.x, horiz.x),
                MathHelper.lerp(0.75, v.y, vy),
                MathHelper.lerp(0.55, v.z, horiz.z)
        );

        player.setVelocity(target);
        player.fallDistance = 0.0f;
        // Small anti-slide so you don’t “creep” down the wall when sticking
        if (!jump && !sneak && player.getVelocity().y < 0) {
            player.setVelocity(player.getVelocity().x, 0.0, player.getVelocity().z);
        }
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        // Primary: toggle detach if already anchored
        UUID owner = player.getUuid();
        UUID anchorId = ACTIVE_ANCHOR.get(owner);
        if (anchorId != null) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                ClimberRopeAnchorEntity anchor = ClimberRopeAnchorEntity.findAnchor(server, anchorId);
                if (anchor != null) anchor.discard();
            }
            ACTIVE_ANCHOR.remove(owner);

            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.9f, 0.8f);
            return;
        }

        // Throw an arcing rope shot that anchors to blocks
        if (player.getWorld().isClient) return;

        ClimberRopeShotEntity shot = new ClimberRopeShotEntity(ModEntities.CLIMBER_ROPE_SHOT, player.getWorld());
        shot.setOwner(player);
        shot.setMode(ClimberRopeShotEntity.Mode.ANCHOR);

        // start at hand-ish
        Vec3d start = player.getPos().add(0, player.getStandingEyeHeight() - 0.15, 0);
        shot.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), player.getPitch());

        // arcing velocity
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d vel = dir.multiply(1.45).add(0, 0.18, 0);
        shot.setVelocity(vel);

        player.getWorld().spawnEntity(shot);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.9f, 0.9f);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // Secondary: arcing rope shot that tethers to living entities and pulls them
        if (player.getWorld().isClient) return;

        ClimberRopeShotEntity shot = new ClimberRopeShotEntity(ModEntities.CLIMBER_ROPE_SHOT, player.getWorld());
        shot.setOwner(player);
        shot.setMode(ClimberRopeShotEntity.Mode.PULL);

        Vec3d start = player.getPos().add(0, player.getStandingEyeHeight() - 0.15, 0);
        shot.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), player.getPitch());

        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d vel = dir.multiply(1.55).add(0, 0.12, 0);
        shot.setVelocity(vel);

        player.getWorld().spawnEntity(shot);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.9f, 1.05f);
    }

    /** Called by anchor entity once it’s spawned to bind it to the player for toggling. */
    public static void bindAnchor(ServerPlayerEntity owner, ClimberRopeAnchorEntity anchor) {
        UUID id = owner.getUuid();

        // kill old if somehow exists
        UUID existing = ACTIVE_ANCHOR.get(id);
        if (existing != null) {
            MinecraftServer server = owner.getServer();
            if (server != null) {
                ClimberRopeAnchorEntity prev = ClimberRopeAnchorEntity.findAnchor(server, existing);
                if (prev != null) prev.discard();
            }
        }

        ACTIVE_ANCHOR.put(id, anchor.getUuid());
    }

    /** Server-authoritative rope length control inputs. */
    public static byte getInputFlags(ServerPlayerEntity player) {
        return INPUT.getOrDefault(player.getUuid(), (byte)0);
    }

    /** Waist/pants attach point (server + client should match). */
    public static Vec3d ropeOrigin(PlayerEntity p) {
        return p.getPos().add(0.0, p.getHeight() * 0.45, 0.0);
    }
}
