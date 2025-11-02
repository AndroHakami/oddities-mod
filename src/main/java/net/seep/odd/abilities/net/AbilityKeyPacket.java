package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.PowerAPI;

public final class AbilityKeyPacket {
    private AbilityKeyPacket() {}

    /** Simple “press” activations (primary/secondary/third/fourth/overview). */
    public static final Identifier ID = new Identifier("oddities", "ability_key");

    /** Hold lifecycle (generic; we’ll use slot = "secondary"). */
    public static final Identifier HOLD_START   = new Identifier("oddities", "ability_hold_start");
    public static final Identifier HOLD_TICK    = new Identifier("oddities", "ability_hold_tick");
    public static final Identifier HOLD_RELEASE = new Identifier("oddities", "ability_hold_release");

    /* ===================== CLIENT HELPERS ===================== */

    // Send from client to server (simple press)
    public static PacketByteBuf makeBuf(String abilitySlot) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(abilitySlot, 16);
        return buf;
    }

    // Hold: start / tick / release
    public static PacketByteBuf makeHoldStartBuf(String slot) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(slot, 16);
        return buf;
    }

    public static PacketByteBuf makeHoldTickBuf(String slot, int heldTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(slot, 16);
        buf.writeVarInt(heldTicks);
        return buf;
    }

    public static PacketByteBuf makeHoldReleaseBuf(String slot, int heldTicks, boolean canceled) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(slot, 16);
        buf.writeVarInt(heldTicks);
        buf.writeBoolean(canceled);
        return buf;
    }

    /* ===================== SERVER REGISTRATION ===================== */

    // Register all receivers (press + hold lifecycle)
    public static void registerServerReceiver() {
        // Simple press
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            final String slot = buf.readString(16);
            server.execute(() -> handleAbilityKey(player, slot));
        });

        // Hold start
        ServerPlayNetworking.registerGlobalReceiver(HOLD_START, (server, player, handler, buf, responseSender) -> {
            final String slot = buf.readString(16);
            server.execute(() -> PowerAPI.holdStart(player, slot));
        });

        // Hold tick
        ServerPlayNetworking.registerGlobalReceiver(HOLD_TICK, (server, player, handler, buf, responseSender) -> {
            final String slot = buf.readString(16);
            final int held = buf.readVarInt();
            server.execute(() -> PowerAPI.holdTick(player, slot, held));
        });

        // Hold release
        ServerPlayNetworking.registerGlobalReceiver(HOLD_RELEASE, (server, player, handler, buf, responseSender) -> {
            final String slot = buf.readString(16);
            final int held = buf.readVarInt();
            final boolean canceled = buf.readBoolean();
            server.execute(() -> PowerAPI.holdRelease(player, slot, held, canceled));
        });
    }

    /* ===================== SERVER HANDLER (PRESS) ===================== */

    private static void handleAbilityKey(ServerPlayerEntity player, String slot) {
        switch (slot) {
            case "primary"   -> PowerAPI.activate(player);
            case "secondary" -> PowerAPI.activateSecondary(player);
            case "third"     -> PowerAPI.activateThird(player);
            case "fourth"    -> player.sendMessage(Text.literal("Fourth ability not implemented yet."), false);
            case "overview"  -> player.sendMessage(Text.literal("Ability overview screen (WIP)."), false);
        }
    }
}
