package net.seep.odd.entity.flyingwitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.flyingwitch.FlyingWitchEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class FlyingWitchRenderer extends GeoEntityRenderer<FlyingWitchEntity> {
    public FlyingWitchRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FlyingWitchModel());
        this.shadowRadius = 0.55f;
    }
}