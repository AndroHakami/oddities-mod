package net.seep.odd.entity.zerosuit;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import org.joml.Matrix4f;

/** Beacon-like square beam: filled core using the beacon texture + sealed square caps, plus a faint additive halo. */
public final class ZeroBeamRenderer extends EntityRenderer<ZeroBeamEntity> {
    // Vanilla beacon beam texture for the core look
    private static final Identifier TEX_BEACON = new Identifier("textures/entity/beacon_beam.png");
    // Optional subtle outer glow
    private static final Identifier TEX_HALO   = new Identifier(Oddities.MOD_ID, "textures/effects/zero_beam_shell.png");

    private static final float U_REPEAT = 0.35f; // texture repeats per block of length
    private static final float HALO_SCALE = 1.18f; // halo slightly larger than core
    private static final float EPS = 0.0006f; // tiny separation to avoid coplanar z shimmer

    // Core: translucent textured (beacon look), depth-write ON, cull OFF (so inside looks filled), caps drawn
    private static final RenderLayer CORE = RenderLayer.of(
            "odd_zero_beam_core_beacon",
            VertexFormats.POSITION_COLOR_TEXTURE,
            VertexFormat.DrawMode.QUADS,
            512, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(GameRenderer::getPositionColorTexProgram))
                    .texture(new RenderPhase.Texture(TEX_BEACON, false, false))
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)       // draw both sides so inside doesn't look hollow
                    .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderPhase.ALL_MASK)     // write depth (fixes face fighting)
                    .build(false)
    );

    // Halo: additive, faint, depth-write ON, cull ON
    private static final RenderLayer HALO = RenderLayer.of(
            "odd_zero_beam_halo",
            VertexFormats.POSITION_COLOR_TEXTURE,
            VertexFormat.DrawMode.QUADS,
            512, false, true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(GameRenderer::getPositionColorTexProgram))
                    .texture(new RenderPhase.Texture(TEX_HALO, false, false))
                    .transparency(RenderPhase.ADDITIVE_TRANSPARENCY)
                    .cull(RenderPhase.ENABLE_CULLING)
                    .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                    .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                    .writeMaskState(RenderPhase.ALL_MASK)
                    .build(false)
    );

    public ZeroBeamRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override public Identifier getTexture(ZeroBeamEntity e) { return TEX_BEACON; }

    @Override
    public void render(ZeroBeamEntity beam, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        Vec3d dir = beam.getDir().normalize();
        float len = (float) beam.getBeamLength();
        float baseR = (float) beam.getRadius();
        if (len <= 0.01f || baseR <= 0f) return;

        // Straight, no wobble; thickness already scales with charge via radius provided by entity
        float life = MathHelper.clamp(beam.getLifeAlpha(), 0f, 1f);
        float coreAlpha = 0.95f * (life*life);   // bright, but not fully 1 to keep blending nice
        float haloAlpha = 0.35f * (life*life);   // subtle glow

        float rCore = baseR;                     // square half-width (core)
        float rHalo = baseR * HALO_SCALE + EPS;  // slightly larger (avoid depth tie)

        // Orient to beam direction
        matrices.push();
        float yawRad   = (float)Math.atan2(dir.x, dir.z);
        float pitchRad = (float)(-(Math.asin(dir.y)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yawRad));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitchRad));

        // Scrolling UV like a beacon
        float tScroll = (beam.age + tickDelta) * 0.02f;
        float u0 = tScroll;
        float u1 = tScroll + len * U_REPEAT;

        // 1) Draw core (4 sides + 2 square caps) — FILLS the volume visually
        VertexConsumer core = buffers.getBuffer(CORE);
        Matrix4f m = matrices.peek().getPositionMatrix();

        // Sides (X+ / X- / Y+ / Y-)
        quadPosColTex(core, m,  rCore, -rCore, 0,  rCore,  rCore, 0,  u0, 0f, coreAlpha); // +X near
        quadPosColTex(core, m,  rCore, -rCore, len,  rCore,  rCore, len,  u1, 1f, coreAlpha); // +X far

        quadPosColTex(core, m, -rCore, -rCore, 0, -rCore,  rCore, 0,  u0, 0f, coreAlpha); // -X near
        quadPosColTex(core, m, -rCore, -rCore, len, -rCore,  rCore, len,  u1, 1f, coreAlpha); // -X far

        quadPosColTex(core, m, -rCore,  rCore, 0,  rCore,  rCore, 0,  u0, 0f, coreAlpha); // +Y near (top)
        quadPosColTex(core, m, -rCore,  rCore, len,  rCore,  rCore, len,  u1, 1f, coreAlpha); // +Y far

        quadPosColTex(core, m, -rCore, -rCore, 0,  rCore, -rCore, 0,  u0, 0f, coreAlpha); // -Y near (bottom)
        quadPosColTex(core, m, -rCore, -rCore, len,  rCore, -rCore, len,  u1, 1f, coreAlpha); // -Y far

        // Caps (square covers) — start (z=0) and end (z=len)
        quadCap(core, m, -rCore, -rCore, 0f,  rCore,  rCore, 0f, coreAlpha);     // start cap
        quadCap(core, m, -rCore, -rCore, len, rCore,  rCore, len, coreAlpha);    // end cap

        // 2) Draw faint halo shell (no caps → softer ends)
        VertexConsumer halo = buffers.getBuffer(HALO);
        drawBoxHalo(halo, m, len, rHalo, haloAlpha, u0, u1);

        matrices.pop();
        super.render(beam, yaw, tickDelta, matrices, buffers, light);
    }

    /* ---------- helpers (build quads) ---------- */

    // Build side quads with UV along length like a beacon (two triangles per face)
    private static void quadPosColTex(VertexConsumer vc, Matrix4f m,
                                      float x0, float y0, float z0,
                                      float x1, float y1, float z1,
                                      float uLen0, float vMax, float a) {
        // z0/z1 differ → along length; map V across thickness (0..1), U along length (uLen0..uLen1 set per call)
        // We submit as two strips: near (z0) and far (z1)
        // near strip (z0)
        vc.vertex(m, x0, y0, z0).color(1f,1f,1f,a).texture(uLen0, 0f).next();
        vc.vertex(m, x1, y0, z0).color(1f,1f,1f,a).texture(uLen0, vMax).next();
        vc.vertex(m, x0, y1, z0).color(1f,1f,1f,a).texture(uLen0, 0f).next();

        vc.vertex(m, x1, y0, z0).color(1f,1f,1f,a).texture(uLen0, vMax).next();
        vc.vertex(m, x1, y1, z0).color(1f,1f,1f,a).texture(uLen0, vMax).next();
        vc.vertex(m, x0, y1, z0).color(1f,1f,1f,a).texture(uLen0, 0f).next();

        // far strip (z1) stitched from the same XY extent but using uLen1 is handled by caller using another call
    }

    // Square cap with simple UVs (centered)
    private static void quadCap(VertexConsumer vc, Matrix4f m,
                                float x0, float y0, float z,
                                float x1, float y1, float zSame,
                                float a) {
        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
        vc.vertex(m, x0, y0, z).color(1f,1f,1f,a).texture(u0, v1).next();
        vc.vertex(m, x0, y1, z).color(1f,1f,1f,a).texture(u0, v0).next();
        vc.vertex(m, x1, y0, z).color(1f,1f,1f,a).texture(u1, v1).next();

        vc.vertex(m, x0, y1, z).color(1f,1f,1f,a).texture(u0, v0).next();
        vc.vertex(m, x1, y1, z).color(1f,1f,1f,a).texture(u1, v0).next();
        vc.vertex(m, x1, y0, z).color(1f,1f,1f,a).texture(u1, v1).next();
    }

    private static void drawBoxHalo(VertexConsumer vc, Matrix4f m,
                                    float len, float r, float a, float u0, float u1) {
        // +X
        addPosColorTex(vc, m,  r, -r, 0,   1,1,1,a, u0, 0f);
        addPosColorTex(vc, m,  r,  r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m,  r, -r, len, 1,1,1,a, u1, 0f);
        addPosColorTex(vc, m,  r,  r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m,  r,  r, len, 1,1,1,a, u1, 1f);
        addPosColorTex(vc, m,  r, -r, len, 1,1,1,a, u1, 0f);

        // -X
        addPosColorTex(vc, m, -r, -r, 0,   1,1,1,a, u0, 0f);
        addPosColorTex(vc, m, -r, -r, len, 1,1,1,a, u1, 0f);
        addPosColorTex(vc, m, -r,  r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m, -r,  r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m, -r, -r, len, 1,1,1,a, u1, 0f);
        addPosColorTex(vc, m, -r,  r, len, 1,1,1,a, u1, 1f);

        // +Y
        addPosColorTex(vc, m, -r,  r, 0,   1,1,1,a, u0, 0f);
        addPosColorTex(vc, m, -r,  r, len, 1,1,1,a, u1, 0f);
        addPosColorTex(vc, m,  r,  r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m,  r,  r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m, -r,  r, len, 1,1,1,a, u1, 0f);
        addPosColorTex(vc, m,  r,  r, len, 1,1,1,a, u1, 1f);

        // -Y
        addPosColorTex(vc, m, -r, -r, 0,   1,1,1,a, u0, 0f);
        addPosColorTex(vc, m,  r, -r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m, -r, -r, len, 1,1,1,a, u1, 0f);
        addPosColorTex(vc, m,  r, -r, 0,   1,1,1,a, u0, 1f);
        addPosColorTex(vc, m,  r, -r, len, 1,1,1,a, u1, 1f);
        addPosColorTex(vc, m, -r, -r, len, 1,1,1,a, u1, 0f);
    }

    private static void addPosColorTex(VertexConsumer vc, Matrix4f m,
                                       float x, float y, float z,
                                       float r, float g, float b, float a,
                                       float u, float v) {
        vc.vertex(m, x, y, z).color(r,g,b,a).texture(u,v).next();
    }
}
