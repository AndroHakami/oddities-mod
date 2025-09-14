package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public final class ArtificerNet {
    private ArtificerNet() {}

    public static final Identifier C2S_START = new Identifier("odd","artificer/start");
    public static final Identifier C2S_STOP  = new Identifier("odd","artificer/stop");
    public static final Identifier C2S_PULSE = new Identifier("odd","artificer/pulse");

    public static void c2sStartVacuum(Hand hand) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(hand);
        ClientPlayNetworking.send(C2S_START, buf);
    }
    public static void c2sStopVacuum() {
        ClientPlayNetworking.send(C2S_STOP, PacketByteBufs.empty());
    }
    public static void c2sPulse() {
        ClientPlayNetworking.send(C2S_PULSE, PacketByteBufs.empty());
    }
}
