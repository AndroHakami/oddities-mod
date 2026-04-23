package net.seep.odd.entity.star_ride;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class StarRideRenderer extends GeoEntityRenderer<StarRideEntity> {
    public StarRideRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new StarRideModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.45F;
    }

    @Override
    public RenderLayer getRenderType(StarRideEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(StarRideEntity entity, float entityYaw, float partialTicks, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        matrices.push();
        matrices.translate(0.0D, Math.sin((entity.age + partialTicks) * 0.12D) * 0.03D, 0.0D);
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
        matrices.pop();
    }
}
