package net.seep.odd.abilities.shift;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.seep.odd.abilities.shift.client.ShiftScreenFx;

public final class ShiftFxNet {
    private ShiftFxNet() {}

    public static final Identifier S2C_SHIFT_IMBUE = new Identifier("odd", "shift_imbue_fx");
    public static final Identifier S2C_SHIFT_TAG   = new Identifier("odd", "shift_tag_fx");
    public static final Identifier S2C_SHIFT_PULSE = new Identifier("odd", "shift_pulse_fx");

    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientPlayNetworking.registerGlobalReceiver(S2C_SHIFT_IMBUE, (client, handler, buf, sender) -> {
            boolean enabled = buf.readBoolean();
            client.execute(() -> ShiftScreenFx.setImbued(enabled));
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_SHIFT_TAG, (client, handler, buf, sender) -> {
            boolean enabled = buf.readBoolean();
            client.execute(() -> ShiftScreenFx.setTagged(enabled));
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_SHIFT_PULSE, (client, handler, buf, sender) -> {
            float strength = buf.readFloat();
            client.execute(() -> ShiftScreenFx.pulse(strength));
        });
    }

    public static void sendImbue(ServerPlayerEntity player, boolean enabled) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(enabled);
        ServerPlayNetworking.send(player, S2C_SHIFT_IMBUE, buf);
    }

    public static void sendTagged(ServerPlayerEntity player, boolean enabled) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(enabled);
        ServerPlayNetworking.send(player, S2C_SHIFT_TAG, buf);
    }

    public static void sendPulse(ServerPlayerEntity player, float strength) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(strength);
        ServerPlayNetworking.send(player, S2C_SHIFT_PULSE, buf);
    }
}
