// src/main/java/net/seep/odd/block/falseflower/client/FalseFlowerRenderer.java
package net.seep.odd.block.falseflower.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.falseflower.FalseFlowerBlock;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FalseFlowerRenderer extends GeoBlockRenderer<FalseFlowerBlockEntity> {
    public FalseFlowerRenderer() {
        super(new DefaultedBlockGeoModel<>(new Identifier(Oddities.MOD_ID, "false_flower")));
    }

    @Override
    public Identifier getTextureLocation(FalseFlowerBlockEntity animatable) {
        String key = "none";
        try {
            if (animatable != null) {
                key = animatable.getCachedState().get(FalseFlowerBlock.SKIN).asString();
            }
        } catch (Exception ignored) {}
        return new Identifier(Oddities.MOD_ID, "textures/block/false_flower/" + key + ".png");
    }
}
