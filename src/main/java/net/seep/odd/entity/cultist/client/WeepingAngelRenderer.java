package net.seep.odd.entity.cultist.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.seep.odd.entity.cultist.WeepingAngelEntity;

@Environment(EnvType.CLIENT)
public final class WeepingAngelRenderer extends EntityRenderer<WeepingAngelEntity> {

    private final BlockRenderManager blocks;

    public WeepingAngelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderManager();
        this.shadowRadius = 0.0f;
    }

    @Override
    public Identifier getTexture(WeepingAngelEntity entity) {
        // not used directly, but required
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }

    @Override
    public void render(WeepingAngelEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        matrices.push();

        // Render block centered on entity
        matrices.translate(-0.5, 0.0, -0.5);

        blocks.renderBlockAsEntity(
                entity.getDisguiseState(),
                matrices,
                vertexConsumers,
                light,
                OverlayTexture.DEFAULT_UV
        );

        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
