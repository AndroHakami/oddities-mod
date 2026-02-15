// FILE: src/main/java/net/seep/odd/abilities/sniper/client/render/SniperGrappleLeashRenderUtil.java
package net.seep.odd.abilities.sniper.client.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class SniperGrappleLeashRenderUtil {
    private SniperGrappleLeashRenderUtil() {}

    // Cable base color (dark charcoal)
    private static final int BASE_R = 38;
    private static final int BASE_G = 38;
    private static final int BASE_B = 42;

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static float stripe(int seg) {
        // alternating bright/dark bands (steel wrap vibe)
        return (seg & 1) == 0 ? 1.05f : 0.72f;
    }

    private static float fiberNoise(float t, int seg) {
        // subtle twisted-cable shimmer, very restrained
        return 0.97f + 0.03f * (float) Math.sin((t * 34.0f) + (seg * 2.1f));
    }

    /**
     * Draw a cable from local (0,0,0) to local (dx,dy,dz).
     * MatrixStack is already translated to the entity render position by Minecraft.
     */
    public static void drawCableLocal(VertexConsumer vc, MatrixStack matrices,
                                      float dx, float dy, float dz,
                                      Vec3d cameraFromStartWorld,
                                      int light) {
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 1.0e-4) return;

        Vec3d dir = new Vec3d(dx / len, dy / len, dz / len);

        Vec3d view = cameraFromStartWorld;
        if (view.lengthSquared() < 1.0e-6) view = new Vec3d(0, 1, 0);
        else view = view.normalize();

        // Two crossed ribbons so it looks “3D”
        Vec3d sideA = dir.crossProduct(view);
        if (sideA.lengthSquared() < 1.0e-6) sideA = dir.crossProduct(new Vec3d(0, 1, 0));
        if (sideA.lengthSquared() < 1.0e-6) sideA = new Vec3d(1, 0, 0);
        sideA = sideA.normalize();

        Vec3d sideB = dir.crossProduct(sideA).normalize();

        // Slightly thinner than climber rope
        double w = 0.045;
        sideA = sideA.multiply(w);
        sideB = sideB.multiply(w);

        int segments = 28;

        // Much less sag for a grapple cable (more taut)
        double sag = Math.min(0.18, len * 0.03);

        Matrix4f m = matrices.peek().getPositionMatrix();

        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;

            float x0 = dx * t0;
            float y0 = dy * t0;
            float z0 = dz * t0;

            float x1 = dx * t1;
            float y1 = dy * t1;
            float z1 = dz * t1;

            double s0 = 4.0 * t0 * (1.0 - t0);
            double s1 = 4.0 * t1 * (1.0 - t1);
            y0 -= (float)(sag * s0);
            y1 -= (float)(sag * s1);

            // Slight darkening along the length + striping + tiny noise
            float along0 = 0.90f + 0.10f * (1.0f - t0);
            float along1 = 0.90f + 0.10f * (1.0f - t1);

            float shade0 = stripe(i)     * along0 * fiberNoise(t0, i);
            float shade1 = stripe(i + 1) * along1 * fiberNoise(t1, i + 1);

            // Add a subtle “steel highlight” bias (cool grey)
            int r0 = clamp255((int)(BASE_R * shade0 + 10));
            int g0 = clamp255((int)(BASE_G * shade0 + 10));
            int b0 = clamp255((int)(BASE_B * shade0 + 14));

            int r1 = clamp255((int)(BASE_R * shade1 + 10));
            int g1 = clamp255((int)(BASE_G * shade1 + 10));
            int b1 = clamp255((int)(BASE_B * shade1 + 14));

            // Offset second ribbon phase slightly for a “twist”
            float shade0b = stripe(i + 1) * along0 * fiberNoise(t0, i + 2);
            float shade1b = stripe(i)     * along1 * fiberNoise(t1, i + 3);

            int r0b = clamp255((int)(BASE_R * shade0b + 6));
            int g0b = clamp255((int)(BASE_G * shade0b + 6));
            int b0b = clamp255((int)(BASE_B * shade0b + 10));

            int r1b = clamp255((int)(BASE_R * shade1b + 6));
            int g1b = clamp255((int)(BASE_G * shade1b + 6));
            int b1b = clamp255((int)(BASE_B * shade1b + 10));

            ribbon(vc, m, x0, y0, z0, x1, y1, z1, sideA, r0, g0, b0, r1, g1, b1, light);
            ribbon(vc, m, x0, y0, z0, x1, y1, z1, sideB, r0b, g0b, b0b, r1b, g1b, b1b, light);
        }
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m,
                               float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               Vec3d side,
                               int r0, int g0, int b0,
                               int r1, int g1, int b1,
                               int light) {
        float sx = (float) side.x;
        float sy = (float) side.y;
        float sz = (float) side.z;

        v(vc, m, x0 - sx, y0 - sy, z0 - sz, r0, g0, b0, light);
        v(vc, m, x0 + sx, y0 + sy, z0 + sz, r0, g0, b0, light);
        v(vc, m, x1 + sx, y1 + sy, z1 + sz, r1, g1, b1, light);

        v(vc, m, x0 - sx, y0 - sy, z0 - sz, r0, g0, b0, light);
        v(vc, m, x1 + sx, y1 + sy, z1 + sz, r1, g1, b1, light);
        v(vc, m, x1 - sx, y1 - sy, z1 - sz, r1, g1, b1, light);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                          int r, int g, int b, int light) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).light(light).next();
    }
}
