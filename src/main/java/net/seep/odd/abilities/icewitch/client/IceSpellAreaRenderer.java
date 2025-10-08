package net.seep.odd.abilities.icewitch.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.seep.odd.abilities.icewitch.IceSpellAreaEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class IceSpellAreaRenderer extends GeoEntityRenderer<IceSpellAreaEntity> {
    public IceSpellAreaRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new IceSpellAreaModel());
        // Auto-detects "<base>_glow.png"
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.0f;
    }

    @Override
    public RenderLayer getRenderType(IceSpellAreaEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(IceSpellAreaEntity entity, float entityYaw, float partialTicks, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (entity.getVelocity().lengthSquared() > 0.01) {
            matrices.translate(0, Math.sin((entity.age + partialTicks) * 0.08) * 0.02, 0);
        }
        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}
