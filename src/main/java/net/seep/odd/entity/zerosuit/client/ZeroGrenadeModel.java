package net.seep.odd.entity.zerosuit.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.zerosuit.ZeroGrenadeEntity;
import software.bernie.geckolib.model.GeoModel;

public final class ZeroGrenadeModel extends GeoModel<ZeroGrenadeEntity> {
    @Override
    public Identifier getModelResource(ZeroGrenadeEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/zero_gernade.geo.json");
    }

    @Override
    public Identifier getTextureResource(ZeroGrenadeEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/zero_gernade.png");
    }

    @Override
    public Identifier getAnimationResource(ZeroGrenadeEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/zero_gernade.animation.json");
    }
}
