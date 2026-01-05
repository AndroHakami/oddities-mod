package net.seep.odd.abilities.firesword.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.abilities.firesword.entity.FireSwordProjectileEntity;

public class FireSwordProjectileRenderer extends EntityRenderer<FireSwordProjectileEntity> {

    private final ItemRenderer itemRenderer;

    public FireSwordProjectileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(FireSwordProjectileEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertices, int light) {

        matrices.push();

        // Match vanilla thrown-item alignment (faces direction of travel)
        float y = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
        float p = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(y - 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p + 90.0F));

        // ✅ Extra rotation so it points "down/forward" instead of upright
        // If you want the opposite direction, flip the sign to -90f.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));

        itemRenderer.renderItem(
                entity.getStack(),
                ModelTransformationMode.GROUND,
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
    public Identifier getTexture(FireSwordProjectileEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
