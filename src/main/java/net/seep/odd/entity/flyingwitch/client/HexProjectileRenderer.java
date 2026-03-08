package net.seep.odd.entity.flyingwitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.flyingwitch.HexProjectileEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class HexProjectileRenderer extends GeoEntityRenderer<HexProjectileEntity> {
    public HexProjectileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HexProjectileModel());

        this.shadowRadius = 0.15f;
    }
}