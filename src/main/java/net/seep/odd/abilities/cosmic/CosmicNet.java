// src/main/java/net/seep/odd/abilities/cosmic/CosmicNet.java
package net.seep.odd.abilities.cosmic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.power.CosmicPower;

public final class CosmicNet {
    private CosmicNet() {}

    // Shared channel IDs (client reads them too)
    public static final Identifier S2C_SLASH_PING = new Identifier("odd", "cosmic_slash_ping");
    public static final Identifier S2C_RIFT       = new Identifier("odd", "cosmic_rift_line");
    public static final Identifier S2C_CPM_STANCE = new Identifier("odd", "cosmic_cpm_stance");
    public static final Identifier S2C_CPM_SLASH  = new Identifier("odd", "cosmic_cpm_slash");

    /* --------------------------- SERVER / COMMON --------------------------- */

    /** Call from Oddities.onInitialize() (server/common). */
    public static void registerServer() {
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld w) -> {
            for (ServerPlayerEntity sp : w.getPlayers()) {
                CosmicPower.serverTick(sp);
            }
        });
    }

    /** Server -> client helpers (safe on dedicated server). */
    public static void sendCpmStance(ServerPlayerEntity to, boolean on) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        ServerPlayNetworking.send(to, S2C_CPM_STANCE, buf);
    }

    public static void sendCpmSlash(ServerPlayerEntity to, int holdTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(Math.max(1, holdTicks));
        ServerPlayNetworking.send(to, S2C_CPM_SLASH, buf);
    }

    public static void sendSlashPing(ServerPlayerEntity to) {
        ServerPlayNetworking.send(to, S2C_SLASH_PING, PacketByteBufs.create());
    }

    public static void sendRift(ServerWorld world, Vec3d a, Vec3d b, int unused) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(a.x); buf.writeDouble(a.y); buf.writeDouble(a.z);
        buf.writeDouble(b.x); buf.writeDouble(b.y); buf.writeDouble(b.z);
        for (ServerPlayerEntity sp : world.getPlayers()) {
            ServerPlayNetworking.send(sp, S2C_RIFT, buf);
        }
    }

    /* --------------------------- CLIENT-ONLY --------------------------- */

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private Client() {}

        /** Call from OdditiesClient.onInitializeClient() only. */
        public static void register() {
            // Using fully-qualified names here avoids server-side linking of client classes.
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_CPM_STANCE, (client, handler, buf, sender) -> {
                        boolean on = buf.readBoolean();
                        client.execute(() -> {
                            if (on) net.seep.odd.abilities.cosmic.client.CosmicCpmBridge.stanceStart();
                            else    net.seep.odd.abilities.cosmic.client.CosmicCpmBridge.stanceStop();
                        });
                    });

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_CPM_SLASH, (client, handler, buf, sender) -> {
                        int holdTicks = buf.readVarInt();
                        client.execute(() -> net.seep.odd.abilities.cosmic.client.CosmicCpmBridge.slash(holdTicks));
                    });

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_SLASH_PING, (c,h,b,s) -> c.execute(net.seep.odd.abilities.power.CosmicPower.Client::pingSlash)
            );

            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    S2C_RIFT, (client, h, buf, s) -> {
                        double ax = buf.readDouble(), ay = buf.readDouble(), az = buf.readDouble();
                        double bx = buf.readDouble(), by = buf.readDouble(), bz = buf.readDouble();
                        client.execute(() -> spawnClientRift(
                                new net.minecraft.util.math.Vec3d(ax, ay, az),
                                new net.minecraft.util.math.Vec3d(bx, by, bz)
                        ));
                    });
        }

        private static void spawnClientRift(net.minecraft.util.math.Vec3d a,
                                            net.minecraft.util.math.Vec3d b) {
            net.minecraft.client.world.ClientWorld w = net.minecraft.client.MinecraftClient.getInstance().world;
            if (w == null) return;
            int steps = Math.max(16, (int) a.distanceTo(b) * 4);
            net.minecraft.util.math.Vec3d d = b.subtract(a);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                net.minecraft.util.math.Vec3d p = a.add(d.multiply(t));
                w.addParticle(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.05, p.z, 0, 0, 0);
            }
        }
    }
}
