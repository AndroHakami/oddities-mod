package net.seep.odd.entity.ufo;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class UfoBomberModel extends GeoModel<UfoBomberEntity> {
    @Override
    public Identifier getModelResource(UfoBomberEntity entity) {
        return new Identifier("odd", "geo/ufo_bomber.geo.json");
    }

    @Override
    public Identifier getTextureResource(UfoBomberEntity entity) {
        return new Identifier("odd", "textures/entity/ufo_bomber.png");
    }

    @Override
    public Identifier getAnimationResource(UfoBomberEntity entity) {
        return new Identifier("odd", "animations/ufo_bomber.animation.json");
    }

    @Override
    public void setCustomAnimations(UfoBomberEntity entity, long instanceId, AnimationState<UfoBomberEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        var bomber = this.getAnimationProcessor().getBone("bomber");
        if (bomber != null) {
            bomber.setRotX(entity.getVisualPitchRad());
            bomber.setRotZ(entity.getVisualRollRad());
        }
    }
}