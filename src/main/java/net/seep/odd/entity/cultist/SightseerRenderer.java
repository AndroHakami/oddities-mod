package net.seep.odd.entity.cultist;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class SightseerRenderer extends GeoEntityRenderer<SightseerEntity> {
    public SightseerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SightseerModel());
        this.shadowRadius = 0.75f;
    }

    @Override
    public RenderLayer getRenderType(SightseerEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}
