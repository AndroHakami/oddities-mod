package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.net.PowerNetworking;

import java.util.function.Consumer;

public final class ClientPowerNetworking {
    private ClientPowerNetworking() {}

    public static void registerReceiver(java.util.function.Consumer<String> clientSetter, Identifier channel) {
        ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, responseSender) -> {
            String id = buf.readString();
            client.execute(() -> clientSetter.accept(id));
        });
    }
    public static void registerPowerSyncReceiver(Consumer<String> clientSetter) {
        ClientPlayNetworking.registerGlobalReceiver(PowerNetworking.SYNC, (client, handler, buf, rs) -> {
            String id = buf.readString();
            client.execute(() -> clientSetter.accept(id));
        });
    }

    public static void registerCooldownReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(PowerNetworking.COOLDOWN, (client, handler, buf, rs) -> {
            String slot = buf.readString();
            int ticks = buf.readVarInt();
            client.execute(() -> ClientCooldowns.set(slot, ticks));
        });
    }
}