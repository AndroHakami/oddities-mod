package net.seep.odd.abilities.rise.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.power.RisePower;

@Environment(EnvType.CLIENT)
public final class RiseSoulParticlesClient {
    private RiseSoulParticlesClient() {}

    private static boolean inited = false;

    private record SoulFx(int id, Vec3d pos, long expiresAt, long castingUntil) {}

    private static final Int2ObjectOpenHashMap<SoulFx> FX = new Int2ObjectOpenHashMap<>();

    // spawn tuning
    private static final double MAX_DIST = 48.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;

    private static final int BASE_RATE_TICKS = 3;   // every 3 ticks per soul
    private static final int CAST_RATE_TICKS = 1;   // every tick while casting

    public static void init() {
        if (inited) return;
        inited = true;

        // Soul add: start particles
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_SOUL_ADD_S2C, (client, handler, buf, responseSender) -> {
            final int id = buf.readVarInt();
            final double x = buf.readDouble();
            final double y = buf.readDouble();
            final double z = buf.readDouble();
            final int lifetime = buf.readVarInt();

            client.execute(() -> {
                if (client.world == null) return;
                long now = client.world.getTime();
                FX.put(id, new SoulFx(id, new Vec3d(x, y, z), now + lifetime, 0));
            });
        });

        // Soul remove: stop particles immediately (spawn consumes it, so it disappears instantly)
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_SOUL_REMOVE_S2C, (client, handler, buf, responseSender) -> {
            final int id = buf.readVarInt();
            client.execute(() -> FX.remove(id));
        });

        // Cast start: intensify particles at this soul
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_CAST_START_S2C, (client, handler, buf, responseSender) -> {
            final int soulId = buf.readVarInt();
            final int delayTicks = buf.readVarInt();
            final int animTicks = buf.readVarInt();

            final double x = buf.readDouble();
            final double y = buf.readDouble();
            final double z = buf.readDouble();
            final int remainingLifetime = buf.readVarInt();

            client.execute(() -> {
                if (client.world == null) return;
                long now = client.world.getTime();
                long expiresAt = now + Math.max(1, remainingLifetime);
                long castingUntil = now + Math.max(delayTicks, animTicks);

                SoulFx old = FX.get(soulId);
                Vec3d pos = (old != null) ? old.pos : new Vec3d(x, y, z);
                FX.put(soulId, new SoulFx(soulId, pos, expiresAt, castingUntil));
            });
        });

        // Cast cancel: go back to normal particle rate
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_CAST_CANCEL_S2C, (client, handler, buf, responseSender) -> {
            final int soulId = buf.readVarInt();
            client.execute(() -> {
                SoulFx old = FX.get(soulId);
                if (old == null || client.world == null) return;
                FX.put(soulId, new SoulFx(old.id, old.pos, old.expiresAt, 0));
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                FX.clear();
                return;
            }

            long now = client.world.getTime();

            // expire
            var it = FX.int2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue().expiresAt <= now) it.remove();
            }

            if (FX.isEmpty()) return;

            // spawn particles
            for (SoulFx s : FX.values()) {
                if (client.player.squaredDistanceTo(s.pos) > MAX_DIST_SQ) continue;

                boolean casting = s.castingUntil > now;
                int rate = casting ? CAST_RATE_TICKS : BASE_RATE_TICKS;
                if ((now + s.id) % rate != 0) continue;

                spawnSoulWisps(client, s.pos, casting, (int)(now + s.id));
            }
        });
    }

    private static void spawnSoulWisps(MinecraftClient client, Vec3d pos, boolean casting, int seed) {
        if (client.world == null) return;

        // Mostly use HAPPY_VILLAGER (already green)
        // Slightly tighter + more upward movement when casting
        double spread = casting ? 0.22 : 0.32;
        double upVel  = casting ? 0.030 : 0.015;

        int count = casting ? 3 : 1;

        for (int i = 0; i < count; i++) {
            double rx = (client.world.random.nextDouble() - 0.5) * spread;
            double ry = (client.world.random.nextDouble()) * 0.35;
            double rz = (client.world.random.nextDouble() - 0.5) * spread;

            double vx = (client.world.random.nextDouble() - 0.5) * 0.01;
            double vy = upVel + client.world.random.nextDouble() * 0.01;
            double vz = (client.world.random.nextDouble() - 0.5) * 0.01;

            client.world.addParticle(
                    ParticleTypes.HAPPY_VILLAGER,
                    pos.x + rx,
                    pos.y + ry,
                    pos.z + rz,
                    vx, vy, vz
            );
        }

        // tiny extra “sparkle” occasionally while casting (still green-ish)
        if (casting) {
            float t = (seed * 0.37f);
            if (MathHelper.sin(t) > 0.85f) {
                client.world.addParticle(
                        ParticleTypes.COMPOSTER,
                        pos.x,
                        pos.y + 0.18,
                        pos.z,
                        0.0, 0.02, 0.0
                );
            }
        }
    }
}
