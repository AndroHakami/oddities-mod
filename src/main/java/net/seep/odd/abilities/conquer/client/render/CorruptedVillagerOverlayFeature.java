package net.seep.odd.abilities.conquer.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.conquer.client.ConquerCorruptionClient;

public final class CorruptedVillagerOverlayFeature
        extends FeatureRenderer<VillagerEntity, VillagerResemblingModel<VillagerEntity>> {

    private static final Identifier OVERLAY =
            new Identifier("odd", "textures/entity/conquer/dark_villager.png");

    public CorruptedVillagerOverlayFeature(FeatureRendererContext<VillagerEntity, VillagerResemblingModel<VillagerEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light,
                       VillagerEntity entity,
                       float limbAngle,
                       float limbDistance,
                       float tickDelta,
                       float animationProgress,
                       float headYaw,
                       float headPitch) {

        if (!ConquerCorruptionClient.isCorrupted(entity)) return;

        // Use cutout so it behaves like normal entity textures (supports transparent pixels)
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(OVERLAY));

        // Context model is already posed/animated by the main renderer
        this.getContextModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV,
                1.0f, 1.0f, 1.0f, 1.0f);
    }
}
