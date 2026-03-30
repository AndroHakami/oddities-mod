package net.seep.odd.item.custom;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import net.seep.odd.item.custom.client.SeepHatRenderer;

public class SeepHatItem extends AbstractBounceHatItem {
    public SeepHatItem(ArmorMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new SeepHatRenderer();
    }
}
