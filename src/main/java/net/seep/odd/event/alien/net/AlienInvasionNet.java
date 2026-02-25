package net.seep.odd.event.alien.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import net.seep.odd.event.alien.client.AlienInvasionClientState;
import net.seep.odd.event.alien.client.fx.AlienPillarFx;

public final class AlienInvasionNet {

    public static final Identifier STATE = new Identifier(Oddities.MOD_ID, "alien_invasion_state");
    public static final Identifier PILLAR = new Identifier(Oddities.MOD_ID, "alien_invasion_pillar");

    private AlienInvasionNet() {}

    // ----------- server -> client
    public static void sendState(ServerPlayerEntity p, boolean active, int wave, int maxWaves) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeVarInt(wave);
        buf.writeVarInt(maxWaves);
        ServerPlayNetworking.send(p, STATE, buf);
    }

    public static void sendPillarFxNear(ServerWorld w, Vec3d center, float radius, float height, int durationTicks) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(center) > (128.0 * 128.0)) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeDouble(center.x);
            buf.writeDouble(center.y);
            buf.writeDouble(center.z);
            buf.writeFloat(radius);
            buf.writeFloat(height);
            buf.writeVarInt(durationTicks);

            ServerPlayNetworking.send(p, PILLAR, buf);
        }
    }

    // ----------- client receivers
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(STATE, (client, handler, buf, sender) -> {
            boolean active = buf.readBoolean();
            int wave = buf.readVarInt();
            int max = buf.readVarInt();

            client.execute(() -> AlienInvasionClientState.setState(active, wave, max));
        });

        ClientPlayNetworking.registerGlobalReceiver(PILLAR, (client, handler, buf, sender) -> {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float r = buf.readFloat();
            float h = buf.readFloat();
            int dur = buf.readVarInt();

            client.execute(() -> AlienPillarFx.add(new Vec3d(x, y, z), r, h, dur));
        });
    }
}