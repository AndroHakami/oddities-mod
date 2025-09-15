package net.seep.odd.abilities.artificer.condenser;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.abilities.artificer.EssenceType;

public final class CondenserNet {
    private CondenserNet() {}

    public static final Identifier C2S_PRESS = new Identifier("odd", "condenser_press");

    /** Register the C2S handler once on common init. */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(
                C2S_PRESS,
                (MinecraftServer server,
                 ServerPlayerEntity player,
                 net.minecraft.server.network.ServerPlayNetworkHandler handler,
                 PacketByteBuf buf,
                 net.fabricmc.fabric.api.networking.v1.PacketSender responseSender) -> {

                    BlockPos pos = buf.readBlockPos();
                    String key   = buf.readString(32);
                    EssenceType type = EssenceType.byKey(key);

                    server.execute(() -> {
                        if (type == null) return;
                        var be = player.getWorld().getBlockEntity(pos);
                        if (!(be instanceof CondenserBlockEntity condenser)) return;
                        condenser.tryCondense(type, player);
                    });
                }
        );
    }

    /** Client side: build and send the small packet. */
    public static void sendPress(BlockPos pos, EssenceType type) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(pos);
        out.writeString(type.key);
        ClientPlayNetworking.send(C2S_PRESS, out);
    }

    /** Keep if you call it from your client init; it’s a no-op by design. */
    public static void registerClient() { /* no-op */ }
}
