// src/main/java/net/seep/odd/abilities/firesword/FireSwordNet.java
package net.seep.odd.abilities.firesword;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.firesword.client.FireSwordCpmBridge;
import net.seep.odd.abilities.firesword.client.FireSwordFx;

public final class FireSwordNet {
    private FireSwordNet() {}

    public static final Identifier S2C_CONJURE_START = new Identifier("odd", "firesword_conjure_start");
    public static final Identifier S2C_CONJURE_STOP  = new Identifier("odd", "firesword_conjure_stop");

    /** Call once from client init. */
    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientPlayNetworking.registerGlobalReceiver(S2C_CONJURE_START, (client, handler, buf, sender) -> {
            client.execute(FireSwordCpmBridge::conjureStart);
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_CONJURE_STOP, (client, handler, buf, sender) -> {
            client.execute(FireSwordCpmBridge::conjureStop);
        });

        FireSwordFx.init();
    }

    public static void sendConjureStart(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        ServerPlayNetworking.send(player, S2C_CONJURE_START, buf);
    }

    public static void sendConjureStop(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        ServerPlayNetworking.send(player, S2C_CONJURE_STOP, buf);
    }
}