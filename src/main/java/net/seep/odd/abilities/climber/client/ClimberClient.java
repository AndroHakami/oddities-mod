package net.seep.odd.abilities.climber.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.seep.odd.abilities.power.ClimberPower;

@Environment(EnvType.CLIENT)
public final class ClimberClient {
    private ClimberClient() {}

    /** Call once from your client initializer. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClimberClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.options == null) return;
        if (client.getNetworkHandler() == null) return;

        byte flags = 0;

        if (client.options.forwardKey.isPressed()) flags |= ClimberPower.IN_FORWARD;
        if (client.options.backKey.isPressed())    flags |= ClimberPower.IN_BACK;
        if (client.options.leftKey.isPressed())    flags |= ClimberPower.IN_LEFT;
        if (client.options.rightKey.isPressed())   flags |= ClimberPower.IN_RIGHT;
        if (client.options.jumpKey.isPressed())    flags |= ClimberPower.IN_JUMP;
        if (client.options.sneakKey.isPressed())   flags |= ClimberPower.IN_SNEAK;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(flags);
        ClientPlayNetworking.send(ClimberPower.CLIMBER_CTRL_C2S, buf);
    }
}
