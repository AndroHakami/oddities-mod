package net.seep.odd.abilities.lunar.item.client;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import software.bernie.geckolib.model.GeoModel;

public final class LunarDrillModel extends GeoModel<LunarDrillItem> {
    @Override
    public Identifier getModelResource(LunarDrillItem item) {
        // Provide this at: resources/assets/odd/geo/lunar_drill.geo.json
        return new Identifier("odd","geo/lunar_drill.geo.json");
    }

    @Override
    public Identifier getTextureResource(LunarDrillItem item) {
        // Provide this at: resources/assets/odd/textures/entity/lunar_drill.png
        return new Identifier("odd","textures/item/lunar_drill.png");
    }

    @Override
    public Identifier getAnimationResource(LunarDrillItem item) {
        // Provide this at: resources/assets/odd/animations/lunar_drill.animation.json
        // Must include at least: "idle" and "drill"
        return new Identifier("odd","animations/lunar_drill.animation.json");
    }
}
