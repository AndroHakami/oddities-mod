// FILE: src/main/java/net/seep/odd/entity/client/IceStatueEntityRenderer.java
package net.seep.odd.entity.client;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import net.seep.odd.entity.IceStatueEntity;

public final class IceStatueEntityRenderer extends EntityRenderer<IceStatueEntity> {

    // vanilla ice texture
    private static final Identifier ICE_TEX = new Identifier("minecraft", "textures/block/ice.png");

    private final PlayerEntityModel<IceStatueEntity> model;

    public IceStatueEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.model = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false);
    }

    @Override
    public Identifier getTexture(IceStatueEntity entity) {
        return ICE_TEX;
    }

    @Override
    public void render(IceStatueEntity e, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vcp, int light) {

        matrices.push();

        // face the same direction as entity yaw
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));

        // ✅ flip upright (your model is currently upside-down)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
        // after flipping, it will be vertically inverted around the origin -> nudge it back onto the ground
        matrices.translate(0.0D, -1.5D, 0.0D);

        // freeze pose: we DO NOT call model.setAngles()
        model.sneaking = e.getCrouch();

        model.head.pitch = e.headP();
        model.head.yaw   = e.headY();
        model.head.roll  = e.headR();

        model.body.pitch = e.bodyP();
        model.body.yaw   = e.bodyY();
        model.body.roll  = e.bodyR();

        model.rightArm.pitch = e.raP();
        model.rightArm.yaw   = e.raY();
        model.rightArm.roll  = e.raR();

        model.leftArm.pitch  = e.laP();
        model.leftArm.yaw    = e.laY();
        model.leftArm.roll   = e.laR();

        model.rightLeg.pitch = e.rlP();
        model.rightLeg.yaw   = e.rlY();
        model.rightLeg.roll  = e.rlR();

        model.leftLeg.pitch  = e.llP();
        model.leftLeg.yaw    = e.llY();
        model.leftLeg.roll   = e.llR();

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(getTexture(e)));

        // slightly frosty tint + alpha
        model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV,
                0.85f, 0.95f, 1.00f, 0.88f);

        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, vcp, light);
    }
}