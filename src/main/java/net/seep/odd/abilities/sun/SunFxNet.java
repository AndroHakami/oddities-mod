package net.seep.odd.abilities.sun;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class SunFxNet {
    private SunFxNet() {}

    public static final Identifier S2C_EMPOWERED = new Identifier("odd", "sun_empowered_overlay_fx");
    public static final Identifier S2C_TRANSFORM_RAY = new Identifier("odd", "sun_transform_ray_fx");

    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientPlayNetworking.registerGlobalReceiver(S2C_EMPOWERED, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> {
                if (on) net.seep.odd.abilities.sun.client.SunEmpoweredFx.begin();
                else net.seep.odd.abilities.sun.client.SunEmpoweredFx.end();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_TRANSFORM_RAY, (client, handler, buf, sender) -> {
            long id = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            boolean reverse = buf.readBoolean();
            client.execute(() -> net.seep.odd.abilities.sun.client.SunTransformRayFx.spawn(id, x, y, z, reverse));
        });
    }

    public static void sendEmpoweredOverlay(ServerPlayerEntity player, boolean on) {
        var buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        ServerPlayNetworking.send(player, S2C_EMPOWERED, buf);
    }

    public static void broadcastTransformRay(ServerPlayerEntity source, boolean reverse) {
        if (source == null || source.getServer() == null) return;
        ServerWorld world = source.getServerWorld();
        Vec3d p = source.getPos();
        long id = (world.getTime() << 20) ^ source.getUuid().getLeastSignificantBits();

        for (ServerPlayerEntity sp : world.getPlayers(s -> s.squaredDistanceTo(p) <= (96.0 * 96.0))) {
            var buf = PacketByteBufs.create();
            buf.writeLong(id);
            buf.writeDouble(p.x);
            buf.writeDouble(source.getY() + source.getHeight() * 0.5);
            buf.writeDouble(p.z);
            buf.writeBoolean(reverse);
            ServerPlayNetworking.send(sp, S2C_TRANSFORM_RAY, buf);
        }
    }
}
