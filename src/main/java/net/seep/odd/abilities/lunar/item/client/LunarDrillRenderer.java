package net.seep.odd.abilities.lunar.item.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class LunarDrillRenderer extends GeoItemRenderer<LunarDrillItem> {

    public LunarDrillRenderer() {
        super(new LunarDrillModel());

    }

    @Override
    public RenderLayer getRenderType(LunarDrillItem animatable,
                                     Identifier texture,
                                     VertexConsumerProvider bufferSource,
                                     float partialTick) {
        // Translucent layer lets you use soft alpha on the texture (e.g., glow edges).
        return RenderLayer.getEntityTranslucent(texture);
    }

    /** Call from your existing client initializer to hook the renderer:
     *  GeoItemRenderer.registerItemRenderer(ModItems.LUNAR_DRILL, new LunarDrillRenderer());
     */
}
