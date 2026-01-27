// src/main/java/net/seep/odd/abilities/fairy/client/CpmCompat.java
package net.seep.odd.abilities.fairy.client;

import java.lang.reflect.Method;

/**
 * CPM helper via reflection so your mod still compiles even if CPM jars shift.
 * Expected CPM API: com.tom.cpm.api.CPMApi.getClientApi().playAnimation(String)
 */
public final class CpmCompat {
    private CpmCompat() {}

    private static boolean tried = false;
    private static Object clientApi = null;
    private static Method playAnim = null;

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> cpmApiCl = Class.forName("com.tom.cpm.api.CPMApi");
            Method getClientApi = cpmApiCl.getMethod("getClientApi");
            clientApi = getClientApi.invoke(null);

            if (clientApi != null) {
                // common method names in CPM versions:
                try {
                    playAnim = clientApi.getClass().getMethod("playAnimation", String.class);
                } catch (NoSuchMethodException ignored) {
                    playAnim = clientApi.getClass().getMethod("playAnimation", String.class, float.class);
                }
            }
        } catch (Throwable ignored) {
            clientApi = null;
            playAnim = null;
        }
    }

    public static void play(String animName) {
        if (animName == null || animName.isBlank()) return;
        init();
        if (clientApi == null || playAnim == null) return;

        try {
            if (playAnim.getParameterCount() == 1) {
                playAnim.invoke(clientApi, animName);
            } else {
                playAnim.invoke(clientApi, animName, 1.0f);
            }
        } catch (Throwable ignored) {}
    }
}
