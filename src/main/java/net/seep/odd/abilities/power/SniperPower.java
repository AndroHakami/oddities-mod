// FILE: src/main/java/net/seep/odd/abilities/power/SniperPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.sniper.entity.SniperGrappleAnchorEntity;
import net.seep.odd.abilities.sniper.entity.SniperGrappleShotEntity;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.item.ModItems;

import java.util.UUID;

public final class SniperPower implements Power {

    public static final Identifier SNIPER_CTRL_C2S = new Identifier(Oddities.MOD_ID, "sniper_ctrl_c2s");

    // Input flags (client -> server)
    public static final byte IN_JUMP = 1 << 0; // space cancels grapple

    private static final Object2ByteOpenHashMap<UUID> INPUT = new Object2ByteOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_ANCHOR = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_SHOT   = new Object2ObjectOpenHashMap<>();

    private static boolean inited = false;

    @Override public String id() { return "sniper"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 10; }
    @Override public long secondaryCooldownTicks() { return 20 * 6; } // 6s (tweak)

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/sniper_primary.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/sniper_grapple.png");
            default -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Summon a Sniper rifle. Hold Right Click to scope (custom scope effect + zoom). Left Click fires a hitscan shot (8 base damage). 2s fire cooldown. Infinite ammo.";
            case "secondary" ->
                    "Fire a grapple hook to a surface. You get pulled quickly toward it and can swing. The grapple auto-detaches when you arrive. Press Space to cancel early.";
            default -> "Sniper";
        };
    }

    @Override
    public String longDescription() {
        return "Sniper: summon a scoped hitscan rifle with a custom scope effect, and a fast grapple hook for traversal.";
    }

    /** Server-side power check */
    public static boolean hasSniper(ServerPlayerEntity sp) {
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "sniper".equals(current);
    }

    /** Call once in common init. */
    public static void init() {
        if (inited) return;
        inited = true;

        // Input packet (space cancel)
        ServerPlayNetworking.registerGlobalReceiver(SNIPER_CTRL_C2S, (server, player, handler, buf, responseSender) -> {
            final byte flags = buf.readByte();
            server.execute(() -> INPUT.put(player.getUuid(), flags));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            INPUT.removeByte(id);
            ACTIVE_ANCHOR.remove(id);
            ACTIVE_SHOT.remove(id);
        });

        // Server tick for grapple physics + cleanup
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                serverTick(p);
            }
        });
    }

    /** Per-player server tick */
    public static void serverTick(ServerPlayerEntity player) {
        if (!hasSniper(player)) return;

        UUID owner = player.getUuid();
        MinecraftServer server = player.getServer();
        if (server == null) return;

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
    }

    /** Read the last input flags we got from the client */
    public static byte getInputFlags(ServerPlayerEntity player) {
        return INPUT.getOrDefault(player.getUuid(), (byte)0);
    }

    public static boolean isGrappleEngaged(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        return ACTIVE_ANCHOR.containsKey(id) || ACTIVE_SHOT.containsKey(id);
    }

    /** Primary: give the Sniper item */
    @Override
    public void activate(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;

        // NOTE: You must register ModItems.SNIPER (see bottom of this message).
        ItemStack stack = new ItemStack(ModItems.SNIPER);

        // If already holding, just play a sound
        if (player.getMainHandStack().isOf(ModItems.SNIPER) || player.getOffHandStack().isOf(ModItems.SNIPER)) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.6f, 1.4f);
            return;
        }

        // Put in main hand if empty, else add to inv
        if (player.getMainHandStack().isEmpty()) {
            player.setStackInHand(Hand.MAIN_HAND, stack);
        } else if (player.getOffHandStack().isEmpty()) {
            player.setStackInHand(Hand.OFF_HAND, stack);
        } else {
            player.getInventory().insertStack(stack);
        }

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.6f, 1.6f);
    }

    /** Secondary: grapple hook */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;

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
        Vec3d vel = dir.multiply(2.35); // fast
        shot.setVelocity(vel);

        player.getWorld().spawnEntity(shot);
        ACTIVE_SHOT.put(owner, shot.getUuid());

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.65f, 1.25f);
    }

    /** Called by grapple shot when it anchors to a block */
    public static void bindAnchor(ServerPlayerEntity owner, SniperGrappleAnchorEntity anchor) {
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

        // clear the shot pointer when anchored
        ACTIVE_SHOT.remove(id);

        ACTIVE_ANCHOR.put(id, anchor.getUuid());

        // SFX
        owner.getWorld().playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.55f, 1.55f);
    }
}
