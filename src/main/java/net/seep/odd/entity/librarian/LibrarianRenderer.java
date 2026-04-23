
package net.seep.odd.entity.librarian;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class LibrarianRenderer extends GeoEntityRenderer<LibrarianEntity> {
    public LibrarianRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new LibrarianModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.45F;
    }

    @Override
    public RenderLayer getRenderType(LibrarianEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(LibrarianEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}
