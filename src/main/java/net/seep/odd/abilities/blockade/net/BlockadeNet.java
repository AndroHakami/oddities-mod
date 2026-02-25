// src/main/java/net/seep/odd/abilities/blockade/net/BlockadeNet.java
package net.seep.odd.abilities.blockade.net;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.blockade.client.BlockadeFx;

public final class BlockadeNet {
    private BlockadeNet() {}

    public static final Identifier S2C_ACTIVE =
            new Identifier(Oddities.MOD_ID, "blockade/active"); // assets namespace irrelevant; this is networking

    /** Server -> client: tell player whether Blockade is active. */
    public static void sendActive(ServerPlayerEntity sp, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        ServerPlayNetworking.send(sp, S2C_ACTIVE, buf);
    }

    /** Call from client init. */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_ACTIVE, (client, handler, buf, response) -> {
            final boolean active = buf.readBoolean();
            client.execute(() -> BlockadeFx.setActive(active));
        });
    }
}
