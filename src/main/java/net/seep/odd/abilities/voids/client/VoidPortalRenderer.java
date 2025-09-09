package net.seep.odd.abilities.voids.client;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.abilities.voids.VoidPortalEntity;

public class VoidPortalRenderer extends EntityRenderer<VoidPortalEntity> {
    private static final Identifier CORE_TEX = new Identifier("odd", "textures/entity/void_portal/core.png");

    public VoidPortalRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VoidPortalEntity e, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        matrices.push();

        // Center the quad on the entity (fix “halfway down”)
        matrices.translate(0.0, e.getHeight() * 0.5, 0.0);

        // Billboard to camera (like particles)
        matrices.multiply(this.dispatcher.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f)); // fix mirrored UV

        // Size & subtle pulse (use same constants as entity so things align)
        float baseW = VoidPortalEntity.R_BASE * 2.1f; // tad wider than particle radius
        float baseH = VoidPortalEntity.HEIGHT + 0.2f;
        float pulse  = 0.03f * (float)Math.sin((e.age + tickDelta) * 0.25f);
        float w = baseW * (1f + pulse);
        float h = baseH * (1f + pulse);

        VertexConsumer buf = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(CORE_TEX));
        MatrixStack.Entry entry = matrices.peek();

        float x0 = -w * 0.5f, x1 =  w * 0.5f;
        float y0 = -h * 0.5f, y1 =  h * 0.5f;
        int r = 255, g = 255, b = 255, a = 235; // slightly transparent so particles show

        // quad (CCW)
        buf.vertex(entry.getPositionMatrix(), x0, y1, 0).color(r,g,b,a)
                .texture(0f,0f).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0)
                .normal(entry.getNormalMatrix(), 0,0,1).next();

        buf.vertex(entry.getPositionMatrix(), x1, y1, 0).color(r,g,b,a)
                .texture(1f,0f).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0)
                .normal(entry.getNormalMatrix(), 0,0,1).next();

        buf.vertex(entry.getPositionMatrix(), x1, y0, 0).color(r,g,b,a)
                .texture(1f,1f).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0)
                .normal(entry.getNormalMatrix(), 0,0,1).next();

        buf.vertex(entry.getPositionMatrix(), x0, y0, 0).color(r,g,b,a)
                .texture(0f,1f).overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0)
                .normal(entry.getNormalMatrix(), 0,0,1).next();

        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(VoidPortalEntity e) {
        return CORE_TEX;
    }
}
