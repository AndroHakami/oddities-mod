
package net.seep.odd.entity.outerblaster.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.outerblaster.BlasterProjectileEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class BlasterProjectileRenderer extends GeoEntityRenderer<BlasterProjectileEntity> {
    public BlasterProjectileRenderer(EntityRendererFactory.Context context) {
        super(context, new BlasterProjectileModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.08f;
    }
}
