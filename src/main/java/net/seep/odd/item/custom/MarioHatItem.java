package net.seep.odd.item.custom;

import net.minecraft.item.ArmorMaterial;

public class MarioHatItem extends AbstractBounceHatItem {
    public MarioHatItem(ArmorMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    protected String getArmorRendererClassName() {
        return "net.seep.odd.item.custom.client.MarioHatRenderer";
    }
}
