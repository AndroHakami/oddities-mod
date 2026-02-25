// src/main/java/net/seep/odd/block/combiner/net/CombinerNet.java
package net.seep.odd.block.combiner.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.block.combiner.CombinerBlockEntity;

public final class CombinerNet {
    private CombinerNet() {}

    public static final Identifier C2S_START = new Identifier("odd", "combiner_start");
    public static final Identifier C2S_HIT   = new Identifier("odd", "combiner_hit");

    public static final Identifier S2C_ANIM  = new Identifier("odd", "combiner_anim");

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_START, (server, player, handler, buf, rs) -> {
            final BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player == null || !player.isAlive()) return;
                var be = player.getWorld().getBlockEntity(pos);
                if (be instanceof CombinerBlockEntity comb) {
                    comb.startQte();
                }
            });
        });

        // ✅ client sends the tick THEY saw when they pressed SPACE
        ServerPlayNetworking.registerGlobalReceiver(C2S_HIT, (server, player, handler, buf, rs) -> {
            final BlockPos pos = buf.readBlockPos();
            final int clientTick = buf.readVarInt();

            server.execute(() -> {
                if (player == null || !player.isAlive()) return;

                var be = player.getWorld().getBlockEntity(pos);
                if (be instanceof CombinerBlockEntity comb) {
                    comb.hitFromNet(player, clientTick); // ✅ player already ServerPlayerEntity
                }
            });
        });
    }

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANIM, (client, handler, buf, rs) -> {
            BlockPos pos = buf.readBlockPos();
            String anim = buf.readString(32);
            client.execute(() -> {
                var w = MinecraftClient.getInstance().world;
                if (w == null) return;
                if (w.getBlockEntity(pos) instanceof CombinerBlockEntity be) {
                    be.triggerAnim("main", anim);
                }
            });
        });
    }

    public static void c2sStart(BlockPos pos) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBlockPos(pos);
        ClientPlayNetworking.send(C2S_START, b);
    }

    // ✅ send tick seen client-side
    public static void c2sHit(BlockPos pos, int clientTick) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBlockPos(pos);
        b.writeVarInt(clientTick);
        ClientPlayNetworking.send(C2S_HIT, b);
    }

    public static void s2cAnim(ServerWorld sw, BlockPos pos, String anim) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBlockPos(pos);
        b.writeString(anim);

        for (var p : PlayerLookup.tracking(sw, pos)) {
            ServerPlayNetworking.send(p, S2C_ANIM, b);
        }
    }
}