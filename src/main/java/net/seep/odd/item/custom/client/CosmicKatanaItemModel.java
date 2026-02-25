// net/seep/odd/item/custom/client/CosmicKatanaItemModel.java
package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.item.custom.CosmicKatanaItem;
import software.bernie.geckolib.model.GeoModel;

public class CosmicKatanaItemModel extends GeoModel<CosmicKatanaItem> {

    @Override
    public Identifier getModelResource(CosmicKatanaItem item) {
        // resources/assets/odd/geo/cosmic_katana.geo.json
        return new Identifier("odd", "geo/cosmic_katana.geo.json");
    }

    @Override
    public Identifier getTextureResource(CosmicKatanaItem item) {
        // resources/assets/odd/textures/item/cosmic_katana.png
        return new Identifier("odd", "textures/item/cosmic_katana.png");
    }

    @Override
    public Identifier getAnimationResource(CosmicKatanaItem item) {
        // resources/assets/odd/animations/cosmic_katana.animation.json
        // Must include at least: "idle" and "block"
        return new Identifier("odd", "animations/cosmic_katana.animation.json");
    }
}