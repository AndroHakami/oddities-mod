package net.seep.odd.entity.booklet.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.booklet.BookletEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;

import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.data.EntityModelData;


public final class BookletModel extends GeoModel<BookletEntity> {

    @Override
    public Identifier getModelResource(BookletEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/booklet.geo.json");
    }

    @Override
    public Identifier getTextureResource(BookletEntity animatable) {
        int v = animatable.getVariantId();
        return new Identifier(Oddities.MOD_ID, "textures/entity/booklet/booklet_" + v + ".png");
    }

    @Override
    public Identifier getAnimationResource(BookletEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/booklet.animation.json");
    }

    @Override
    public void setCustomAnimations(BookletEntity animatable, long instanceId, AnimationState<BookletEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone head = (GeoBone) this.getAnimationProcessor().getBone("head");
        if (head == null) return;

        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        float degToRad = (float) (Math.PI / 180.0);

        head.setRotX(data.headPitch() * degToRad);
        head.setRotY(data.netHeadYaw() * degToRad);
    }
}
