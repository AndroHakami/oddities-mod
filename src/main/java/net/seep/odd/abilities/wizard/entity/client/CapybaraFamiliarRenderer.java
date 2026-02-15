// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/client/CapybaraFamiliarRenderer.java
package net.seep.odd.abilities.wizard.entity.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.abilities.wizard.entity.CapybaraFamiliarEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class CapybaraFamiliarRenderer extends GeoEntityRenderer<CapybaraFamiliarEntity> {
    public CapybaraFamiliarRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CapybaraFamiliarModel());
        this.shadowRadius = 0.35f;
    }
}
