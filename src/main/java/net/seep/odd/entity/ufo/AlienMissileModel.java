package net.seep.odd.entity.ufo;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class AlienMissileModel extends GeoModel<AlienMissileEntity> {
    @Override
    public Identifier getModelResource(AlienMissileEntity entity) {
        return new Identifier("odd", "geo/alien_missile.geo.json");
    }

    @Override
    public Identifier getTextureResource(AlienMissileEntity entity) {
        return new Identifier("odd", "textures/entity/alien_missile.png");
    }

    @Override
    public Identifier getAnimationResource(AlienMissileEntity entity) {
        return new Identifier("odd", "animations/alien_missile.animation.json");
    }
}