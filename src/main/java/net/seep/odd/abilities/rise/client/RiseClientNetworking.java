package net.seep.odd.abilities.rise.client;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.power.RisePower;

import org.joml.Vector3f;

public final class RiseClientNetworking {
    private RiseClientNetworking() {}

    private static boolean inited = false;

    private static final Int2ObjectOpenHashMap<Vec3d> SOUL_POS = new Int2ObjectOpenHashMap<>();
    private static final Int2LongOpenHashMap SOUL_EXPIRES = new Int2LongOpenHashMap();

    public static void registerClient() {
        if (inited) return;
        inited = true;

        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_SOUL_ADD_S2C, (client, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int duration = buf.readVarInt();

            client.execute(() -> {
                if (client.world == null) return;
                SOUL_POS.put(id, new Vec3d(x, y, z));
                SOUL_EXPIRES.put(id, client.world.getTime() + duration);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_SOUL_REMOVE_S2C, (client, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            client.execute(() -> {
                SOUL_POS.remove(id);
                SOUL_EXPIRES.remove(id);
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(RiseClientNetworking::tickParticles);
    }

    private static void tickParticles(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (SOUL_POS.isEmpty()) return;

        long now = client.world.getTime();

        // cleanup expired locally too (server also removes, this keeps it snappy)
        var it = SOUL_POS.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int id = e.getIntKey();
            long exp = SOUL_EXPIRES.get(id);
            if (exp != 0 && exp <= now) {
                it.remove();
                SOUL_EXPIRES.remove(id);
            }
        }

        if (SOUL_POS.isEmpty()) return;

        DustParticleEffect green = new DustParticleEffect(new Vector3f(0.12F, 0.80F, 0.12F), 1.2F);

        for (int id : SOUL_POS.keySet()) {
            Vec3d c = SOUL_POS.get(id);

            // soft "sphere" swirl
            for (int i = 0; i < 3; i++) {
                double a = client.world.random.nextDouble() * Math.PI * 2.0;
                double b = client.world.random.nextDouble() * Math.PI;
                double r = 0.55 + client.world.random.nextDouble() * 0.15;

                double px = c.x + Math.cos(a) * Math.sin(b) * r;
                double py = c.y + Math.cos(b) * r;
                double pz = c.z + Math.sin(a) * Math.sin(b) * r;

                client.world.addParticle(green, px, py, pz, 0.0, 0.01, 0.0);
            }
        }
    }
}
