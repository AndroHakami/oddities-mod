package net.seep.odd.abilities.fallingsnow;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class FallingSnowNet {
    private FallingSnowNet(){}

    public static final Identifier S2C_PING_CHARGES =
            new Identifier(Oddities.MOD_ID, "fallingsnow/ping_charges");

    public static void s2cPingCharges(ServerPlayerEntity sp) {
        PacketByteBuf out = PacketByteBufs.create();
        ServerPlayNetworking.send(sp, S2C_PING_CHARGES, out);
    }
}
