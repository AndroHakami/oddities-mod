// src/main/java/net/seep/odd/abilities/cultist/CultistNet.java
package net.seep.odd.abilities.cultist;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.cultist.client.CultistCpmBridge;
import net.seep.odd.abilities.cultist.client.CultistFx;

public final class CultistNet {
    private CultistNet() {}

    public static final Identifier S2C_TOUCH_START = new Identifier("odd", "cultist_touch_start");
    public static final Identifier S2C_TOUCH_STOP  = new Identifier("odd", "cultist_touch_stop");

    /** Call once from client init. */
    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientPlayNetworking.registerGlobalReceiver(S2C_TOUCH_START, (client, handler, buf, sender) -> {
            client.execute(CultistCpmBridge::touchStart);
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_TOUCH_STOP, (client, handler, buf, sender) -> {
            client.execute(CultistCpmBridge::touchStop);
        });
        CultistFx.init();
    }

    public static void sendTouchStart(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        ServerPlayNetworking.send(player, S2C_TOUCH_START, buf);
    }

    public static void sendTouchStop(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        ServerPlayNetworking.send(player, S2C_TOUCH_STOP, buf);
    }
}