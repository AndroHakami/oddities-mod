// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/BlackFlameEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

public final class BlackFlameEffect {
    private BlackFlameEffect() {}

    public static final int DEFAULT_DURATION_TICKS = 20 * 5; // 5s
    public static final float DEFAULT_RADIUS = 4.0f;
    public static final float DEFAULT_HEIGHT = 7.0f;

    private static boolean inited = false;

    private record Active(Vec3d center, float radius, float height, long startTick, int durationTicks) {}

    // per-world active flames (weak so it won't leak on reload)
    private static final WeakHashMap<ServerWorld, Long2ObjectOpenHashMap<Active>> ACTIVE = new WeakHashMap<>();

    private static void initCommon() {
        if (inited) return;
        inited = true;

        // tick per-world
        ServerTickEvents.END_WORLD_TICK.register(BlackFlameEffect::tickWorld);

        // when a player joins, send any nearby active flames (so late arrivals still see it)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity sp = handler.player;
            if (sp == null) return;
            if (!(sp.getWorld() instanceof ServerWorld sw)) return;

            var map = ACTIVE.get(sw);
            if (map == null || map.isEmpty()) return;

            long now = sw.getTime();
            map.long2ObjectEntrySet().fastForEach(e -> {
                long id = e.getLongKey();
                Active a = e.getValue();
                int remaining = (int) Math.max(0, (a.startTick + a.durationTicks) - now);
                if (remaining <= 0) return;

                // only send if reasonably close
                if (sp.squaredDistanceTo(a.center) > (128.0 * 128.0)) return;

                BlackFlameNet.sendState(sp, id, a.center, a.radius, a.height, remaining, a.durationTicks);
            });
        });
    }

    /** Brew THROW entrypoint */
    public static void apply(World world, net.minecraft.util.math.BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;
        initCommon();

        // sink slightly so it feels grounded
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() - 0.35, pos.getZ() + 0.5);

        float radius = DEFAULT_RADIUS;
        float height = DEFAULT_HEIGHT;
        int dur = DEFAULT_DURATION_TICKS;

        long now = sw.getTime();
        long id = sw.getRandom().nextLong() ^ (now << 16);

        ACTIVE.computeIfAbsent(sw, w -> new Long2ObjectOpenHashMap<>())
                .put(id, new Active(center, radius, height, now, dur));

        // initial broadcast (remaining=dur)
        BlackFlameNet.broadcast(sw, id, center, radius, height, dur, dur);

        // sound
        sw.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.35f, 1.45f);
        sw.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.BLOCKS, 0.30f, 0.55f);
    }

    private static void tickWorld(ServerWorld sw) {
        var map = ACTIVE.get(sw);
        if (map == null || map.isEmpty()) return;

        long now = sw.getTime();

        var it = map.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            long id = e.getLongKey();
            Active a = e.getValue();

            long end = a.startTick + a.durationTicks;
            int remaining = (int) (end - now);
            if (remaining <= 0) {
                it.remove();
                continue;
            }

            // ✅ damage tick (every 4 ticks = smooth DOT, not insane)
            if ((now & 3L) == 0L) {
                damageInside(sw, a);
            }

            // ✅ resync periodically so anyone walking in sees it (without extending lifetime)
            if ((now % 10L) == 0L) {
                BlackFlameNet.broadcast(sw, id, a.center, a.radius, a.height, remaining, a.durationTicks);
            }
        }
    }

    private static void damageInside(ServerWorld sw, Active a) {
        Vec3d c = a.center;
        float r = a.radius;
        float h = a.height;

        Box box = new Box(c.x - r, c.y, c.z - r, c.x + r, c.y + h, c.z + r);
        double r2 = (double) r * (double) r;

        for (LivingEntity le : sw.getEntitiesByClass(LivingEntity.class, box, ent -> true)) {
            double dx = le.getX() - c.x;
            double dz = le.getZ() - c.z;
            if ((dx*dx + dz*dz) > r2) continue;

            // damage-over-time
            le.damage(sw.getDamageSources().magic(), 1.75f); // tuned: noticeable over 5s
        }
    }
}
