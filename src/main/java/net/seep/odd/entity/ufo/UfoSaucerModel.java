package net.seep.odd.entity.ufo;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.ufo.client.UfoSaucerBoneTracker;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class UfoSaucerModel extends GeoModel<UfoSaucerEntity> {
    @Override
    public Identifier getModelResource(UfoSaucerEntity entity) {
        return new Identifier("odd", "geo/ufo_saucer.geo.json");
    }

    @Override
    public Identifier getTextureResource(UfoSaucerEntity entity) {
        return new Identifier("odd", "textures/entity/ufo_saucer.png");
    }

    @Override
    public Identifier getAnimationResource(UfoSaucerEntity entity) {
        return new Identifier("odd", "animations/ufo_saucer.animation.json");
    }

    @Override
    public void setCustomAnimations(UfoSaucerEntity entity, long instanceId, AnimationState<UfoSaucerEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        var arm = this.getAnimationProcessor().getBone("ufo_arm");
        if (arm != null) {
            double px = arm.getPivotX() / 16.0;
            double py = arm.getPivotY() / 16.0;
            double pz = arm.getPivotZ() / 16.0;

            Vec3d local = new Vec3d(-px, py, pz);
            Vec3d rotated = local.rotateY(-entity.getYaw() * MathHelper.RADIANS_PER_DEGREE);

            UfoSaucerBoneTracker.setArmWorldPos(entity.getId(), entity.getPos().add(rotated));
        }
    }
}