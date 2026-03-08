package net.seep.odd.entity.windwitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.windwitch.WindWitchEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class WindWitchModel extends GeoModel<WindWitchEntity> {

    @Override
    public Identifier getModelResource(WindWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/wind_witch.geo.json");
    }

    @Override
    public Identifier getTextureResource(WindWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/wind_witch.png");
    }

    @Override
    public Identifier getAnimationResource(WindWitchEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/wind_witch.animation.json");
    }

    @Override
    public void setCustomAnimations(WindWitchEntity animatable, long instanceId, AnimationState<WindWitchEntity> animationState) {
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