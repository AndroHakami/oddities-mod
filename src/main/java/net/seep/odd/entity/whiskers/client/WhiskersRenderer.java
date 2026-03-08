// FILE: src/main/java/net/seep/odd/entity/whiskers/client/WhiskersRenderer.java
package net.seep.odd.entity.whiskers.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.whiskers.WhiskersEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class WhiskersRenderer extends GeoEntityRenderer<WhiskersEntity> {
    public WhiskersRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new WhiskersModel());
        this.shadowRadius = 0.35f;
    }
}