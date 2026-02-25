// src/main/java/net/seep/odd/block/combiner/client/CombinerRenderer.java
package net.seep.odd.block.combiner.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.seep.odd.block.combiner.CombinerBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class CombinerRenderer extends GeoBlockRenderer<CombinerBlockEntity> {
    public CombinerRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new CombinerModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this)); // ✅ autoglow layer
    }
}