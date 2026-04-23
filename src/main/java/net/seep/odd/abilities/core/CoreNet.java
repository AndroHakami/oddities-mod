package net.seep.odd.abilities.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.power.CorePower;

public final class CoreNet {
    private CoreNet() {}

    public static final Identifier S2C_CPM_PASSIVE = new Identifier("odd", "core_cpm_passive");
    public static final Identifier S2C_CPM_PRIMARY = new Identifier("odd", "core_cpm_primary");
    public static final Identifier S2C_PASSIVE_HUD = new Identifier("odd", "core_passive_hud");
    public static final Identifier S2C_BLAST_FX = new Identifier("odd", "core_blast_fx");

    public static void registerServer() {
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                CorePower.serverTick(player);

                if ((world.getTime() % 5L) == 0L) {
                    sendPassiveHud(player, CorePower.passiveRemainingTicks(player));
                }
            }
        });
    }

    public static void sendPassiveSpin(ServerPlayerEntity to, int holdTicks, float radius) {
        var buf = PacketByteBufs.create();
        buf.writeVarInt(Math.max(1, holdTicks));
        buf.writeFloat(Math.max(0.5F, radius));
        ServerPlayNetworking.send(to, S2C_CPM_PASSIVE, buf);
    }

    public static void sendPrimarySpin(ServerPlayerEntity to, int holdTicks) {
        var buf = PacketByteBufs.create();
        buf.writeVarInt(Math.max(1, holdTicks));
        ServerPlayNetworking.send(to, S2C_CPM_PRIMARY, buf);
    }

    public static void sendPassiveHud(ServerPlayerEntity to, int remainingTicks) {
        var buf = PacketByteBufs.create();
        buf.writeVarInt(Math.max(0, remainingTicks));
        ServerPlayNetworking.send(to, S2C_PASSIVE_HUD, buf);
    }

    public static void sendBlastFx(ServerWorld world, double x, double y, double z, float radius, int durationTicks, boolean huge) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(x, y, z) > 180.0D * 180.0D) continue;

            var buf = PacketByteBufs.create();
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(radius);
            buf.writeVarInt(Math.max(4, durationTicks));
            buf.writeBoolean(huge);
            ServerPlayNetworking.send(player, S2C_BLAST_FX, buf);
        }
    }

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private Client() {}

        public static void register() {
            net.seep.odd.abilities.core.client.CorePassiveHudClient.init();
            net.seep.odd.abilities.core.client.CoreCpmBridge.init();
            net.seep.odd.abilities.core.client.CoreTickingScreenFx.init();
            net.seep.odd.abilities.core.client.CoreRadiusWorldFx.init();
            net.seep.odd.abilities.core.client.CoreBlastWorldFx.init();

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_CPM_PASSIVE, (client, handler, buf, sender) -> {
                        int holdTicks = buf.readVarInt();
                        float radius = buf.readFloat();
                        client.execute(() -> {
                            net.seep.odd.abilities.core.client.CoreCpmBridge.playPassiveSpin(holdTicks);
                            net.seep.odd.abilities.core.client.CoreTickingScreenFx.begin(holdTicks);
                            net.seep.odd.abilities.core.client.CoreRadiusWorldFx.begin(holdTicks, radius);
                        });
                    }
            );

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_CPM_PRIMARY, (client, handler, buf, sender) -> {
                        int holdTicks = buf.readVarInt();
                        client.execute(() -> net.seep.odd.abilities.core.client.CoreCpmBridge.playShortSpin(holdTicks));
                    }
            );

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_PASSIVE_HUD, (client, handler, buf, sender) -> {
                        int remainingTicks = buf.readVarInt();
                        client.execute(() -> net.seep.odd.abilities.core.client.CorePassiveHudClient.setRemainingTicks(remainingTicks));
                    }
            );

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_BLAST_FX, (client, handler, buf, sender) -> {
                        double x = buf.readDouble();
                        double y = buf.readDouble();
                        double z = buf.readDouble();
                        float radius = buf.readFloat();
                        int durationTicks = buf.readVarInt();
                        boolean huge = buf.readBoolean();
                        client.execute(() -> net.seep.odd.abilities.core.client.CoreBlastWorldFx.spawn(x, y, z, radius, durationTicks, huge));
                    }
            );
        }
    }
}
