package net.seep.odd.item.custom;

import net.minecraft.item.ArmorMaterial;

public class WarioHatItem extends AbstractBounceHatItem {
    public WarioHatItem(ArmorMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    protected String getArmorRendererClassName() {
        return "net.seep.odd.item.custom.client.WarioHatRenderer";
    }
}
