package net.seep.odd.entity.dragoness;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import software.bernie.geckolib.model.GeoModel;

public final class UfoProtectorModel extends GeoModel<UfoProtectorEntity> {
    @Override
    public Identifier getModelResource(UfoProtectorEntity entity) {
        return new Identifier(Oddities.MOD_ID, "geo/ufo_protector.geo.json");
    }

    @Override
    public Identifier getTextureResource(UfoProtectorEntity entity) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/ufo_protector.png");
    }

    @Override
    public Identifier getAnimationResource(UfoProtectorEntity entity) {
        return new Identifier(Oddities.MOD_ID, "animations/ufo_protector.animation.json");
    }
}
