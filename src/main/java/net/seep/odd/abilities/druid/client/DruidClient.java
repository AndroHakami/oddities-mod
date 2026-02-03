package net.seep.odd.abilities.druid.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.druid.DruidNet;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class DruidClient {
    private DruidClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(DruidNet.S2C_OPEN_WHEEL, (client, handler, buf, responseSender) -> {
            WheelPayload payload = readWheelPayload(buf);

            client.execute(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return;
                mc.setScreen(new DruidWheelScreen(payload.cooldownTicksRemaining, payload.options));
            });
        });

        // NEW: store current form key client-side (for heart sprite swap)
        ClientPlayNetworking.registerGlobalReceiver(DruidNet.S2C_FORM, (client, handler, buf, responseSender) -> {
            String key = buf.readString(64);
            client.execute(() -> DruidClientState.setFormKey(key));
        });
    }

    private static WheelPayload readWheelPayload(PacketByteBuf buf) {
        long cd = buf.readVarLong();
        int n = buf.readVarInt();

        List<DruidWheelScreen.Option> opts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String key = buf.readString(64);
            String name = buf.readString(128);
            Identifier icon = new Identifier(buf.readString(256));
            opts.add(new DruidWheelScreen.Option(key, name, icon));
        }
        return new WheelPayload(cd, opts);
    }

    private record WheelPayload(long cooldownTicksRemaining, List<DruidWheelScreen.Option> options) {}
}
