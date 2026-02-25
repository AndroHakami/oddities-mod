// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/SnowgraveNet.java
package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class SnowgraveNet {
    private SnowgraveNet() {}

    public static final Identifier SNOWGRAVE_ZONE_S2C = new Identifier("odd", "snowgrave_zone_s2c");

    public static void sendZone(ServerWorld sw, long id, Vec3d center, float radius, int durationTicks) {
        double maxDistSq = 128.0 * 128.0;

        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.squaredDistanceTo(center) > maxDistSq) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeLong(id);
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            buf.writeFloat(radius);
            buf.writeInt(durationTicks);

            ServerPlayNetworking.send(sp, SNOWGRAVE_ZONE_S2C, buf);
        }
    }
}
