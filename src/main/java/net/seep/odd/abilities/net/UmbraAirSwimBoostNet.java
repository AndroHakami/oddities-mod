// src/main/java/net/seep/odd/abilities/net/UmbraAirSwimBoostNet.java
package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.astral.OddUmbraBoostInput;

public final class UmbraAirSwimBoostNet {
    private UmbraAirSwimBoostNet() {}

    public static final Identifier UMBRA_SWIM_BOOST =
            new Identifier("odd", "umbra_swim_boost");

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(UMBRA_SWIM_BOOST,
                (server, player, handler, buf, responseSender) -> {
                    final boolean held = buf.readBoolean();
                    server.execute(() -> {
                        if (player instanceof OddUmbraBoostInput bi) {
                            bi.oddities$setUmbraBoostHeld(held, player.age);
                        }
                    });
                });
    }

    public static PacketByteBuf makeBuf(boolean held) {
        var b = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        b.writeBoolean(held);
        return b;
    }
}
