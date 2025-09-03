package net.seep.odd.abilities.anim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One tiny API you call from powers to drive CPM animations/variables.
 * The implementation is swapped at runtime depending on whether CPM is installed.
 */
public interface CpmBridge {
    /** True if CPM (and its client API) are present. */
    boolean present();

    /** Set a boolean var for a given player model (local or remote on this client). */
    void setBool(UUID playerId, String var, boolean value);

    /** Set a float var [0..1] (or any float CPM var you defined). */
    void setFloat(UUID playerId, String var, float value);

    /** Play a gesture/animation clip you named inside the CPM model. */
    void playGesture(UUID playerId, String gestureName, float speed, boolean loop);

    /** Convenience for local player. */
    default void setBoolLocal(String var, boolean v) {
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) setBool(mc.player.getUuid(), var, v);
    }
    default void setFloatLocal(String var, float v) {
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) setFloat(mc.player.getUuid(), var, v);
    }
    default void playGestureLocal(String g, float s, boolean loop) {
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) playGesture(mc.player.getUuid(), g, s, loop);
    }
}
