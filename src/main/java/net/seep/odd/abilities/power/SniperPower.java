// FILE: src/main/java/net/seep/odd/abilities/power/SniperPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.sniper.entity.SniperGrappleAnchorEntity;
import net.seep.odd.abilities.sniper.entity.SniperGrappleShotEntity;
import net.seep.odd.abilities.sniper.net.SniperGlideServer;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.status.ModStatusEffects;

import java.util.UUID;

public final class SniperPower implements Power {

    public static final Identifier SNIPER_CTRL_C2S = new Identifier(Oddities.MOD_ID, "sniper_ctrl_c2s");

    // Input flags (client -> server)
    public static final byte IN_JUMP = 1 << 0; // space (grapple cancel)

    private static final Object2ByteOpenHashMap<UUID> INPUT = new Object2ByteOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_ANCHOR = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_SHOT   = new Object2ObjectOpenHashMap<>();

    private static boolean inited = false;

    /* =================== POWERLESS override helpers =================== */
    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

    public static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    public static void warnPowerlessOncePerSec(ServerPlayerEntity p) {
        if (p == null) return;
        long now = p.getWorld().getTime();
        long next = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < next) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(Text.literal("§cYou are powerless."), true);
    }

    @Override public String id() { return "sniper"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }              // toggle
    @Override public long secondaryCooldownTicks() { return 20 * 6; } // grapple

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/sniper_primary.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/sniper_grapple.png");
            default -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }


    @Override
    public String longDescription() {
        return "Strike down your opponents with a long ranged sniper, equipped with a grapple and a parachute nothing will stop you from landing your shots!";
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "PARACHUTE";
            case "secondary" -> "GRAPPLE SHOT";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Arm/Disarm your parachute, hold spac to hover in the air for a limited time while armed.";
            case "secondary" -> "Fire a fast grapple shot to quickly reposition.";
            default -> "Sun";
        };
    }

    /** Server-side power check */
    public static boolean hasSniper(ServerPlayerEntity sp) {
        String current = PowerAPI.get(sp);
        return "sniper".equals(current);
    }

    /** Server-side gate for using sniper actions */
    private static boolean canUse(ServerPlayerEntity sp) {
        return sp != null && hasSniper(sp) && !isPowerless(sp);
    }

    /** ✅ Used by grapple entities (and any other code) to read last input flags. */
    public static byte getInputFlags(ServerPlayerEntity player) {
        if (!canUse(player)) return (byte)0;
        return INPUT.getOrDefault(player.getUuid(), (byte)0);
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        cancelGrapple(player);
        // hard-disarm parachute system (glide server will also gate by power, but this makes it immediate)
        SniperGlideServer.setArmed(player, false);
    }

    /** Call once in common init. */
    public static void init() {
        if (inited) return;
        inited = true;

        // Input packet (space cancel for grapple; sent regardless of holding the sniper item)
        ServerPlayNetworking.registerGlobalReceiver(SNIPER_CTRL_C2S, (server, player, handler, buf, responseSender) -> {
            final byte flags = buf.readByte();
            server.execute(() -> {
                // ignore input from non-snipers / powerless
                if (!canUse(player)) {
                    INPUT.removeByte(player.getUuid());
                    return;
                }
                INPUT.put(player.getUuid(), flags);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            INPUT.removeByte(id);
            ACTIVE_ANCHOR.remove(id);
            ACTIVE_SHOT.remove(id);
            WARN_UNTIL.removeLong(id);
            // no need to clear SniperGlideServer here; it clears itself on disconnect
        });

        // Grapple cleanup + hard enforcement of powerless / lost-power behavior
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                serverTick(p);
            }
        });
    }

    /** Per-player server tick */
    public static void serverTick(ServerPlayerEntity player) {
        UUID owner = player.getUuid();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Not sniper anymore: stop grapple, clear input, disarm parachute
        if (!hasSniper(player)) {
            if (isGrappleEngaged(player)) cancelGrapple(player);
            INPUT.removeByte(owner);
            SniperGlideServer.setArmed(player, false);
            return;
        }

        // POWERLESS: stop grapple + disarm parachute + clear input
        if (isPowerless(player)) {
            if (isGrappleEngaged(player)) {
                warnPowerlessOncePerSec(player);
                cancelGrapple(player);
            }
            INPUT.removeByte(owner);
            SniperGlideServer.setArmed(player, false);
            return;
        }

        // Cleanup stale anchor pointer
        UUID anchorId = ACTIVE_ANCHOR.get(owner);
        if (anchorId != null) {
            SniperGrappleAnchorEntity anchor = SniperGrappleAnchorEntity.findAnchor(server, anchorId);
            if (anchor == null || !anchor.isAlive()) ACTIVE_ANCHOR.remove(owner);
        }

        // Cleanup stale shot pointer
        UUID shotId = ACTIVE_SHOT.get(owner);
        if (shotId != null) {
            SniperGrappleShotEntity shot = SniperGrappleShotEntity.findShot(server, shotId);
            if (shot == null || !shot.isAlive()) ACTIVE_SHOT.remove(owner);
        }

        // If jump is held this tick and a grapple exists, hard-cancel (in addition to entity-side checks)
        byte flags = INPUT.getOrDefault(owner, (byte)0);
        boolean jumpHeld = (flags & IN_JUMP) != 0;
        if (jumpHeld && isGrappleEngaged(player)) {
            cancelGrapple(player);
        }
    }

    public static boolean isGrappleEngaged(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        return ACTIVE_ANCHOR.containsKey(id) || ACTIVE_SHOT.containsKey(id);
    }

    private static void cancelGrapple(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        UUID id = player.getUuid();

        UUID anchorId = ACTIVE_ANCHOR.remove(id);
        if (anchorId != null && server != null) {
            SniperGrappleAnchorEntity a = SniperGrappleAnchorEntity.findAnchor(server, anchorId);
            if (a != null) a.discard();
        }

        UUID shotId = ACTIVE_SHOT.remove(id);
        if (shotId != null && server != null) {
            SniperGrappleShotEntity s = SniperGrappleShotEntity.findShot(server, shotId);
            if (s != null) s.discard();
        }
    }

    /* ===================== Primary: toggle Parachute ARM (SniperGlideServer) ===================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;

        if (!hasSniper(player)) {
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
            player.sendMessage(Text.literal("§cOnly Sniper can use this."), true);
            return;
        }

        if (isPowerless(player)) {
            warnPowerlessOncePerSec(player);
            cancelGrapple(player);
            SniperGlideServer.setArmed(player, false);
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
            return;
        }

        boolean newArmed = !SniperGlideServer.isArmed(player);
        SniperGlideServer.setArmed(player, newArmed);

        player.playSound(
                newArmed ? SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                SoundCategory.PLAYERS,
                0.65f,
                newArmed ? 1.35f : 0.75f
        );

        player.sendMessage(Text.literal(newArmed ? "§aParachute: ARMED" : "§7Parachute: DISARMED"), true);
    }

    /* ===================== Secondary: grapple ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;

        if (!hasSniper(player)) {
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
            player.sendMessage(Text.literal("§cOnly Sniper can use this."), true);
            return;
        }

        if (isPowerless(player)) {
            warnPowerlessOncePerSec(player);
            cancelGrapple(player);
            SniperGlideServer.setArmed(player, false);
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
            return;
        }

        UUID owner = player.getUuid();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // If currently grappling: cancel
        UUID anchorId = ACTIVE_ANCHOR.get(owner);
        if (anchorId != null) {
            SniperGrappleAnchorEntity a = SniperGrappleAnchorEntity.findAnchor(server, anchorId);
            if (a != null) a.discard();
            ACTIVE_ANCHOR.remove(owner);
            return;
        }

        UUID shotId = ACTIVE_SHOT.get(owner);
        if (shotId != null) {
            SniperGrappleShotEntity s = SniperGrappleShotEntity.findShot(server, shotId);
            if (s != null) s.discard();
            ACTIVE_SHOT.remove(owner);
            return;
        }

        // Spawn a new grapple shot
        SniperGrappleShotEntity shot = new SniperGrappleShotEntity(ModEntities.SNIPER_GRAPPLE_SHOT, player.getWorld());
        shot.setOwner(player);
        shot.setOwnerUuid(player.getUuid());

        Vec3d start = player.getPos().add(0, player.getStandingEyeHeight() - 0.12, 0);
        shot.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), player.getPitch());
        shot.setStartPos(start);

        Vec3d dir = player.getRotationVec(1.0f).normalize();
        shot.setVelocity(dir.multiply(2.35));

        player.getWorld().spawnEntity(shot);
        ACTIVE_SHOT.put(owner, shot.getUuid());

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.65f, 1.25f);
    }

    /** Called by grapple shot when it anchors to a block */
    public static void bindAnchor(ServerPlayerEntity owner, SniperGrappleAnchorEntity anchor) {
        if (owner == null || anchor == null) return;

        if (!canUse(owner)) {
            anchor.discard();
            return;
        }

        UUID id = owner.getUuid();

        // kill old anchor if exists
        UUID existing = ACTIVE_ANCHOR.get(id);
        if (existing != null) {
            MinecraftServer server = owner.getServer();
            if (server != null) {
                SniperGrappleAnchorEntity prev = SniperGrappleAnchorEntity.findAnchor(server, existing);
                if (prev != null) prev.discard();
            }
        }

        // clear shot pointer when anchored
        ACTIVE_SHOT.remove(id);
        ACTIVE_ANCHOR.put(id, anchor.getUuid());

        owner.getWorld().playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.55f, 1.55f);
    }
}