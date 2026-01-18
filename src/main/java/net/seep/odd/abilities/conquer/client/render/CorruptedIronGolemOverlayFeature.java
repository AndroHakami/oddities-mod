package net.seep.odd.abilities.conquer.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.IronGolemEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.conquer.client.ConquerCorruptionClient;

public final class CorruptedIronGolemOverlayFeature
        extends FeatureRenderer<IronGolemEntity, IronGolemEntityModel<IronGolemEntity>> {

    private static final Identifier OVERLAY =
            new Identifier("odd", "textures/entity/conquer/dark_iron_golem.png");

    public CorruptedIronGolemOverlayFeature(FeatureRendererContext<IronGolemEntity, IronGolemEntityModel<IronGolemEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       IronGolemEntity entity,
                       float limbAngle,
                       float limbDistance,
                       float tickDelta,
                       float animationProgress,
                       float headYaw,
                       float headPitch) {

        if (!ConquerCorruptionClient.isCorrupted(entity)) return;

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(OVERLAY));
        this.getContextModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV,
                1.0f, 1.0f, 1.0f, 1.0f);
    }
}
