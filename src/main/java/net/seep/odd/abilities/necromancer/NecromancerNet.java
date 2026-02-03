// src/main/java/net/seep/odd/abilities/necromancer/NecromancerNet.java
package net.seep.odd.abilities.necromancer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.NecromancerPower;

public final class NecromancerNet {
    private NecromancerNet() {}

    // Client -> Server
    public static final Identifier C2S_NECRO_SUMMON  = new Identifier(Oddities.MOD_ID, "c2s_necro_summon");
    public static final Identifier C2S_NECRO_FEAR    = new Identifier(Oddities.MOD_ID, "c2s_necro_fear");
    public static final Identifier C2S_NECRO_CASTING = new Identifier(Oddities.MOD_ID, "c2s_necro_casting"); // keepalive / cancel

    // Server -> Client
    public static final Identifier S2C_NECRO_MODE = new Identifier(Oddities.MOD_ID, "s2c_necro_mode");

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_NECRO_SUMMON, (server, player, handler, buf, responseSender) -> {
            boolean skeleton = buf.readBoolean();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> NecromancerPower.onClientSummonRequest(player, skeleton, pos));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_NECRO_FEAR, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> NecromancerPower.onClientFearRequest(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_NECRO_CASTING, (server, player, handler, buf, responseSender) -> {
            boolean casting = buf.readBoolean();
            server.execute(() -> NecromancerPower.onClientSetCasting(player, casting));
        });
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_NECRO_MODE, (client, handler, buf, responseSender) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> net.seep.odd.abilities.necromancer.client.NecromancerClient.setSummonMode(on));
        });
    }

    @Environment(EnvType.CLIENT)
    public static void sendSummon(boolean skeleton, BlockPos pos) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(skeleton);
        out.writeBlockPos(pos);
        ClientPlayNetworking.send(C2S_NECRO_SUMMON, out);
    }

    @Environment(EnvType.CLIENT)
    public static void sendFearRay() {
        PacketByteBuf out = PacketByteBufs.create();
        ClientPlayNetworking.send(C2S_NECRO_FEAR, out);
    }

    @Environment(EnvType.CLIENT)
    public static void sendCasting(boolean casting) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(casting);
        ClientPlayNetworking.send(C2S_NECRO_CASTING, out);
    }
}
