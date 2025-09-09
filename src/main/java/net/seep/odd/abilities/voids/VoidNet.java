package net.seep.odd.abilities.voids;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;

public final class VoidNet {
    private VoidNet(){}

    public static final Identifier C2S_USE = new Identifier("odd","void/use");

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_USE, (s, p, h, b, rs) ->
                s.execute(() -> {
                    if (!"void".equals(net.seep.odd.abilities.PowerAPI.get(p))) return;
                    VoidSystem.onPrimary(p);
                }));
    }

    // call from a client keybind (same pattern as your other powers)
    public static void sendUse() {
        if (!"void".equals(net.seep.odd.abilities.client.ClientPowerHolder.get())) return;
        ClientPlayNetworking.send(C2S_USE, PacketByteBufs.create());
    }
}
