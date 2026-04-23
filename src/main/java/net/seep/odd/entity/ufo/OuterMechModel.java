package net.seep.odd.entity.ufo;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class OuterMechModel extends GeoModel<OuterMechEntity> {
    @Override
    public Identifier getModelResource(OuterMechEntity entity) {
        return new Identifier("odd", "geo/outer_mech.geo.json");
    }

    @Override
    public Identifier getTextureResource(OuterMechEntity entity) {
        return new Identifier("odd", "textures/entity/outer_mech.png");
    }

    @Override
    public Identifier getAnimationResource(OuterMechEntity entity) {
        return new Identifier("odd", "animations/outer_mech.animation.json");
    }

    @Override
    public void setCustomAnimations(OuterMechEntity entity, long instanceId, AnimationState<OuterMechEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        var mainBody = this.getAnimationProcessor().getBone("main_body");
        if (mainBody != null && entity.allowMainBodyLook()) {
            // main_body should only "look" by yawing.
            // Do NOT pitch the whole mech body here.
            mainBody.setRotY(mainBody.getRotY() + entity.getLookYawRad());
        }

        var extend = this.getAnimationProcessor().getBone("extend");
        if (extend != null) {
            // extend should only rotate on its local Y axis
            extend.setRotY(extend.getRotY() + entity.getExtendYawRad());
        }

        var tube2 = this.getAnimationProcessor().getBone("tube2");
        if (tube2 != null) {
            // tube2 should only rotate on its local X axis
            tube2.setRotX(tube2.getRotX() + entity.getTube2PitchRad());
        }
    }
}