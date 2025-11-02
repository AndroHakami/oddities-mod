package net.seep.odd.abilities.net;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.client.AbilityHudOverlay;
import net.seep.odd.abilities.client.ClientCooldowns;

public final class PowerNetworking {
    private PowerNetworking(){}

    /* ===================== CHANNELS ===================== */

    // S2C
    public static final Identifier S2C_SYNC_POWER = new Identifier(Oddities.MOD_ID, "power/sync_power_id");
    public static final Identifier S2C_COOLDOWN   = new Identifier(Oddities.MOD_ID, "power/cooldown");
    public static final Identifier S2C_CHARGES    = new Identifier(Oddities.MOD_ID, "power/charges");

    // C2S (NEW): hold lifecycle
    public static final Identifier C2S_HOLD_START   = new Identifier(Oddities.MOD_ID, "power/hold_start");
    public static final Identifier C2S_HOLD_TICK    = new Identifier(Oddities.MOD_ID, "power/hold_tick");
    public static final Identifier C2S_HOLD_RELEASE = new Identifier(Oddities.MOD_ID, "power/hold_release");

    /* ===================== SERVER REG ===================== */

    /** Call from common init. */
    public static void initServer() {
        // HOLD START
        ServerPlayNetworking.registerGlobalReceiver(C2S_HOLD_START, (server, player, handler, buf, responseSender) -> {
            final String slot = buf.readString(16);
            server.execute(() -> PowerAPI.holdStart(player, slot));
        });

        // HOLD TICK
        ServerPlayNetworking.registerGlobalReceiver(C2S_HOLD_TICK, (server, player, handler, buf, responseSender) -> {
            final String slot = buf.readString(16);
            final int heldTicks = buf.readVarInt();
            server.execute(() -> PowerAPI.holdTick(player, slot, heldTicks));
        });

        // HOLD RELEASE
        ServerPlayNetworking.registerGlobalReceiver(C2S_HOLD_RELEASE, (server, player, handler, buf, responseSender) -> {
            final String slot = buf.readString(16);
            final int heldTicks = buf.readVarInt();
            final boolean canceled = buf.readBoolean();
            server.execute(() -> PowerAPI.holdRelease(player, slot, heldTicks, canceled));
        });
    }

    /* ===================== CLIENT REG ===================== */

    /** Call from client init. */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        // 1) power id sync -> clear local UI state, AbilityHudOverlay reads it on render
        ClientPlayNetworking.registerGlobalReceiver(S2C_SYNC_POWER, (client, handler, buf, response) -> {
            final String id = buf.readString(64);
            client.execute(() -> {
                // this is what your HUD reads
                AbilityHudOverlay.ClientCharges.clear();
                ClientCooldowns.clear();
                net.seep.odd.abilities.client.ClientPowerHolder.set(id);
            });
        });

        // 2) simple cooldown update (non-charge)
        ClientPlayNetworking.registerGlobalReceiver(S2C_COOLDOWN, (client, handler, buf, response) -> {
            final String slot = buf.readString(16);
            final long remain = buf.readVarLong();
            client.execute(() -> ClientCooldowns.set(slot, (int)Math.max(0L, remain)));
        });

        // 3) charge lane snapshot
        ClientPlayNetworking.registerGlobalReceiver(S2C_CHARGES, (client, handler, buf, response) -> {
            final String slot = buf.readString(16);
            final int have = buf.readVarInt();
            final int max  = buf.readVarInt();
            final long recharge  = buf.readVarLong();
            final long nextReady = buf.readVarLong();
            final long serverNow = buf.readVarLong();
            client.execute(() -> AbilityHudOverlay.ClientCharges.set(slot, have, max, recharge, nextReady, serverNow));
        });
    }

    /* ===================== CLIENT SEND HELPERS ===================== */

    @Environment(EnvType.CLIENT)
    public static void sendHoldStart(String slot) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(slot, 16);
        ClientPlayNetworking.send(C2S_HOLD_START, out);
    }
    @Environment(EnvType.CLIENT)
    public static void sendHoldTick(String slot, int heldTicks) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(slot, 16);
        out.writeVarInt(heldTicks);
        ClientPlayNetworking.send(C2S_HOLD_TICK, out);
    }
    @Environment(EnvType.CLIENT)
    public static void sendHoldRelease(String slot, int heldTicks, boolean canceled) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(slot, 16);
        out.writeVarInt(heldTicks);
        out.writeBoolean(canceled);
        ClientPlayNetworking.send(C2S_HOLD_RELEASE, out);
    }

    /* ===================== S2C EMITTERS (SERVER SIDE) ===================== */

    public static void syncTo(ServerPlayerEntity sp, String id) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(id == null ? "" : id, 64);
        ServerPlayNetworking.send(sp, S2C_SYNC_POWER, out);
    }

    public static void sendCooldown(ServerPlayerEntity sp, String slot, long remain) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(slot, 16);
        out.writeVarLong(Math.max(0L, remain));
        ServerPlayNetworking.send(sp, S2C_COOLDOWN, out);
    }

    public static void sendCharges(ServerPlayerEntity sp, String slot, int have, int max, long recharge, long nextReady, long serverNow) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(slot, 16);
        out.writeVarInt(have);
        out.writeVarInt(max);
        out.writeVarLong(recharge);
        out.writeVarLong(nextReady);
        out.writeVarLong(serverNow);
        ServerPlayNetworking.send(sp, S2C_CHARGES, out);
    }
}
