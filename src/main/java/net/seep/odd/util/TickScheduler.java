package net.seep.odd.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

public final class TickScheduler {
    private TickScheduler() {}
    private static boolean init = false;

    private record Task(long dueTick, Runnable run) {}
    private static final Map<ServerWorld, PriorityQueue<Task>> QUEUES = new HashMap<>();

    public static void init() {
        if (init) return; init = true;
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            var q = QUEUES.get(world);
            if (q == null || q.isEmpty()) return;
            long now = world.getTime();
            while (!q.isEmpty() && q.peek().dueTick <= now) {
                var t = q.poll();
                try { t.run.run(); } catch (Throwable ignored) {}
            }
        });
    }

    public static void runLater(ServerWorld world, int delayTicks, Runnable task) {
        var q = QUEUES.computeIfAbsent(world,
                w -> new PriorityQueue<>(Comparator.comparingLong(Task::dueTick)));
        q.add(new Task(world.getTime() + delayTicks, task));
    }
}
