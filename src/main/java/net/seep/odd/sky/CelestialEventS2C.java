package net.seep.odd.sky;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class CelestialEventS2C {
    public static final Identifier ALIEN = new Identifier("odd", "celestial_alien");
    public static final Identifier CLEAR = new Identifier("odd", "celestial_clear");

    private CelestialEventS2C() {}

    /** Server → all players in this world. */
    public static void sendAlien(ServerWorld sw,
                                 Identifier sun, Identifier moon,
                                 float hueDeg, float sat, float val, float nightLift,
                                 float sunScale, float moonScale,
                                 boolean hideClouds,
                                 int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(sun);
        buf.writeIdentifier(moon);
        buf.writeFloat(hueDeg);
        buf.writeFloat(sat);
        buf.writeFloat(val);
        buf.writeFloat(nightLift);
        buf.writeFloat(sunScale);
        buf.writeFloat(moonScale);
        buf.writeBoolean(hideClouds);
        buf.writeVarInt(durationTicks);
        for (ServerPlayerEntity p : sw.getPlayers())
            ServerPlayNetworking.send(p, ALIEN, buf);
    }

    public static void sendClear(ServerWorld sw) {
        PacketByteBuf buf = PacketByteBufs.create();
        for (ServerPlayerEntity p : sw.getPlayers())
            ServerPlayNetworking.send(p, CLEAR, buf);
    }

    /** Client receivers – call once from your client initializer. */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ALIEN, (client, handler, buf, sender) -> {
            Identifier sun  = buf.readIdentifier();
            Identifier moon = buf.readIdentifier();
            float hueDeg   = buf.readFloat();
            float sat      = buf.readFloat();
            float val      = buf.readFloat();
            float night    = buf.readFloat();
            float sunScale = buf.readFloat();
            float moonScale= buf.readFloat();
            boolean hide   = buf.readBoolean();
            int dur        = buf.readVarInt();
            client.execute(() ->
                    CelestialEventClient.apply(sun, moon, hueDeg, sat, val, night, sunScale, moonScale, hide, dur)
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(CLEAR, (client, handler, buf, sender) ->
                client.execute(CelestialEventClient::clear)
        );
    }
}
