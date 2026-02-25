// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/BlackFlameNet.java
package net.seep.odd.abilities.artificer.mixer.brew;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class BlackFlameNet {
    private BlackFlameNet() {}

    public static final Identifier BLACK_FLAME_S2C = new Identifier("odd", "black_flame_s2c");

    /**
     * Sends a state snapshot.
     * remainingTicks lets clients that arrive late still see it WITHOUT resetting its lifetime.
     */
    public static void sendState(ServerPlayerEntity sp, long id, Vec3d center, float radius, float height,
                                 int remainingTicks, int totalDurationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(id);
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeFloat(radius);
        buf.writeFloat(height);
        buf.writeInt(remainingTicks);
        buf.writeInt(totalDurationTicks);
        ServerPlayNetworking.send(sp, BLACK_FLAME_S2C, buf);
    }

    /** Broadcast to nearby players. */
    public static void broadcast(ServerWorld sw, long id, Vec3d center, float radius, float height,
                                 int remainingTicks, int totalDurationTicks) {
        double maxDistSq = 128.0 * 128.0;
        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.squaredDistanceTo(center) > maxDistSq) continue;
            sendState(sp, id, center, radius, height, remainingTicks, totalDurationTicks);
        }
    }
}
