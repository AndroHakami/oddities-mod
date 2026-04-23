
package net.seep.odd.item.outerblaster;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class OuterBlasterFxNet {
    public static final Identifier IMPACT_S2C = new Identifier("odd", "outer_blaster_impact");

    private OuterBlasterFxNet() {}

    public static void sendImpact(ServerWorld world, Vec3d center, float radius, int durationTicks) {
        long id = world.random.nextLong();

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            if (player.squaredDistanceTo(center.x, center.y, center.z) > 160.0 * 160.0) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeLong(id);
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            buf.writeFloat(radius);
            buf.writeInt(durationTicks);

            ServerPlayNetworking.send(player, IMPACT_S2C, buf);
        }
    }
}
