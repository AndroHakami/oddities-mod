
package net.seep.odd.entity.outerblaster.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.outerblaster.BlasterProjectileEntity;
import software.bernie.geckolib.model.GeoModel;

public final class BlasterProjectileModel extends GeoModel<BlasterProjectileEntity> {
    @Override
    public Identifier getModelResource(BlasterProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/blaster_projectile.geo.json");
    }

    @Override
    public Identifier getTextureResource(BlasterProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/blaster_projectile.png");
    }

    @Override
    public Identifier getAnimationResource(BlasterProjectileEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/blaster_projectile.animation.json");
    }
}
