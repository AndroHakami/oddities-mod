// net/seep/odd/abilities/overdrive/client/OverdriveScreenFX.java
package net.seep.odd.abilities.overdrive.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public final class OverdriveScreenFX {
    // zoom tuning (unchanged)
    private static final float MAX_ZOOM_SCALE = 0.80f;
    private static final float PUNCH_ZOOM_BUMP = 1.03f;

    // shake tuning
    private static final float MAX_SHAKE_DEG = 0.5f;
    private static final float PUNCH_SHAKE_DEG = 0.6f;
    private static final float SHAKE_FREQ = 22f;

    private OverdriveScreenFX(){}

    /** Returns adjusted FOV based on charge/punch state. */
    public static double adjustFov(double baseFov) {
        if (!"overdrive".equals(net.seep.odd.abilities.client.ClientPowerHolder.get())) return baseFov;

        boolean charging = OverdriveCpmBridge.isCharging();
        boolean punching = OverdriveCpmBridge.isPunching();

        if (charging) {
            float t = OverdriveCpmBridge.chargeProgress();
            float s = 1f - (1f - MAX_ZOOM_SCALE) * smooth(t);
            return baseFov * s;
        }
        if (punching) return baseFov * PUNCH_ZOOM_BUMP;
        return baseFov;
    }

    /** Result container for shake offsets (in degrees). Return null = no shake. */
    public static final class Shake {
        public final float dyaw, dpitch;
        public Shake(float dyaw, float dpitch) { this.dyaw = dyaw; this.dpitch = dpitch; }
    }

    /** Compute shake offsets; Mixin applies them using shadowed setRotation. */
    public static Shake computeShake(Camera cam, float tickDelta) {
        if (!"overdrive".equals(net.seep.odd.abilities.client.ClientPowerHolder.get())) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return null;

        float ampDeg;
        if (OverdriveCpmBridge.isCharging()) {
            ampDeg = MAX_SHAKE_DEG * smooth(OverdriveCpmBridge.chargeProgress());
        } else if (OverdriveCpmBridge.isPunching()) {
            ampDeg = PUNCH_SHAKE_DEG;
        } else return null;

        float time = (mc.player.age + tickDelta) * SHAKE_FREQ / 20f;
        double nYaw   = Math.sin(time) * 0.6 + Math.sin(time * 1.7 + 1.3) * 0.4;
        double nPitch = Math.sin(time * 1.3 + 0.7) * 0.6 + Math.sin(time * 2.1 + 2.4) * 0.4;

        float dy = (float)(nYaw   * ampDeg);
        float dp = (float)(nPitch * ampDeg * 0.6f);
        return new Shake(dy, dp);
    }

    private static float smooth(float x) { return x * x * (3f - 2f * x); } // smoothstep
    public static float clampPitch(float p) { return Math.max(-90f, Math.min(90f, p)); }
}
