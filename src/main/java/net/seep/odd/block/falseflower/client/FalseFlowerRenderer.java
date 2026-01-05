package net.seep.odd.block.falseflower.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FalseFlowerRenderer extends GeoBlockRenderer<FalseFlowerBlockEntity> {
    public FalseFlowerRenderer() { super(new DefaultedBlockGeoModel<>(new Identifier(Oddities.MOD_ID, "false_flower"))); }

    @Override
    public Identifier getTextureLocation(FalseFlowerBlockEntity animatable) {
        String key = animatable == null ? "none" :
                net.seep.odd.abilities.fairy.FairySpell.AURA_LEVITATION.textureKey();
        try {
            var skin = animatable.getCachedState().get(net.seep.odd.block.falseflower.FalseFlowerBlock.SKIN).name().toLowerCase();
            key = skin;
        } catch (Exception ignored) {}
        return new Identifier(Oddities.MOD_ID, "textures/block/false_flower/" + key + ".png");
    }
}
