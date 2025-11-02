package net.seep.odd.entity.falsefrog.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.falsefrog.FalseFrogEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class FalseFrogRenderer extends GeoEntityRenderer<FalseFrogEntity> {
    public FalseFrogRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FalseFrogModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.9f;
    }
}
