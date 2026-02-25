// src/main/java/net/seep/odd/abilities/cosmic/CosmicFxNet.java
package net.seep.odd.abilities.cosmic;

import ladysnake.satin.api.util.GlMatrices;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.cosmic.client.CosmicChargeFx;
import net.seep.odd.abilities.cosmic.client.CosmicDashTrailFx;

public final class CosmicFxNet {
    private CosmicFxNet() {}

    public static final Identifier S2C_DASH_TRAIL  = new Identifier("odd", "cosmic_dash_trail_fx");
    public static final Identifier S2C_CHARGE_OVER = new Identifier("odd", "cosmic_charge_overlay_fx");

    /** Call from client init. */
    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientPlayNetworking.registerGlobalReceiver(S2C_DASH_TRAIL, (client, handler, buf, responseSender) -> {
            long id = buf.readLong();
            double sx = buf.readDouble();
            double sy = buf.readDouble();
            double sz = buf.readDouble();
            double ex = buf.readDouble();
            double ey = buf.readDouble();
            double ez = buf.readDouble();
            float radius = buf.readFloat();
            int durationTicks = buf.readVarInt();

            client.execute(() ->
                    CosmicDashTrailFx.spawn(id, sx, sy, sz, ex, ey, ez, radius, durationTicks)
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_CHARGE_OVER, (client, handler, buf, responseSender) -> {
            boolean charging = buf.readBoolean();
            client.execute(() -> {
                if (charging) CosmicChargeFx.beginCharge();
                else CosmicChargeFx.endCharge();
            });
        });
    }

    /** Player-only overlay toggle (charge vignette/zoom). */
    public static void sendChargeOverlay(ServerPlayerEntity player, boolean charging) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(charging);
        ServerPlayNetworking.send(player, S2C_CHARGE_OVER, buf);
    }

    /** Broadcast the dash trail to nearby players (and the user). */
    public static void broadcastDashTrail(ServerPlayerEntity source, Vec3d start, Vec3d end, float radius, int durationTicks) {
        if (source == null || source.getServer() == null) return;
        ServerWorld world = source.getServerWorld();
        if (world == null) return;

        Vec3d mid = start.add(end).multiply(0.5);
        double range = 96.0;
        double rangeSq = range * range;

        long id = (world.getTime() << 20) ^ source.getUuid().getLeastSignificantBits();

        for (ServerPlayerEntity p : world.getPlayers(sp -> sp.squaredDistanceTo(mid) <= rangeSq)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeLong(id);
            buf.writeDouble(start.x); buf.writeDouble(start.y); buf.writeDouble(start.z);
            buf.writeDouble(end.x);   buf.writeDouble(end.y);   buf.writeDouble(end.z);
            buf.writeFloat(radius);
            buf.writeVarInt(durationTicks);

            ServerPlayNetworking.send(p, S2C_DASH_TRAIL, buf);
        }
    }
}