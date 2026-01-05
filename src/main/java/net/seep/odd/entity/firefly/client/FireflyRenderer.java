package net.seep.odd.entity.firefly.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.seep.odd.entity.firefly.FireflyEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class FireflyRenderer extends GeoEntityRenderer<FireflyEntity> {
    public FireflyRenderer(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx) {
        super(ctx, new FireflyModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.1f;
    }
}

    // slight emissive glow (if you provide an emissive texture, swap RenderLayer)
