package net.seep.odd.entity.bosswitch.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.entity.bosswitch.BossWitchEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BossWitchRenderer extends GeoEntityRenderer<BossWitchEntity> {
    public BossWitchRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BossWitchModel());
        this.shadowRadius = 0.85f;
    }
}