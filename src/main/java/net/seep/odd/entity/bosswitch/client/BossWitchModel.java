package net.seep.odd.entity.bosswitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.bosswitch.BossWitchEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class BossWitchModel extends GeoModel<BossWitchEntity> {
    @Override
    public Identifier getModelResource(BossWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/boss_witch.geo.json");
    }

    @Override
    public Identifier getTextureResource(BossWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/boss_witch.png");
    }

    @Override
    public Identifier getAnimationResource(BossWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/boss_witch.animation.json");
    }

    @Override
    public void setCustomAnimations(BossWitchEntity animatable, long instanceId, AnimationState<BossWitchEntity> animationState) {
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