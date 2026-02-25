// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/AmplifiedJudgementNet.java
package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class AmplifiedJudgementNet {
    private AmplifiedJudgementNet() {}

    public static final Identifier S2C = new Identifier("odd", "amplified_judgement_s2c");

    // mode: 0=charge, 1=beam
    public static void send(ServerWorld sw, long id, Vec3d center, int mode, int durationTicks) {
        double maxDistSq = 128.0 * 128.0;

        for (ServerPlayerEntity sp : sw.getPlayers()) {
            if (sp.squaredDistanceTo(center) > maxDistSq) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeLong(id);
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            buf.writeInt(mode);
            buf.writeInt(durationTicks);

            ServerPlayNetworking.send(sp, S2C, buf);
        }
    }
}