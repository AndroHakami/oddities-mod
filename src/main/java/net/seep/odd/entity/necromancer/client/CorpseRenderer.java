// src/main/java/net/seep/odd/entity/necromancer/client/CorpseRenderer.java
package net.seep.odd.entity.necromancer.client;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.seep.odd.Oddities;
import net.seep.odd.entity.necromancer.AbstractCorpseEntity;

public final class CorpseRenderer<T extends AbstractCorpseEntity> extends EntityRenderer<T> {

    private final Identifier tex;

    public static final Identifier ZOMBIE_TEX =
            new Identifier(Oddities.MOD_ID, "textures/entity/necromancer/zombie_corpse.png");
    public static final Identifier SKELETON_TEX =
            new Identifier(Oddities.MOD_ID, "textures/entity/necromancer/skeleton_corpse.png");

    public CorpseRenderer(EntityRendererFactory.Context ctx, Identifier tex) {
        super(ctx);
        this.tex = tex;
        this.shadowRadius = 0.0f;
    }

    @Override
    public Identifier getTexture(T entity) {
        return tex;
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {

        float a = entity.alpha(tickDelta);
        if (a <= 0.01f) return;

        matrices.push();

        // Lay flat on ground
        matrices.translate(0.0, 0.02, 0.0);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90f));

        // rotate randomly via entity yaw
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(entity.getYaw()));

        float size = 0.9f;
        matrices.scale(size, size, size);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(tex));

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();
        Matrix3f nrm = entry.getNormalMatrix();

        int alpha = (int)(a * 255f);
        int r = 255, g = 255, b = 255;

        // Quad corners in local space (flat)
        float x0 = -0.5f, y0 = -0.5f;
        float x1 =  0.5f, y1 =  0.5f;

        // Up normal (after transforms)
        float nx = 0f, ny = 0f, nz = 1f;

        vc.vertex(mat, x0, y0, 0f).color(r, g, b, alpha).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(mat, x1, y0, 0f).color(r, g, b, alpha).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(mat, x1, y1, 0f).color(r, g, b, alpha).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(mat, x0, y1, 0f).color(r, g, b, alpha).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nrm, nx, ny, nz).next();

        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
