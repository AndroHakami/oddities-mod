package net.seep.odd.abilities.ghostlings.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GhostlingRenderer extends GeoEntityRenderer<GhostlingEntity> {
    public GhostlingRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GhostlingModel());
        this.shadowRadius = 0.4f;
    }
}
