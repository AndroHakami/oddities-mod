// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/AutoColdNet.java
package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class AutoColdNet {
    private AutoColdNet() {}

    public static final Identifier AUTO_COLD_S2C = new Identifier("odd", "auto_cold_s2c");

    public static void sendStart(ServerWorld sw, int entityId, int durationTicks) {
        send(sw, entityId, true, durationTicks);
    }

    public static void sendStop(ServerWorld sw, int entityId) {
        send(sw, entityId, false, 0);
    }

    private static void send(ServerWorld sw, int entityId, boolean active, int durationTicks) {
        double maxDistSq = 128.0 * 128.0;

        for (ServerPlayerEntity sp : sw.getPlayers()) {
            // send if they’re near the target entity (approx: near the sender player)
            if (sp.squaredDistanceTo(sw.getEntityById(entityId)) > maxDistSq) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(entityId);
            buf.writeBoolean(active);
            buf.writeVarInt(durationTicks);

            ServerPlayNetworking.send(sp, AUTO_COLD_S2C, buf);
        }
    }
}
