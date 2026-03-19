package net.seep.odd.entity.fatwitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.fatwitch.FatWitchSigilEntity;
import software.bernie.geckolib.model.GeoModel;

public final class FatWitchSigilModel extends GeoModel<FatWitchSigilEntity> {

    @Override
    public Identifier getModelResource(FatWitchSigilEntity entity) {
        return new Identifier(Oddities.MOD_ID, "geo/fat_witch_sigil.geo.json");
    }

    @Override
    public Identifier getTextureResource(FatWitchSigilEntity entity) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/fat_witch_sigil.png");
    }

    @Override
    public Identifier getAnimationResource(FatWitchSigilEntity entity) {
        return new Identifier(Oddities.MOD_ID, "animations/fat_witch_sigil.animation.json");
    }
}