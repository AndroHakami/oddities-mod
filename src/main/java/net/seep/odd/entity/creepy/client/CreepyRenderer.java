package net.seep.odd.entity.creepy.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.seep.odd.entity.creepy.CreepyEntity;

public final class CreepyRenderer extends GeoEntityRenderer<CreepyEntity> {
    public CreepyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CreepyModel());
        this.shadowRadius = 0.0f;
    }

    @Override
    public RenderLayer getRenderType(CreepyEntity animatable, Identifier texture, VertexConsumerProvider bufferSource, float partialTick) {
        // transparent if your texture has alpha
        return RenderLayer.getEntityTranslucent(texture);
    }
}
