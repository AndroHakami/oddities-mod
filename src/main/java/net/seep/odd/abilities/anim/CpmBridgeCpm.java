package net.seep.odd.abilities.anim;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * CPM-backed implementation. This uses reflection on CPM's public client API
 * so your project doesn't take a hard compile-time dependency on specific names.
 *
 * After you read their wiki, you can replace these call-sites with direct calls.
 */
public final class CpmBridgeCpm implements CpmBridge {
    private final Object clientApi; // com.tom.cpm.api.IClientAPI (or similar)
    private final MethodHandle setVarBool;
    private final MethodHandle setVarFloat;
    private final MethodHandle playGesture;

    private CpmBridgeCpm(Object clientApi, MethodHandle setVarBool, MethodHandle setVarFloat, MethodHandle playGesture) {
        this.clientApi = clientApi;
        this.setVarBool = setVarBool;
        this.setVarFloat = setVarFloat;
        this.playGesture = playGesture;
    }

    @Override public boolean present() { return true; }

    @Override public void setBool(UUID playerId, String var, boolean value) {
        try { setVarBool.invoke(clientApi, playerId, var, value); } catch (Throwable ignored) {}
    }
    @Override public void setFloat(UUID playerId, String var, float value) {
        try { setVarFloat.invoke(clientApi, playerId, var, value); } catch (Throwable ignored) {}
    }
    @Override public void playGesture(UUID playerId, String gestureName, float speed, boolean loop) {
        try { playGesture.invoke(clientApi, playerId, gestureName, speed, loop); } catch (Throwable ignored) {}
    }

    /**
     * Try to grab CPM's client API from FabricLoader ObjectShare, using the keys
     * documented on their wiki. We probe a couple common ones defensively.
     */
    public static CpmBridge tryCreate() {
        var share = FabricLoader.getInstance().getObjectShare();
        Object api = share.get("cpm:clientApi");
        if (api == null) api = share.get("cpm:client");  // try alternate key
        if (api == null) api = share.get("cpm:apiClient");

        if (api == null) return null;

        // Resolve the methods via reflection once; cache MethodHandles
        var lookup = MethodHandles.publicLookup();
        try {
            // Replace method names/signatures below with the exact ones from the CPM wiki:
            // e.g. boolean/float variables and named gesture playback for a player UUID on this client.
            MethodHandle setBool = lookup.findVirtual(api.getClass(), "setPlayerBoolean",
                    MethodType.methodType(void.class, UUID.class, String.class, boolean.class));
            MethodHandle setFloat = lookup.findVirtual(api.getClass(), "setPlayerFloat",
                    MethodType.methodType(void.class, UUID.class, String.class, float.class));
            MethodHandle play = lookup.findVirtual(api.getClass(), "playPlayerGesture",
                    MethodType.methodType(void.class, UUID.class, String.class, float.class, boolean.class));
            return new CpmBridgeCpm(api, setBool, setFloat, play);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }
}
