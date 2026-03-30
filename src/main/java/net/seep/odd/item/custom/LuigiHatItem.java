package net.seep.odd.item.custom;

import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import net.seep.odd.item.custom.client.LuigiHatRenderer;

public class LuigiHatItem extends AbstractBounceHatItem {
    public LuigiHatItem(ArmorMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    protected GeoArmorRenderer<?> createArmorRenderer() {
        return new LuigiHatRenderer();
    }
}
