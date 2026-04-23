package net.seep.odd.entity.scared_rascal_fight;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ScaredRascalFightRenderer extends GeoEntityRenderer<ScaredRascalFightEntity> {
    public ScaredRascalFightRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ScaredRascalFightModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.35F;
    }
}
