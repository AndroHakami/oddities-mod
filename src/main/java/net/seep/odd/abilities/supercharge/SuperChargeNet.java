package net.seep.odd.abilities.supercharge;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class SuperChargeNet {
    private SuperChargeNet() {}
    public static final Identifier HUD = new Identifier(Oddities.MOD_ID, "supercharge_hud");

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(HUD, (client, handler, buf, response) -> {
            boolean show = buf.readBoolean();
            int cur = buf.readVarInt();
            int max = buf.readVarInt();
            client.execute(() -> SuperHud.onHud(show, cur, max));
        });
    }

    public static void sendHud(ServerPlayerEntity p, boolean show, int cur, int max) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBoolean(show);
        buf.writeVarInt(cur);
        buf.writeVarInt(max);
        ServerPlayNetworking.send(p, HUD, buf);
    }
}
