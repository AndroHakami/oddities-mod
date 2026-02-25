package net.seep.odd.abilities.artificer.mixer.brew;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class DismantleNet {
    private DismantleNet() {}

    public static final Identifier DISMANTLE_S2C = new Identifier("odd", "dismantle_s2c");

    public static void sendSpawn(ServerWorld sw, long id, Vec3d center, float halfSize, int durationTicks) {
        double maxDistSq = 128.0 * 128.0;

        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.squaredDistanceTo(center) > maxDistSq) continue;

            PacketByteBuf buf = PacketByteBufs.create(); // ✅ correct (avoids that cast crash)
            buf.writeLong(id);
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            buf.writeFloat(halfSize);
            buf.writeInt(durationTicks);

            ServerPlayNetworking.send(sp, DISMANTLE_S2C, buf);
        }
    }
}
