package net.seep.odd.abilities.spotted.server;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class BuddyManager {
    private BuddyManager(){}

    public static Entity findBuddy(MinecraftServer server, UUID owner, BuddyPersistentState.Ref ref) {
        if (ref == null) return null;
        ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, ref.dimension));
        if (w == null) return null;
        return w.getEntity(ref.entityUuid); // returns null if not loaded
    }

    public static Entity recallOrSpawn(
            MinecraftServer server,
            ServerWorld targetWorld,
            UUID owner,
            java.util.function.Function<ServerWorld, Entity> spawnFn,
            Vec3d toPos) {

        var state = BuddyPersistentState.get(server);
        var ref = state.get(owner);
        Entity e = findBuddy(server, owner, ref);

        if (e != null) {
            // Move across dimensions if needed
            if (e.getWorld() != targetWorld) {
                e = e.moveToWorld(targetWorld);
                if (e == null) { // move failed, fall back to spawn
                    return spawnNew(server, state, targetWorld, owner, spawnFn, toPos);
                }
            }
            e.teleport(toPos.x, toPos.y, toPos.z);
            return e;
        }

        // Not found (unloaded / gone) -> spawn new and bump generation
        return spawnNew(server, state, targetWorld, owner, spawnFn, toPos);
    }

    private static Entity spawnNew(MinecraftServer server, BuddyPersistentState state, ServerWorld w,
                                   UUID owner, java.util.function.Function<ServerWorld, Entity> spawnFn,
                                   Vec3d toPos) {
        int gen = state.nextGen(owner);
        Entity e = spawnFn.apply(w);
        if (e == null) return null;
        // Write owner + generation onto the entity so it can self-validate (see entity patch below)
        e.setPosition(toPos);
        w.spawnEntity(e);

        state.set(owner, e.getUuid(), w.getRegistryKey().getValue(), gen);
        return e;
    }
}
