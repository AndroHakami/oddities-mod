package net.seep.odd.abilities.umbra.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.model.json.ModelTransformationMode;

import net.seep.odd.abilities.umbra.entity.ShadowKunaiEntity;

@Environment(EnvType.CLIENT)
public final class ShadowKunaiRenderer extends EntityRenderer<ShadowKunaiEntity> {

    private final ItemRenderer itemRenderer;

    public ShadowKunaiRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ShadowKunaiEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        matrices.push();

        // move to entity center
        matrices.translate(0.0, 0.0, 0.0);

        float y = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
        float p = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());

        // Align with flight direction (straight)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(y - 90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p));

        // Counter any “45° baked angle” in the item model (straighten it)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-45.0f));

        // Slight scale so it reads like a kunai
        matrices.scale(1.0f, 1.0f, 1.0f);

        itemRenderer.renderItem(
                entity.getStack(),
                ModelTransformationMode.FIXED,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertices,
                entity.getWorld(),
                entity.getId()
        );

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(ShadowKunaiEntity entity) {
        return null; // item-rendered
    }
}