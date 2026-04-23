package net.seep.odd.item.custom;

import net.minecraft.item.ArmorMaterial;

public class SeepHatItem extends AbstractBounceHatItem {
    public SeepHatItem(ArmorMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    protected String getArmorRendererClassName() {
        return "net.seep.odd.item.custom.client.SeepHatRenderer";
    }
}
