// src/main/java/net/seep/odd/block/cosmic_katana/CosmicKatanaBlockNet.java
package net.seep.odd.block.cosmic_katana;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class CosmicKatanaBlockNet {
    private CosmicKatanaBlockNet() {}

    public static final Identifier S2C_UNLEASH = new Identifier("odd", "cosmic_katana_block_unleash");

    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientPlayNetworking.registerGlobalReceiver(S2C_UNLEASH, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                if (client.world == null) return;
                var be = client.world.getBlockEntity(pos);
                if (be instanceof CosmicKatanaBlockEntity ck) {
                    ck.triggerAnim("main", "unleash");
                }
            });
        });
    }

    public static void broadcastUnleash(ServerWorld world, BlockPos pos) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double r2 = 64.0 * 64.0;

        for (ServerPlayerEntity p : world.getPlayers(pl -> pl.squaredDistanceTo(cx, cy, cz) <= r2)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            ServerPlayNetworking.send(p, S2C_UNLEASH, buf);
        }
    }
}