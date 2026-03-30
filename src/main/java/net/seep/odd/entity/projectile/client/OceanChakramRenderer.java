package net.seep.odd.entity.projectile.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.projectile.OceanChakramEntity;

public class OceanChakramRenderer extends EntityRenderer<OceanChakramEntity> {
    private final ItemRenderer itemRenderer;

    public OceanChakramRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(OceanChakramEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        Entity focused = this.dispatcher.camera.getFocusedEntity();
        if (entity.age < 2 && focused != null && focused.squaredDistanceTo(entity) < 12.25D) {
            return;
        }

        matrices.push();

        Vec3d lookDir = entity.getVelocity();

        // Make return visually obvious by aiming at owner while returning
        if (entity.isReturning() && entity.getOwner() != null) {
            Vec3d toOwner = entity.getOwner().getEyePos().subtract(entity.getPos());
            if (toOwner.lengthSquared() > 1.0E-6D) {
                lookDir = toOwner.normalize();
            }
        }

        if (lookDir.lengthSquared() > 1.0E-6D) {
            Vec3d dir = lookDir.normalize();

            float flightYaw = (float) (Math.atan2(dir.x, dir.z) * (180.0D / Math.PI));
            float horizontal = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            float flightPitch = (float) (Math.atan2(dir.y, horizontal) * (180.0D / Math.PI));

            // Face the movement / return direction
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(flightYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-flightPitch));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        }

        // THIS is the important fix:
        // keep the same spin axis, but lay the chakram flat instead of upright
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));

        // Give return mode a stronger visual read without changing gameplay
        if (entity.isReturning()) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(18.0F));
        }

        matrices.scale(1.1F, 1.1F, 1.1F);

        // Spin around its own center axis
        float spinSpeed = entity.isReturning() ? -58.0F : 34.0F;
        float spin = (entity.age + tickDelta) * spinSpeed;
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));

        this.itemRenderer.renderItem(
                entity.getStack(),
                ModelTransformationMode.FIXED,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                entity.getId()
        );

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(OceanChakramEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}