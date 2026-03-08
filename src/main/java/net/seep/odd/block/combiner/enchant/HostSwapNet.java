// src/main/java/net/seep/odd/block/combiner/enchant/HostSwapNet.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class HostSwapNet {
    private HostSwapNet(){}

    public static final Identifier C2S_SWAP = new Identifier(Oddities.MOD_ID, "host_swap");

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_SWAP, (server, player, handler, buf, rs) -> {
            server.execute(() -> HostSwapHandler.trySwap(player));
        });
    }

    public static void c2sSwap() {
        PacketByteBuf b = PacketByteBufs.create();
        ClientPlayNetworking.send(C2S_SWAP, b);
    }
}