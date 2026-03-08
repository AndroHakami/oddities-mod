package net.seep.odd.entity.windwitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.windwitch.WindWitchEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class WindWitchRenderer extends GeoEntityRenderer<WindWitchEntity> {
    public WindWitchRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new WindWitchModel());
        this.shadowRadius = 0.55f;
    }
}