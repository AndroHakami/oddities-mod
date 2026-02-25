// FILE: src/main/java/net/seep/odd/abilities/artificer/item/client/ArtificerVacuumRenderer.java
package net.seep.odd.abilities.artificer.item.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ArtificerVacuumRenderer extends GeoItemRenderer<ArtificerVacuumItem> {

    public ArtificerVacuumRenderer() {
        super(new ArtificerVacuumModel());

        // ✅ init looping sounds (safe to call multiple times)


        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.addRenderLayer(new VacuumBeamLayer(this)); // now particle tornado
    }

    public RenderLayer getRenderType(ArtificerVacuumItem anim, Identifier texture,
                                     VertexConsumerProvider buffers, float pt) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}