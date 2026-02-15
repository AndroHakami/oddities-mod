// FILE: src/main/java/net/seep/odd/item/wizard/client/WalkingStickRenderProvider.java
package net.seep.odd.item.wizard.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import software.bernie.geckolib.animatable.client.RenderProvider;

public final class WalkingStickRenderProvider implements RenderProvider {
    private WalkingStickItemRenderer renderer;

    @Override
    public BuiltinModelItemRenderer getCustomRenderer() {
        if (this.renderer == null) {
            this.renderer = new WalkingStickItemRenderer();
        }
        return this.renderer;
    }
}
