package net.seep.odd.block.grandanvil.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.block.grandanvil.GrandAnvilBlockEntity;

public final class GrandAnvilNet {
    private GrandAnvilNet() {}

    public static final Identifier START = new Identifier("odd", "grand_anvil_start");
    public static final Identifier HIT   = new Identifier("odd", "grand_anvil_hit");

    /** Call once during mod init (server/common). */
    public static void registerServer() {
        // Start QTE
        ServerPlayNetworking.registerGlobalReceiver(START, (server, player, handler, buf, rs) -> {
            final BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player == null || !player.isAlive()) return; // player is already ServerPlayerEntity here
                Object be = player.getWorld().getBlockEntity(pos);
                if (be instanceof GrandAnvilBlockEntity) {
                    GrandAnvilBlockEntity anvil = (GrandAnvilBlockEntity) be;
                    anvil.startQte();
                }
            });
        });

        // Hit (SPACE)
        ServerPlayNetworking.registerGlobalReceiver(HIT, (server, player, handler, buf, rs) -> {
            final BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player == null || !player.isAlive()) return;
                Object be = player.getWorld().getBlockEntity(pos);
                if (be instanceof GrandAnvilBlockEntity) {
                    GrandAnvilBlockEntity anvil = (GrandAnvilBlockEntity) be;
                    anvil.hit();
                }
            });
        });
    }

    // -------- client -> server senders --------

    public static void c2sStart(BlockPos pos) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBlockPos(pos);
        ClientPlayNetworking.send(START, b);
    }

    public static void c2sHit(BlockPos pos) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBlockPos(pos);
        ClientPlayNetworking.send(HIT, b);
    }
}
