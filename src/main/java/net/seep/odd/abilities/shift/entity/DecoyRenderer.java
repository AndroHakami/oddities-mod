package net.seep.odd.abilities.shift.entity;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class DecoyRenderer extends GeoEntityRenderer<DecoyEntity> {
    public DecoyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DecoyModel());
        this.shadowRadius = 0.42F;
    }
}
