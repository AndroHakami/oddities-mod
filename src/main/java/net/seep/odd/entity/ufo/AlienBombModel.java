package net.seep.odd.entity.ufo;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class AlienBombModel extends GeoModel<AlienBombEntity> {
    @Override
    public Identifier getModelResource(AlienBombEntity entity) {
        return new Identifier("odd", "geo/alien_bomb.geo.json");
    }

    @Override
    public Identifier getTextureResource(AlienBombEntity entity) {
        return new Identifier("odd", "textures/entity/alien_bomb.png");
    }

    @Override
    public Identifier getAnimationResource(AlienBombEntity entity) {
        return new Identifier("odd", "animations/alien_bomb.animation.json");
    }
}