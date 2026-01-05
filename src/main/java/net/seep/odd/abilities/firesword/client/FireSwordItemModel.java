package net.seep.odd.abilities.firesword.client;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.firesword.item.FireSwordItem;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.model.GeoModel;

public class FireSwordItemModel extends GeoModel<FireSwordItem> {
    @Override
    public Identifier getModelResource(FireSwordItem item) {
        // Provide this at: resources/assets/odd/geo/lunar_drill.geo.json
        return new Identifier("odd","geo/fire_sword.geo.json");
    }

    @Override
    public Identifier getTextureResource(FireSwordItem item) {
        // Provide this at: resources/assets/odd/textures/entity/lunar_drill.png
        return new Identifier("odd","textures/item/fire_sword.png");
    }

    @Override
    public Identifier getAnimationResource(FireSwordItem item) {
        // Provide this at: resources/assets/odd/animations/lunar_drill.animation.json
        // Must include at least: "idle" and "drill"
        return new Identifier("odd","animations/fire_sword.animation.json");
    }
}
