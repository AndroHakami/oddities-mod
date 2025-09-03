package net.seep.odd.abilities.anim;

public final class CpmHolder {
    private static CpmBridge INSTANCE = new CpmBridgeNoop();
    public static void install(CpmBridge b){ INSTANCE = (b == null ? new CpmBridgeNoop() : b); }
    public static CpmBridge get(){ return INSTANCE; }
}