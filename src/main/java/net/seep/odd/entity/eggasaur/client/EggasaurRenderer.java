package net.seep.odd.entity.eggasaur.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.eggasaur.EggasaurEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class EggasaurRenderer extends GeoEntityRenderer<EggasaurEntity> {
    public EggasaurRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new EggasaurModel());
        this.shadowRadius = 0.25f;
    }
}