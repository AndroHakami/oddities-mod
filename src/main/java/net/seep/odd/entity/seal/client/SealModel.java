package net.seep.odd.entity.seal.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.seal.SealEntity;
import software.bernie.geckolib.model.GeoModel;

public final class SealModel extends GeoModel<SealEntity> {

    @Override
    public Identifier getModelResource(SealEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/seal.geo.json");
    }

    @Override
    public Identifier getTextureResource(SealEntity seal) {
        return switch (seal.getVariantId()) {
            case 1 -> new Identifier(Oddities.MOD_ID, "textures/entity/seal/seal_pink.png");
            case 2 -> new Identifier(Oddities.MOD_ID, "textures/entity/seal/seal_blue.png");
            default -> new Identifier(Oddities.MOD_ID, "textures/entity/seal/seal.png");
        };
    }


    @Override
    public Identifier getAnimationResource(SealEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/seal.animation.json");
    }
}
