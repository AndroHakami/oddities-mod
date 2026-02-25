package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import net.seep.odd.util.OddEffectRuntime;

import java.util.Iterator;

public final class CloudOfEntropyEffect {
    private CloudOfEntropyEffect() {}

    private static final Identifier S2C_SPAWN = new Identifier("odd", "entropy_cloud_spawn");
    private static final Identifier S2C_STOP  = new Identifier("odd", "entropy_cloud_stop");

    // ===== server runtime =====
    private static final Long2ObjectOpenHashMap<Cloud> CLOUDS = new Long2ObjectOpenHashMap<>();
    private static boolean inited = false;
    private static long idCounter = 1;

    // tuneables
    private static final int DURATION_TICKS = 20 * 6;       // 6s
    private static final double BASE_RADIUS = 5.5;          // cloud radius
    private static final int FROZEN_TICKS_SET = 200;
    private static final int FIRE_SECONDS_SET = 2;

    /** Called by OddEffectRuntime on server stop/start so clouds don't persist across rejoin. */
    public static void resetRuntimeState() {
        CLOUDS.clear();
        idCounter = 1;
    }

    public static void apply(World world, BlockPos hitPos, @Nullable LivingEntity thrower, net.minecraft.item.ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;
        initCommon();

        Vec3d c = Vec3d.ofCenter(hitPos).add(0, 0.25, 0);

        long id = (idCounter++);
        long endTick = sw.getServer().getTicks() + DURATION_TICKS;

        Cloud cloud = new Cloud(id, sw.getRegistryKey().getValue(), c, BASE_RADIUS, endTick);
        CLOUDS.put(id, cloud);

        broadcastSpawn(sw, cloud);
    }

    private static void initCommon() {
        if (inited) return;
        inited = true;

        // register reset hook ONCE
        OddEffectRuntime.registerReset(CloudOfEntropyEffect::resetRuntimeState);

        ServerTickEvents.END_SERVER_TICK.register(CloudOfEntropyEffect::serverTick);
    }

    private static void serverTick(MinecraftServer server) {
        if (CLOUDS.isEmpty()) return;

        long now = server.getTicks();
        Iterator<Cloud> it = CLOUDS.values().iterator();

        while (it.hasNext()) {
            Cloud cloud = it.next();

            // resolve world
            ServerWorld sw = null;
            for (ServerWorld w : server.getWorlds()) {
                if (w.getRegistryKey().getValue().equals(cloud.worldId)) { sw = w; break; }
            }
            if (sw == null) { it.remove(); continue; }

            if (now >= cloud.endTick) {
                broadcastStop(sw, cloud.id);
                it.remove();
                continue;
            }

            spawnParticles(sw, cloud);
            applyToLiving(sw, cloud);
        }
    }

    private static void applyToLiving(ServerWorld sw, Cloud cloud) {
        Box box = new Box(
                cloud.center.x - cloud.radius, cloud.center.y - 2.0, cloud.center.z - cloud.radius,
                cloud.center.x + cloud.radius, cloud.center.y + 2.0, cloud.center.z + cloud.radius
        );

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> true)) {
            double d2 = e.getPos().squaredDistanceTo(cloud.center);
            if (d2 > cloud.radius * cloud.radius) continue;

            e.setFrozenTicks(Math.max(e.getFrozenTicks(), FROZEN_TICKS_SET));
            e.setOnFireFor(FIRE_SECONDS_SET);
        }
    }

    private static void spawnParticles(ServerWorld sw, Cloud cloud) {
        sw.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                cloud.center.x, cloud.center.y + 0.4, cloud.center.z,
                14,
                cloud.radius * 0.55, 0.65, cloud.radius * 0.55,
                0.01);

        sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                cloud.center.x, cloud.center.y + 0.25, cloud.center.z,
                10,
                cloud.radius * 0.7, 0.5, cloud.radius * 0.7,
                0.02);

        sw.spawnParticles(ParticleTypes.FLAME,
                cloud.center.x, cloud.center.y + 0.15, cloud.center.z,
                8,
                cloud.radius * 0.65, 0.35, cloud.radius * 0.65,
                0.012);
    }

    private static void broadcastSpawn(ServerWorld sw, Cloud cloud) {
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(cloud.center.x, cloud.center.y, cloud.center.z) > (96.0 * 96.0)) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarLong(cloud.id);
            buf.writeDouble(cloud.center.x);
            buf.writeDouble(cloud.center.y);
            buf.writeDouble(cloud.center.z);
            buf.writeFloat((float) cloud.radius);

            int ticksLeft = (int) MathHelper.clamp(cloud.endTick - sw.getServer().getTicks(), 0, 20 * 60);
            buf.writeVarInt(ticksLeft);

            ServerPlayNetworking.send(p, S2C_SPAWN, buf);
        }
    }

    private static void broadcastStop(ServerWorld sw, long id) {
        for (ServerPlayerEntity p : sw.getPlayers()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarLong(id);
            ServerPlayNetworking.send(p, S2C_STOP, buf);
        }
    }

    private static final class Cloud {
        final long id;
        final Identifier worldId;
        final Vec3d center;
        final double radius;
        final long endTick;

        Cloud(long id, Identifier worldId, Vec3d center, double radius, long endTick) {
            this.id = id;
            this.worldId = worldId;
            this.center = center;
            this.radius = radius;
            this.endTick = endTick;
        }
    }

    /* ================= CLIENT ================= */

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean clientInited = false;
        private static final Long2ObjectOpenHashMap<ClientCloud> CLOUDS = new Long2ObjectOpenHashMap<>();

        public static void init() {
            if (clientInited) return;
            clientInited = true;

            net.seep.odd.abilities.artificer.mixer.brew.client.EntropyCloudFx.init();

            // IMPORTANT: clear on disconnect so it can’t “stick” after rejoin
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                CLOUDS.clear();
                net.seep.odd.abilities.artificer.mixer.brew.client.EntropyCloudFx.setTarget(0f);
                net.seep.odd.abilities.artificer.mixer.brew.client.EntropyCloudFx.tickClient();
            });

            ClientPlayNetworking.registerGlobalReceiver(S2C_SPAWN, (client, handler, buf, rs) -> {
                long id = buf.readVarLong();
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float radius = buf.readFloat();
                int ticksLeft = buf.readVarInt();

                client.execute(() -> CLOUDS.put(id, new ClientCloud(id, new Vec3d(x, y, z), radius, ticksLeft)));
            });

            ClientPlayNetworking.registerGlobalReceiver(S2C_STOP, (client, handler, buf, rs) -> {
                long id = buf.readVarLong();
                client.execute(() -> CLOUDS.remove(id));
            });

            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc == null || mc.player == null) return;

                var iter = CLOUDS.values().iterator();
                while (iter.hasNext()) {
                    ClientCloud c = iter.next();
                    c.ticksLeft--;
                    if (c.ticksLeft <= 0) iter.remove();
                }

                float target = 0f;
                Vec3d pp = mc.player.getPos();

                for (ClientCloud c : CLOUDS.values()) {
                    double d = pp.distanceTo(c.center);
                    float t = (float) (1.0 - MathHelper.clamp((d - c.radius) / 2.0, 0.0, 1.0));
                    target = Math.max(target, t);
                }

                net.seep.odd.abilities.artificer.mixer.brew.client.EntropyCloudFx.setTarget(target);
                net.seep.odd.abilities.artificer.mixer.brew.client.EntropyCloudFx.tickClient();
            });
        }

        private static final class ClientCloud {
            final long id;
            final Vec3d center;
            final float radius;
            int ticksLeft;

            ClientCloud(long id, Vec3d center, float radius, int ticksLeft) {
                this.id = id;
                this.center = center;
                this.radius = radius;
                this.ticksLeft = ticksLeft;
            }
        }
    }
}
