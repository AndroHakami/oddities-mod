package net.seep.odd.block.supercooker.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public class SuperCookerModel extends GeoModel<SuperCookerBlockEntity> {
    @Override
    public Identifier getModelResource(SuperCookerBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/super_cooker.geo.json");
    }

    @Override
    public Identifier getAnimationResource(SuperCookerBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/super_cooker.animation.json");
    }

    @Override
    public Identifier getTextureResource(SuperCookerBlockEntity animatable) {
        return animatable.isEnabledTexture()
                ? new Identifier(Oddities.MOD_ID, "textures/block/super_cooker_enabled.png")
                : new Identifier(Oddities.MOD_ID, "textures/block/super_cooker.png");
    }
}
