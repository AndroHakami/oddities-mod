package net.seep.odd.abilities.gamble.item.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;
import software.bernie.geckolib.model.GeoModel;

public final class GambleRevolverModel extends GeoModel<GambleRevolverItem> {
    @Override
    public Identifier getModelResource(GambleRevolverItem item) {
        return new Identifier(Oddities.MOD_ID, "geo/gamble_revolver.geo.json");
    }
    @Override
    public Identifier getTextureResource(GambleRevolverItem item) {
        // keep your texture path; change if your PNG lives elsewhere
        return new Identifier(Oddities.MOD_ID, "textures/entity/gamble/gamble_revolver.png");
    }
    @Override
    public Identifier getAnimationResource(GambleRevolverItem item) {
        return new Identifier(Oddities.MOD_ID, "animations/gamble_revolver.animation.json");
    }
}
