package net.seep.odd.sky.day;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

import java.util.HashMap;
import java.util.Map;

public final class BiomeDayProfileNetworking {
    private BiomeDayProfileNetworking() {}

    public static final Identifier SYNC_ID = new Identifier(Oddities.MOD_ID, "biome_day_profiles_sync");

    private static boolean serverInited = false;
    private static boolean clientInited = false;

    public static void initServer() {
        if (serverInited) return;
        serverInited = true;

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.getPlayer(), server));
    }

    public static void initClient() {
        if (clientInited) return;
        clientInited = true;

        ClientPlayNetworking.registerGlobalReceiver(SYNC_ID, (client, handler, buf, responseSender) -> {
            int size = buf.readVarInt();
            Map<Identifier, BiomeDayProfile> map = new HashMap<>();

            for (int i = 0; i < size; i++) {
                Identifier biomeId = buf.readIdentifier();
                int sky = buf.readInt();
                int fog = buf.readInt();
                int horizon = buf.readInt();
                int cloud = buf.readInt();
                map.put(biomeId, new BiomeDayProfile(sky, fog, horizon, cloud));
            }

            client.execute(() -> BiomeDayProfileClientStore.replaceAll(map));
        });
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncTo(player, server);
        }
    }

    public static void syncTo(ServerPlayerEntity player, MinecraftServer server) {
        PacketByteBuf buf = PacketByteBufs.create();

        Map<Identifier, BiomeDayProfile> profiles = BiomeDayProfileState.get(server).getProfiles();
        buf.writeVarInt(profiles.size());

        for (Map.Entry<Identifier, BiomeDayProfile> entry : profiles.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            buf.writeInt(entry.getValue().skyColor());
            buf.writeInt(entry.getValue().fogColor());
            buf.writeInt(entry.getValue().horizonColor());
            buf.writeInt(entry.getValue().cloudColor());
        }

        ServerPlayNetworking.send(player, SYNC_ID, buf);
    }
}