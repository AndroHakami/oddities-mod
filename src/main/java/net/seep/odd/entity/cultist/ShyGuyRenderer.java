package net.seep.odd.entity.cultist;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ShyGuyRenderer extends GeoEntityRenderer<ShyGuyEntity> {
    public ShyGuyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ShyGuyModel());
        this.shadowRadius = 0.75f;
    }

    @Override
    public RenderLayer getRenderType(ShyGuyEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}
