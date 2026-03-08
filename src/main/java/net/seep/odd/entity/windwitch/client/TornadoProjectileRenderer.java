package net.seep.odd.entity.windwitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.windwitch.TornadoProjectileEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class TornadoProjectileRenderer extends GeoEntityRenderer<TornadoProjectileEntity> {
    public TornadoProjectileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new TornadoProjectileModel());
        this.shadowRadius = 0.0f;
    }
}