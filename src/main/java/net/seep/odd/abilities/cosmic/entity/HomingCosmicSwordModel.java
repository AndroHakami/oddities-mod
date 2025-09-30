// net/seep/odd/abilities/cosmic/client/model/HomingCosmicSwordModel.java
package net.seep.odd.abilities.cosmic.entity;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;


import net.minecraft.util.Identifier;
import net.seep.odd.item.ghost.GhostHandItem;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public final class HomingCosmicSwordModel extends GeoModel<HomingCosmicSwordEntity> {
    @Override public Identifier getModelResource(HomingCosmicSwordEntity entity) {
        return new Identifier("odd", "geo/cosmic_sword.geo.json");
    }
    @Override public Identifier getTextureResource(HomingCosmicSwordEntity entity) {
        return new Identifier("odd", "textures/entity/cosmic_sword.png");
    }
    @Override public Identifier getAnimationResource(HomingCosmicSwordEntity entity) {
        return new Identifier("odd", "animations/cosmic_sword.animation.json");

    }





    @Override
    public void setCustomAnimations(HomingCosmicSwordEntity entity, long instanceId, AnimationState<HomingCosmicSwordEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);

        // rotate model to match projectile flight (root bone is the safe default)
        CoreGeoBone root = getAnimationProcessor().getBone("root");
        if (root == null) root = getAnimationProcessor().getBone("sword");
        if (root != null) {
            float yaw = entity.getYaw();   // degrees
            float pitch = entity.getPitch();
            root.setRotY((float) Math.toRadians(-yaw + 90f));
            root.setRotX((float) Math.toRadians(pitch));
        }
    }
}
