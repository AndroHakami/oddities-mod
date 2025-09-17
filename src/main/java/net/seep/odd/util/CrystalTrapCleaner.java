package net.seep.odd.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public final class CrystalTrapCleaner {
    private CrystalTrapCleaner() {}

    public static void init() {
        // On world load: clean once
        ServerWorldEvents.LOAD.register((server, world) -> clean(world));

        // Periodic clean: every 10 seconds
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 200 == 0) clean(world); // 200 ticks ~ 10s
        });
    }

    private static void clean(ServerWorld world) {
        long now = world.getTime();

        // Build a world-wide box using the world border to avoid huge doubles
        var wb = world.getWorldBorder();
        double minX = wb.getBoundWest()  - 2;
        double maxX = wb.getBoundEast()  + 2;
        double minZ = wb.getBoundNorth() - 2;
        double maxZ = wb.getBoundSouth() + 2;
        Box box = new Box(minX, world.getBottomY(), minZ, maxX, world.getTopY(), maxZ);

        // Find BlockDisplays tagged as our trap, and discard if expired (or clearly orphaned)
        for (DisplayEntity.BlockDisplayEntity e :
                world.getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, box,
                        ent -> ent.getCommandTags().contains("odd_crystal_trap"))) {

            long expireAt = -1L;
            for (String tag : e.getCommandTags()) {
                if (tag.startsWith("odd_exp_")) {
                    try { expireAt = Long.parseLong(tag.substring(8)); } catch (NumberFormatException ignored) {}
                    break;
                }
            }

            // If we have an expire time and it's passed, remove it.
            // If we somehow lack an expire tag, be conservative: remove if it's been around > 15s (age resets on load, so also check noSave absence).
            if ((expireAt > 0 && now >= expireAt) || (!e.isRemoved() && e.age > 300)) {
                e.discard();
            }
        }
    }
}
