package net.seep.odd.entity.flyingwitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.flyingwitch.HexProjectileEntity;
import software.bernie.geckolib.model.GeoModel;

public final class HexProjectileModel extends GeoModel<HexProjectileEntity> {

    @Override
    public Identifier getModelResource(HexProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/hex_projectile.geo.json");
    }

    @Override
    public Identifier getTextureResource(HexProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/hex_projectile.png");
    }

    @Override
    public Identifier getAnimationResource(HexProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/hex_projectile.animation.json");
    }
}