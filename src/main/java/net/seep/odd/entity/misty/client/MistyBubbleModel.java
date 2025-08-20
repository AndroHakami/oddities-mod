package net.seep.odd.entity.misty.client;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import net.seep.odd.entity.misty.MistyBubbleEntity;

public class MistyBubbleModel extends GeoModel<MistyBubbleEntity> {
    @Override public Identifier getModelResource(MistyBubbleEntity animatable) {
        return new Identifier("odd", "geo/misty_bubble.geo.json");
    }
    @Override public Identifier getTextureResource(MistyBubbleEntity animatable) {
        return new Identifier("odd", "textures/entity/misty_bubble/misty_bubble.png");
    }
    @Override public Identifier getAnimationResource(MistyBubbleEntity animatable) {
        return new Identifier("odd", "animations/misty_bubble.animation.json");
    }
}
