package net.seep.odd.item.custom;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import net.seep.odd.item.custom.client.WaluigiHatRenderer;

public class WaluigiHatItem extends AbstractBounceHatItem {
    public WaluigiHatItem(ArmorMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new WaluigiHatRenderer();
    }
}
