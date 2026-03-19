// FILE: src/main/java/net/seep/odd/entity/skull_bird/client/SkullBirdRenderer.java
package net.seep.odd.entity.skull_bird.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.skull_bird.SkullBirdEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class SkullBirdRenderer extends GeoEntityRenderer<SkullBirdEntity> {

    public SkullBirdRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SkullBirdModel());
        this.shadowRadius = 0.3f;
    }
}