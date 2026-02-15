// FILE: src/main/java/net/seep/odd/item/wizard/client/WalkingStickItemModel.java
package net.seep.odd.item.wizard.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.item.wizard.WalkingStickItem;
import software.bernie.geckolib.model.GeoModel;

public final class WalkingStickItemModel extends GeoModel<WalkingStickItem> {
    private static final Identifier MODEL = new Identifier(Oddities.MOD_ID, "geo/walking_stick.geo.json");
    private static final Identifier ANIMS = new Identifier(Oddities.MOD_ID, "animations/walking_stick.animation.json");

    // NOTE: Texture is chosen dynamically in the renderer, but GeoModel still needs *some* texture.
    private static final Identifier FALLBACK_TEX = new Identifier(Oddities.MOD_ID, "textures/item/walking_stick_none.png");

    @Override
    public Identifier getModelResource(WalkingStickItem animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(WalkingStickItem animatable) {
        return FALLBACK_TEX;
    }

    @Override
    public Identifier getAnimationResource(WalkingStickItem animatable) {
        return ANIMS;
    }
}
