// src/main/java/net/seep/odd/block/cosmic_katana/client/CosmicKatanaBlockRenderer.java
package net.seep.odd.block.cosmic_katana.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.seep.odd.block.cosmic_katana.CosmicKatanaBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class CosmicKatanaBlockRenderer extends GeoBlockRenderer<CosmicKatanaBlockEntity> {
    public CosmicKatanaBlockRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new CosmicKatanaBlockModel());
    }
}