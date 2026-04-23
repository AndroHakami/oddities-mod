package net.seep.odd.entity.ufo;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;


public class UfoSlicerModel extends GeoModel<UfoSlicerEntity> {
    @Override
    public Identifier getModelResource(UfoSlicerEntity entity) {
        return new Identifier("odd", "geo/ufo_slicer.geo.json");
    }

    @Override
    public Identifier getTextureResource(UfoSlicerEntity entity) {
        return new Identifier("odd", "textures/entity/ufo_slicer.png");
    }

    @Override
    public Identifier getAnimationResource(UfoSlicerEntity entity) {
        return new Identifier("odd", "animations/ufo_slicer.animation.json");
    }

    @Override
    public void setCustomAnimations(UfoSlicerEntity animatable, long instanceId, AnimationState<UfoSlicerEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        CoreGeoBone entire = this.getAnimationProcessor().getBone("entire_ufo");
        if (entire != null) {
            // If your model's forward axis is flipped, just negate this value.
            entire.setRotX(animatable.getVisualTiltRad());
        }
    }
}