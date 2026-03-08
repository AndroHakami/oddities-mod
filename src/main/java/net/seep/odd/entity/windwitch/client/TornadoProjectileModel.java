package net.seep.odd.entity.windwitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.windwitch.TornadoProjectileEntity;
import software.bernie.geckolib.model.GeoModel;

public final class TornadoProjectileModel extends GeoModel<TornadoProjectileEntity> {

    @Override
    public Identifier getModelResource(TornadoProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/tornado_projectile.geo.json");
    }

    @Override
    public Identifier getTextureResource(TornadoProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/tornado_projectile.png");
    }

    @Override
    public Identifier getAnimationResource(TornadoProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/tornado_projectile.animation.json");
    }
}