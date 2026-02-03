// src/main/java/net/seep/odd/abilities/necromancer/NecromancerCorpseDetonator.java
package net.seep.odd.abilities.necromancer;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

// change this import if your corpse base class is elsewhere:
import net.seep.odd.entity.necromancer.AbstractCorpseEntity;

public final class NecromancerCorpseDetonator {
    private NecromancerCorpseDetonator() {}

    private static final int CHARGE_TICKS = 20 * 3;   // 3 seconds
    private static final double RANGE = 30.0;
    private static final float EXPLOSION_POWER = 2.7f; // slightly < creeper-ish
    private static final Object2ObjectOpenHashMap<net.minecraft.registry.RegistryKey<net.minecraft.world.World>, Int2LongOpenHashMap> SCHEDULED =
            new Object2ObjectOpenHashMap<>();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(NecromancerCorpseDetonator::tickWorld);
    }

    /** Arms every corpse within RANGE blocks. Returns how many were armed. */
    public static int armCorpses(ServerWorld sw, Vec3d center) {
        long explodeAt = sw.getTime() + CHARGE_TICKS;

        Int2LongOpenHashMap map = SCHEDULED.computeIfAbsent(sw.getRegistryKey(), k -> new Int2LongOpenHashMap());
        Box box = Box.of(center, RANGE * 2, RANGE * 2, RANGE * 2);

        int count = 0;
        for (AbstractCorpseEntity corpse : sw.getEntitiesByClass(AbstractCorpseEntity.class, box, e -> e.isAlive())) {
            if (corpse.squaredDistanceTo(center) <= (RANGE * RANGE)) {
                map.put(corpse.getId(), explodeAt);
                count++;
            }
        }
        return count;
    }

    private static void tickWorld(ServerWorld sw) {
        Int2LongOpenHashMap map = SCHEDULED.get(sw.getRegistryKey());
        if (map == null || map.isEmpty()) return;

        long now = sw.getTime();

        IntIterator it = map.keySet().iterator();
        while (it.hasNext()) {
            int id = it.nextInt();
            long when = map.get(id);

            if (now < when) continue;

            Entity e = sw.getEntityById(id);
            if (e instanceof AbstractCorpseEntity corpse && corpse.isAlive()) {
                Vec3d p = corpse.getPos();

                // explode (no block damage)
                sw.createExplosion(
                        null,                  // source entity
                        p.x, p.y + 0.05, p.z,
                        EXPLOSION_POWER,
                        false,                 // create fire
                        World.ExplosionSourceType.NONE // ✅ no block damage
                );

                // delete corpse
                corpse.discard();
            }

            it.remove();
        }

        if (map.isEmpty()) SCHEDULED.remove(sw.getRegistryKey());
    }
}
