package net.seep.odd.entity.eggasaur.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.eggasaur.EggasaurEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class EggasaurModel extends GeoModel<EggasaurEntity> {

    @Override
    public Identifier getModelResource(EggasaurEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/eggasaur.geo.json");
    }

    @Override
    public Identifier getTextureResource(EggasaurEntity animatable) {
        int v = animatable.getVariantId();
        return new Identifier(Oddities.MOD_ID, "textures/entity/eggasaur/eggasaur_" + v + ".png");
    }

    @Override
    public Identifier getAnimationResource(EggasaurEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/eggasaur.animation.json");
    }

    @Override
    public void setCustomAnimations(EggasaurEntity animatable, long instanceId, AnimationState<EggasaurEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone head = (GeoBone) this.getAnimationProcessor().getBone("head");
        if (head == null) return;

        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float degToRad = (float) (Math.PI / 180.0);

        head.setRotX(data.headPitch() * degToRad);
        head.setRotY(data.netHeadYaw() * degToRad);
    }
}