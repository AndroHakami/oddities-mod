package net.seep.odd.entity.zerosuit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import net.seep.odd.entity.zerosuit.ZeroBeamEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Camera-facing textured ribbon from start→end with a soft outer glow. */
public final class ZeroBeamRenderer extends EntityRenderer<ZeroBeamEntity> {
    private static final Identifier TEX_CORE = new Identifier(Oddities.MOD_ID, "textures/effects/zero_beam_core.png");
    private static final Identifier TEX_GLOW = new Identifier(Oddities.MOD_ID, "textures/effects/zero_beam_glow.png");

    // Tiles/second UV scroll
    private static final float SCROLL = 2.0f;

    public ZeroBeamRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override public Identifier getTexture(ZeroBeamEntity e) { return TEX_CORE; }

    @Override
    public void render(ZeroBeamEntity e, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider buffers, int packedLight) {
        // Read tracked values
        final Vec3d start = e.getStart();
        final Vec3d dir   = e.getDir().normalize();
        final double len  = e.getBeamLength();
        final float radius = (float)e.getBeamWidth();

        if (len <= 0.01 || radius <= 0f) return;

        // End point and mid (entity is spawned at mid)
        final Vec3d end = start.add(dir.multiply(len));
        final Vec3d origin = new Vec3d(e.getX(), e.getY(), e.getZ());

        // Convert to renderer-local (entity origin) space
        final Vec3d a = start.subtract(origin);
        final Vec3d b = end.subtract(origin);

        // Billboard right vector perpendicular to beam; fallback if vertical
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        final float alpha = computeAlpha(e, tickDelta);
        final float u0 = uvOffset(tickDelta);
        final float u1 = u0 + (float)len;

        matrices.push();
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();
        Matrix3f nrm = entry.getNormalMatrix();

        // Core (1× width)
        drawRibbon(
                buffers.getBuffer(RenderLayer.getEntityTranslucent(TEX_CORE)),
                mat, nrm, a, b, right.multiply(radius),
                u0, u1, 0f, 1f,
                1f, 1f, 1f, alpha,
                packedLight
        );

        // Glow (1.5× width, brighter)
        drawRibbon(
                buffers.getBuffer(RenderLayer.getEntityTranslucent(TEX_GLOW)),
                mat, nrm, a, b, right.multiply(radius * 1.5),
                u0 * 0.75f, u1 * 0.75f, 0f, 1f,
                1f, 1f, 1f, alpha * 0.55f,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
        );

        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, buffers, packedLight);
    }

    private static float computeAlpha(ZeroBeamEntity e, float tickDelta) {
        int life = e.getLife();
        int max  = Math.max(1, e.getMaxLife());
        float t = MathHelper.clamp(life / (float)max, 0f, 1f);
        return t * t; // ease-out
    }

    private static float uvOffset(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float secs = (mc.world == null ? 0f : (mc.world.getTime() + tickDelta)) / 20f;
        return secs * SCROLL;
    }

    /** Emits a quad: (a−r)→(b−r)→(b+r)→(a+r) with full vertex format. */
    private static void drawRibbon(
            VertexConsumer vc, Matrix4f mat, Matrix3f nrm,
            Vec3d a, Vec3d b, Vec3d r,
            float u0, float u1, float v0, float v1,
            float cr, float cg, float cb, float ca,
            int light) {

        final float nx = 0, ny = 1, nz = 0;

        float axL = (float)(a.x - r.x), ayL = (float)(a.y - r.y), azL = (float)(a.z - r.z);
        float axR = (float)(a.x + r.x), ayR = (float)(a.y + r.y), azR = (float)(a.z + r.z);
        float bxL = (float)(b.x - r.x), byL = (float)(b.y - r.y), bzL = (float)(b.z - r.z);
        float bxR = (float)(b.x + r.x), byR = (float)(b.y + r.y), bzR = (float)(b.z + r.z);

        // tri 1
        vc.vertex(mat, axL, ayL, azL).color(cr, cg, cb, ca).texture(u0, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(mat, bxL, byL, bzL).color(cr, cg, cb, ca).texture(u1, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(mat, bxR, byR, bzR).color(cr, cg, cb, ca).texture(u1, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        // tri 2
        vc.vertex(mat, axL, ayL, azL).color(cr, cg, cb, ca).texture(u0, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(mat, bxR, byR, bzR).color(cr, cg, cb, ca).texture(u1, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(mat, axR, ayR, azR).color(cr, cg, cb, ca).texture(u0, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
    }
}
