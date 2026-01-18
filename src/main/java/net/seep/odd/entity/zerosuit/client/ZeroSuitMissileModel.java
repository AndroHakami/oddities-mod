package net.seep.odd.entity.zerosuit.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity;
import software.bernie.geckolib.model.GeoModel;

public final class ZeroSuitMissileModel extends GeoModel<ZeroSuitMissileEntity> {

    @Override
    public Identifier getModelResource(ZeroSuitMissileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/zero_suit_missile.geo.json");
    }

    @Override
    public Identifier getTextureResource(ZeroSuitMissileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/zero_suit_missile.png");
    }

    @Override
    public Identifier getAnimationResource(ZeroSuitMissileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/zero_suit_missile.animation.json");
    }
}
