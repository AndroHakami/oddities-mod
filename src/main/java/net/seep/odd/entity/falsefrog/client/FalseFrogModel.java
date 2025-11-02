package net.seep.odd.entity.falsefrog.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.falsefrog.FalseFrogEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class FalseFrogModel extends GeoModel<FalseFrogEntity> {
    @Override
    public Identifier getModelResource(FalseFrogEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/false_frog.geo.json");
    }
    @Override
    public Identifier getTextureResource(FalseFrogEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/false_frog.png");
    }
    @Override
    public Identifier getAnimationResource(FalseFrogEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/false_frog.animation.json");
    }


}
