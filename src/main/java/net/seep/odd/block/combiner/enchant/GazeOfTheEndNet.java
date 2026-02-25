// src/main/java/net/seep/odd/block/combiner/enchant/GazeOfTheEndNet.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GazeOfTheEndNet {
    private GazeOfTheEndNet(){}

    public static final Identifier C2S_REQUEST = new Identifier(Oddities.MOD_ID, "gaze_end_request");
    public static final Identifier S2C_HEALTH  = new Identifier(Oddities.MOD_ID, "gaze_end_health");

    // tiny rate limit per player (server-side)
    private static final Map<UUID, Long> LAST_REQ_TICK = new HashMap<>();

    /** call in common/server init */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST, (server, player, handler, buf, responseSender) -> {
            final int targetId = buf.readVarInt();
            server.execute(() -> handleRequest(player, targetId));
        });
    }

    private static void handleRequest(ServerPlayerEntity player, int targetId) {
        if (player == null || !player.isAlive()) return;
        if (player.getWorld() == null) return;

        // Must actually be wearing the EYE helmet enchant (anti-cheat sanity)
        var helmet = player.getEquippedStack(EquipmentSlot.HEAD);
        if (helmet == null || helmet.isEmpty()) return;
        if (CombinerEnchantments.EYE == null) return;
        if (EnchantmentHelper.getLevel(CombinerEnchantments.EYE, helmet) <= 0) return;

        long now = player.getWorld().getTime();
        long last = LAST_REQ_TICK.getOrDefault(player.getUuid(), -9999L);
        if (now - last < 3) return; // ~ every 3 ticks max
        LAST_REQ_TICK.put(player.getUuid(), now);

        var e = player.getWorld().getEntityById(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;

        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(targetId);
        out.writeFloat(le.getHealth());
        out.writeFloat(le.getMaxHealth());
        ServerPlayNetworking.send(player, S2C_HEALTH, out);
    }

    /** call in client init */
    public static void registerClient(GazeClientSink sink) {
        ClientPlayNetworking.registerGlobalReceiver(S2C_HEALTH, (client, handler, buf, responseSender) -> {
            final int id = buf.readVarInt();
            final float hp = buf.readFloat();
            final float max = buf.readFloat();
            client.execute(() -> sink.onHealth(id, hp, max));
        });
    }

    /** small callback so we don’t hard-couple to client class */
    public interface GazeClientSink {
        void onHealth(int entityId, float hp, float maxHp);
    }
}