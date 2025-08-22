package net.seep.odd.abilities.tamer.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.abilities.tamer.entity.VillagerEvoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;


public class VillagerEvo1Renderer extends GeoEntityRenderer<VillagerEvoEntity> {
    public VillagerEvo1Renderer(EntityRendererFactory.Context ctx) {
        super(ctx, new VillagerEvo1Model());
        this.shadowRadius = 0.4f;
    }
}
