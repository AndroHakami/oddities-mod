package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.PowerAPI;

public class AbilityKeyPacket {
    public static final Identifier ID = new Identifier("oddities", "ability_key");

    // Send from client to server
    public static PacketByteBuf makeBuf(String abilitySlot) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(abilitySlot);
        return buf;
    }

    // Server handler
    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            String slot = buf.readString();
            server.execute(() -> handleAbilityKey(player, slot));
        });
    }

    private static void handleAbilityKey(ServerPlayerEntity player, String slot) {
        switch (slot) {
            case "primary"   -> PowerAPI.activate(player);
            case "secondary" -> PowerAPI.activateSecondary(player);
            case "third"     -> player.sendMessage(Text.literal("Third ability not implemented yet."), false);
            case "fourth"    -> player.sendMessage(Text.literal("Fourth ability not implemented yet."), false);
            case "overview"  -> player.sendMessage(Text.literal("Ability overview screen (WIP)."), false);
        }
        }
    }