package net.seep.odd.abilities.sun;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.power.SunPower;

public final class SunNet {
    private SunNet() {}

    public static final Identifier S2C_HUD = new Identifier("odd", "sun_hud_state");
    public static final Identifier S2C_HOLD_POSE = new Identifier("odd", "sun_hold_pose");

    public static void registerServer() {
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld w) -> {
            for (ServerPlayerEntity sp : w.getPlayers()) {
                SunPower.serverTick(sp);
            }
        });
    }

    public static void sendHudState(ServerPlayerEntity to, float energy, boolean transformed, boolean secondaryCharging) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(energy);
        buf.writeBoolean(transformed);
        buf.writeBoolean(secondaryCharging);
        ServerPlayNetworking.send(to, S2C_HUD, buf);
    }

    public static void sendHoldPose(ServerPlayerEntity to, boolean on) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        ServerPlayNetworking.send(to, S2C_HOLD_POSE, buf);
    }

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private Client() {}

        public static void register() {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(S2C_HUD, (client, handler, buf, sender) -> {
                float energy = buf.readFloat();
                boolean transformed = buf.readBoolean();
                boolean secondaryCharging = buf.readBoolean();
                client.execute(() -> SunPower.Client.setHud(energy, transformed, secondaryCharging));
            });

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(S2C_HOLD_POSE, (client, handler, buf, sender) -> {
                boolean on = buf.readBoolean();
                client.execute(() -> {
                    if (on) net.seep.odd.abilities.sun.client.SunCpmBridge.holdStart();
                    else net.seep.odd.abilities.sun.client.SunCpmBridge.holdStop();
                });
            });
        }
    }
}
