package net.seep.odd.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny shared runtime that makes sure "timed effects" don't persist across
 * integrated-server restarts / dev disconnect-rejoin cycles.
 *
 * Usage:
 *   OddEffectRuntime.registerReset(SomeEffect::resetRuntimeState);
 */
public final class OddEffectRuntime {
    private OddEffectRuntime() {}

    private static final List<Runnable> RESETTERS = new CopyOnWriteArrayList<>();
    private static boolean inited = false;

    public static void registerReset(Runnable resetter) {
        init();
        RESETTERS.add(resetter);
    }

    private static void init() {
        if (inited) return;
        inited = true;

        // Integrated server: leaving a world stops the server but NOT the JVM.
        // So we must clear static maps on stop + next start.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> resetAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> resetAll());
    }

    public static void resetAll() {
        for (Runnable r : RESETTERS) {
            try { r.run(); }
            catch (Throwable t) {
                System.out.println("[OddEffectRuntime] reset failed: " + t);
            }
        }
    }
}
