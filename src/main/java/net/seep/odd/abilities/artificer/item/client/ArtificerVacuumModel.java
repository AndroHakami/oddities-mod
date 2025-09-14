package net.seep.odd.abilities.artificer.item.client;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import software.bernie.geckolib.model.GeoModel;

public final class ArtificerVacuumModel extends GeoModel<ArtificerVacuumItem> {
    @Override public Identifier getModelResource(ArtificerVacuumItem item) {
        return new Identifier("odd","geo/vacuum.geo.json");           // <- adjust if needed
    }
    @Override public Identifier getTextureResource(ArtificerVacuumItem item) {
        return new Identifier("odd","textures/entity/vacuum.png");      // <- adjust if needed
    }
    @Override public Identifier getAnimationResource(ArtificerVacuumItem item) {
        return new Identifier("odd","animations/vacuum.animation.json"); // must contain "idle" & "select_gaia"
    }
}
