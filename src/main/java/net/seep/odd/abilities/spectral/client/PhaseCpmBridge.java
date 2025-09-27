package net.seep.odd.abilities.spectral.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Bridges Spectral Phase state to CPM. When phase turns on, plays the "phase" animation looping.
 * When phase turns off, stops it. Uses reflection to call your existing CpmHooks so this compiles
 * even if CPM / hooks are missing.
 *
 * Expected hooks (any of these will work if present):
 *   - CpmHooks.loop(String)
 *   - CpmHooks.playLoop(String)
 *   - CpmHooks.play(String, boolean loop)
 *   - CpmHooks.play(String)              // if your CPM "phase" anim is set to Loop in editor
 * And for stopping:
 *   - CpmHooks.stop(String)
 *   - CpmHooks.stopLoop(String)
 *   - CpmHooks.stopAll()
 */
@Environment(EnvType.CLIENT)
public final class PhaseCpmBridge {
    private static final String HOOKS = "net.seep.odd.abilities.overdrive.client.CpmHooks";
    private static boolean playing;

    private PhaseCpmBridge() {}

    public static void onPhaseActiveChanged(boolean active) {
        if (active && !playing) {
            // try to start looping "phase"
            boolean ok =
                    tryInvoke("loop", "phase") ||
                            tryInvoke("playLoop", "phase") ||
                            tryInvoke("play", "phase", Boolean.TRUE) ||
                            tryInvoke("play", "phase"); // relies on CPM animation itself being Loop type
            if (ok) playing = true;
        } else if (!active && playing) {
            // try to stop it cleanly
            boolean ok =
                    tryInvoke("stop", "phase") ||
                            tryInvoke("stopLoop", "phase") ||
                            tryInvoke("stopAll");
            playing = false; // even if stop failed, avoid hammering
        }
    }

    private static boolean tryInvoke(String method, Object... args) {
        try {
            Class<?> cls = Class.forName(HOOKS);
            Class<?>[] sig = Arrays.stream(args).map(a -> {
                if (a instanceof Boolean) return boolean.class;
                return String.class;
            }).toArray(Class[]::new);
            Method m = cls.getDeclaredMethod(method, sig);
            m.setAccessible(true);
            m.invoke(null, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
