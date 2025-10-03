// net/seep/odd/abilities/cosmic/client/render/HomingCosmicSwordRenderer.java
package net.seep.odd.abilities.cosmic.entity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class HomingCosmicSwordRenderer extends GeoEntityRenderer<HomingCosmicSwordEntity> {
    public HomingCosmicSwordRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HomingCosmicSwordModel());
        this.shadowRadius = 0.15f;
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderLayer getRenderType(HomingCosmicSwordEntity animatable, Identifier texture, VertexConsumerProvider bufferSource, float partialTick) {
        // Use translucent if your texture has alpha glow; otherwise use getEntityCutoutNoCull(texture)
        return RenderLayer.getEntityTranslucent(texture);
    }
}
