package net.seep.odd.entity.rascal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.UUID;

public final class RascalRenderer extends GeoEntityRenderer<RascalEntity> {
    public RascalRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RascalModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(RascalEntity entity, float entityYaw, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        super.render(entity, entityYaw, partialTick, matrices, buffers, light);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        UUID owner = entity.getQuestOwnerUuid();
        if (owner == null || !owner.equals(client.player.getUuid())) return;

        Text marker = entity.isCalmed()
                ? Text.literal("❤").formatted(Formatting.RED)
                : Text.literal("!").formatted(Formatting.YELLOW);

        renderMarker(entity, marker, matrices, buffers);
    }

    private void renderMarker(RascalEntity entity, Text marker, MatrixStack matrices, VertexConsumerProvider buffers) {
        TextRenderer textRenderer = this.getTextRenderer();

        matrices.push();
        matrices.translate(0.0D, entity.getHeight() + 0.55D, 0.0D);
        matrices.multiply(this.dispatcher.getRotation());
        matrices.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x = -textRenderer.getWidth(marker) / 2.0F;

        textRenderer.draw(
                marker.asOrderedText(),
                x,
                0.0F,
                0xFFFFFFFF,
                false,
                matrix,
                buffers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
        );

        textRenderer.draw(
                marker.asOrderedText(),
                x,
                0.0F,
                0xFFFFFFFF,
                false,
                matrix,
                buffers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
        );

        matrices.pop();
    }
}