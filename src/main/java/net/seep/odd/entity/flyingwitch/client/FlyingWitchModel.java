package net.seep.odd.entity.flyingwitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.flyingwitch.FlyingWitchEntity;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.core.animation.AnimationState;

public final class FlyingWitchModel extends GeoModel<FlyingWitchEntity> {

    @Override
    public Identifier getModelResource(FlyingWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/flying_witch.geo.json");
    }

    @Override
    public Identifier getTextureResource(FlyingWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/flying_witch.png");
    }

    @Override
    public Identifier getAnimationResource(FlyingWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/flying_witch.animation.json");
    }

    @Override
    public void setCustomAnimations(FlyingWitchEntity animatable, long instanceId, AnimationState<FlyingWitchEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone head = (GeoBone) this.getAnimationProcessor().getBone("head");
        if (head == null) return;

        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) return;

        float degToRad = (float) (Math.PI / 180.0);

        head.setRotX(data.headPitch() * degToRad);
        head.setRotY(data.netHeadYaw() * degToRad);
    }
}