package net.seep.odd.entity.dragoness;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class DragonessModel extends GeoModel<DragonessEntity> {
    @Override
    public Identifier getModelResource(DragonessEntity entity) {
        return new Identifier(Oddities.MOD_ID, "geo/dragoness.geo.json");
    }

    @Override
    public Identifier getTextureResource(DragonessEntity entity) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/dragoness.png");
    }

    @Override
    public Identifier getAnimationResource(DragonessEntity entity) {
        return new Identifier(Oddities.MOD_ID, "animations/dragoness.animation.json");
    }

    @Override
    public void setCustomAnimations(DragonessEntity entity, long instanceId, AnimationState<DragonessEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        GeoBone all = (GeoBone) this.getAnimationProcessor().getBone("all");
        if (all != null) {
            DragonessAttackType type = entity.getAttackType();
            boolean allowFlightAdjustment = entity.isAirbornePose()
                    && type != DragonessAttackType.CHILL_STANCE
                    && type != DragonessAttackType.CHILL_LOOP
                    && type != DragonessAttackType.CHILL_DISTURBED;
            if (allowFlightAdjustment) {
                all.setRotX(all.getRotX() + entity.getFlightPosePitchRad());
                all.setRotZ(all.getRotZ() + entity.getFlightPoseRollRad());
            }
        }

        if (!entity.shouldLockHeadLook()) {
            GeoBone head = (GeoBone) this.getAnimationProcessor().getBone("head");
            EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && data != null) {
                float degToRad = (float) (Math.PI / 180.0);
                head.setRotX(head.getRotX() + data.headPitch() * degToRad);
                head.setRotY(head.getRotY() + data.netHeadYaw() * degToRad);
            }
        }
    }
}
