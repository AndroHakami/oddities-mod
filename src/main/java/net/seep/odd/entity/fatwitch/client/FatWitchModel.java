package net.seep.odd.entity.fatwitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.fatwitch.FatWitchEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class FatWitchModel extends GeoModel<FatWitchEntity> {

    @Override
    public Identifier getModelResource(FatWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/fat_witch.geo.json");
    }

    @Override
    public Identifier getTextureResource(FatWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/fat_witch.png");
    }

    @Override
    public Identifier getAnimationResource(FatWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/fat_witch.animation.json");
    }

    @Override
    public void setCustomAnimations(FatWitchEntity animatable, long instanceId, AnimationState<FatWitchEntity> animationState) {
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