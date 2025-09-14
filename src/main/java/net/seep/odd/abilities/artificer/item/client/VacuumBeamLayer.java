package net.seep.odd.abilities.artificer.item.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import net.minecraft.util.math.RotationAxis;

public final class VacuumBeamLayer extends GeoRenderLayer<ArtificerVacuumItem> {
    private static final Identifier TEX =
            new Identifier(net.seep.odd.Oddities.MOD_ID, "textures/effects/vacuum_beam.png");

    // ======= positioning (tune these to taste) =======
    private static final float NOZZLE_X = 0.00f;  // left/right
    private static final float NOZZLE_Y = 0.45f;  // ↑ raise the beam a bit higher
    private static final float NOZZLE_Z = -0.05f; // forward from the item root

    // Point the beam North (-Z). If it still looks off, try -90f or 180f.
    private static final float YAW_DEG   = 270f;   // rotate around Y so “west” → “north”
    private static final float PITCH_DEG = 0f;    // tilt if your nozzle angles up/down

    private static final float LENGTH    = 6f;
    private static final float SIZE_NEAR = 0.03f;
    private static final float SIZE_FAR  = 1.77f;

    private static final float UV_SPEED  = 1.6f;

    public VacuumBeamLayer(GeoItemRenderer<ArtificerVacuumItem> r) { super(r); }

    @Override
    public void render(MatrixStack matrices,
                       ArtificerVacuumItem animatable,
                       BakedGeoModel bakedModel,
                       RenderLayer renderType,
                       VertexConsumerProvider buffers,
                       VertexConsumer buffer,
                       float partialTick,
                       int light,
                       int overlay) {

        if (!shouldRenderBeam()) return;

        matrices.push();

        // 1) move to nozzle
        matrices.translate(NOZZLE_X, NOZZLE_Y, NOZZLE_Z);
        // 2) rotate so local -Z aims North
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(YAW_DEG));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(PITCH_DEG));

        // draw along local -Z
        float z0 = -0.02f;
        float z1 = z0 - LENGTH;
        float s0 = SIZE_NEAR, s1 = SIZE_FAR;

        MatrixStack.Entry e = matrices.peek();

        // translucent for soft edges; switch to getEntityCutoutNoCull(TEX) for hard cutouts
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityTranslucent(TEX));

        float t = (MinecraftClient.getInstance().world == null ? 0f
                : (MinecraftClient.getInstance().world.getTime() + partialTick) * UV_SPEED);
        float vOff = (t % 1f);

        // top
        quad(vc, e, -s0,  s0, z0,  s0,  s0, z0,  s1,  s1, z1, -s1,  s1, z1, vOff, light);
        // bottom
        quad(vc, e, -s0, -s0, z0, -s1, -s1, z1,  s1, -s1, z1,  s0, -s0, z0, vOff, light);
        // left
        quad(vc, e, -s0,  s0, z0, -s1,  s1, z1, -s1, -s1, z1, -s0, -s0, z0, vOff, light);
        // right
        quad(vc, e,  s0,  s0, z0,  s0, -s0, z0,  s1, -s1, z1,  s1,  s1, z1, vOff, light);

        matrices.pop();
    }

    private static boolean shouldRenderBeam() {
        var mc = MinecraftClient.getInstance();
        return mc != null && mc.player != null
                && mc.player.isUsingItem()
                && mc.player.getActiveItem().getItem() instanceof ArtificerVacuumItem;
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry e,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float vOff, int light) {
        float u0 = 0f, u1 = 1f, v0 = vOff, v1 = vOff + 1f;
        float nx = 0, ny = 0, nz = -1;
        vc.vertex(e.getPositionMatrix(), x1, y1, z1).color(255,255,255,160).texture(u0, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e.getNormalMatrix(), nx, ny, nz).next();
        vc.vertex(e.getPositionMatrix(), x2, y2, z2).color(255,255,255,160).texture(u0, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e.getNormalMatrix(), nx, ny, nz).next();
        vc.vertex(e.getPositionMatrix(), x3, y3, z3).color(255,255,255,160).texture(u1, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e.getNormalMatrix(), nx, ny, nz).next();
        vc.vertex(e.getPositionMatrix(), x4, y4, z4).color(255,255,255,160).texture(u1, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e.getNormalMatrix(), nx, ny, nz).next();
    }
}