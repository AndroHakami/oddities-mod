package net.seep.odd.item.custom.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.custom.CrownItem;
import software.bernie.geckolib.model.GeoModel;

public class CrownModel extends GeoModel<CrownItem> {
    @Override
    public Identifier getModelResource(CrownItem animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/crown.geo.json");
    }

    @Override
    public Identifier getTextureResource(CrownItem animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/armor/crown.png");
    }

    @Override
    public Identifier getAnimationResource(CrownItem animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/crown.animation.json");
    }
}