package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class PowerNetworking {
    public static final Identifier SYNC = new Identifier("oddities", "power_sync");
    public static final Identifier COOLDOWN = new Identifier("oddities", "cooldown_sync");

    private PowerNetworking() {}

    public static void syncTo(ServerPlayerEntity player, String id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(id == null ? "" : id);
        ServerPlayNetworking.send(player, SYNC, buf);
    }

    public static void sendCooldown(ServerPlayerEntity player, String slot, long ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(slot);
        buf.writeVarInt((int) Math.max(0, ticks));
        ServerPlayNetworking.send(player, COOLDOWN, buf);
    }
}