package net.seep.odd.item.custom.client;

import net.minecraft.item.ArmorItem;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public abstract class AbstractBounceHatRenderer<T extends ArmorItem & GeoItem> extends GeoArmorRenderer<T> {
    protected AbstractBounceHatRenderer(GeoModel<T> model) {
        super(model);
    }
}
