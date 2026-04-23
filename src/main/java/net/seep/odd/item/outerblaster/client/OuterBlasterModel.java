
package net.seep.odd.item.outerblaster.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.outerblaster.OuterBlasterItem;
import software.bernie.geckolib.model.GeoModel;

public final class OuterBlasterModel extends GeoModel<OuterBlasterItem> {
    @Override
    public Identifier getModelResource(OuterBlasterItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/outer_blaster.geo.json");
    }

    @Override
    public Identifier getTextureResource(OuterBlasterItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/item/outer_blaster.png");
    }

    @Override
    public Identifier getAnimationResource(OuterBlasterItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/outer_blaster.animation.json");
    }
}
