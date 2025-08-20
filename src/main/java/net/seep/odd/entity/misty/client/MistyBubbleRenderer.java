package net.seep.odd.entity.misty.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import net.seep.odd.entity.misty.MistyBubbleEntity;

public class MistyBubbleRenderer extends GeoEntityRenderer<MistyBubbleEntity> {
    public MistyBubbleRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new MistyBubbleModel());
        // Uses textures/entity/misty_bubble/misty_bubble_e.png automatically if present
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0f;
    }

    @Override
    public RenderLayer getRenderType(MistyBubbleEntity e, Identifier tex,
                                     VertexConsumerProvider buffers, float partialTick) {
        // non-cull translucent
        return RenderLayer.getEntityTranslucent(tex);
    }

    // Keep the bubble fullbright so it never appears black
    @Override protected int getBlockLight(MistyBubbleEntity entity, BlockPos pos) { return 15; }
    @Override protected int getSkyLight  (MistyBubbleEntity entity, BlockPos pos) { return 15; }

    // <<< scale here instead of in render() >>>
    @Override
    public void scaleModelForRender(float widthScale, float heightScale,
                                    MatrixStack poseStack,
                                    MistyBubbleEntity animatable,
                                    BakedGeoModel model,
                                    boolean isReRender,
                                    float partialTick,
                                    int packedLight,
                                    int packedOverlay) {

        // Apply our big bubble scale only once (base pass). The glow reRender reuses the same matrices.
        if (!isReRender) {
            float s = 1.4f; // tweak to taste
            poseStack.scale(s, s, s);
        }

        // Let GeoEntityRenderer apply its internal width/height scaling
        super.scaleModelForRender(widthScale, heightScale, poseStack, animatable, model,
                isReRender, partialTick, packedLight, packedOverlay);
    }
}
