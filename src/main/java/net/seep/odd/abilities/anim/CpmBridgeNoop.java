package net.seep.odd.abilities.anim;

import java.util.UUID;

/** Fallback when CPM isn't installed */
public final class CpmBridgeNoop implements CpmBridge {
    @Override public boolean present() { return false; }
    @Override public void setBool(UUID playerId, String var, boolean value) {}
    @Override public void setFloat(UUID playerId, String var, float value) {}
    @Override public void playGesture(UUID playerId, String gestureName, float speed, boolean loop) {}
}
