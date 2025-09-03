// net/seep/odd/integrations/cpm/CpmBridge.java
package net.seep.odd.integrations.cpm;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/**
 * Minimal CPM hook: turn a named CPM animation/gesture on or off.
 * Uses reflection so the game still runs if CPM isn't installed.
 */
@Environment(EnvType.CLIENT)
public final class CpmBridge {
    private static Object api; // the CPM client API instance, if present

    private CpmBridge() {}

    /** Call from your client initializer once. */
    public static void init() {
        try {
            // Customizable Player Models API entrypoint
            Class<?> apiClass = Class.forName("com.tom.cpm.api.CustomPlayerModelsApi");
            api = apiClass.getMethod("getClient").invoke(null);
        } catch (Throwable ignored) { /* CPM not present or different version */ }
    }

    /** Turn a looped animation / gesture on or off for the local player. */
    public static void setGesture(String name, boolean active) {
        if (api == null) return;
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        try {
            // Newer CPM: setGestureState(Player, String, boolean)
            var m = api.getClass().getMethod("setGestureState",
                    mc.player.getClass(), String.class, boolean.class);
            m.invoke(api, mc.player, name, active);
            return;
        } catch (Throwable ignored) {}

        try {
            // Older CPM: play/stop named animation
            if (active) {
                var m = api.getClass().getMethod("playAnimation",
                        mc.player.getClass(), String.class, boolean.class);
                m.invoke(api, mc.player, name, /*loop*/true);
            } else {
                var m = api.getClass().getMethod("stopAnimation",
                        mc.player.getClass(), String.class);
                m.invoke(api, mc.player, name);
            }
        } catch (Throwable ignored) {}
    }
}
