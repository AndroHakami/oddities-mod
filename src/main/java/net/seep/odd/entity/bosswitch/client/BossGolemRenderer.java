package net.seep.odd.entity.bosswitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.bosswitch.BossGolemEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BossGolemRenderer extends GeoEntityRenderer<BossGolemEntity> {
    public BossGolemRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BossGolemModel());
        this.shadowRadius = 1.45f;
    }
}