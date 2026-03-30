package net.seep.odd.block.false_memory.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.seep.odd.block.false_memory.FalseMemoryBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class FalseMemoryBlockRenderer extends GeoBlockRenderer<FalseMemoryBlockEntity> {
    public FalseMemoryBlockRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new FalseMemoryBlockModel());
    }
}