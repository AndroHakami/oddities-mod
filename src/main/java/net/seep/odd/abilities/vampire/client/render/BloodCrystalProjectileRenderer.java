package net.seep.odd.abilities.vampire.client.render;

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
import net.minecraft.util.math.Vec3d;

import net.minecraft.item.ItemStack;

import net.seep.odd.abilities.vampire.entity.BloodCrystalProjectileEntity;
import net.seep.odd.item.ModItems;

public class BloodCrystalProjectileRenderer extends EntityRenderer<BloodCrystalProjectileEntity> {
    private final ItemRenderer itemRenderer;

    // ✅ make it render as the blood crystal item (you said you’ll texture it)
    private static final ItemStack RENDER_STACK = new ItemStack(ModItems.BLOOD_CRYSTAL_PROJECTILE);


    public BloodCrystalProjectileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0f;
        this.shadowOpacity = 0.0f;
    }

    @Override
    public void render(BloodCrystalProjectileEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();

        // slight lift
        matrices.translate(0.0, 0.05, 0.0);

        // align to velocity
        Vec3d v = entity.getVelocity();
        if (v.lengthSquared() > 1.0E-4) {
            float yRot = (float)(MathHelper.atan2(v.z, v.x) * 180.0 / Math.PI) + 90.0f;
            float xRot = (float)(MathHelper.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * 180.0 / Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yRot));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(xRot * 0.6f));
        }

        // spin
        float age = entity.age + tickDelta;
        float spin = (age * 20.0f) % 360.0f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));

        // scale
        float s = 1.3f;
        matrices.scale(s, s, s);

        // fake thickness (4 passes)
        for (int i = 0; i < 4; i++) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 45.0f));
            itemRenderer.renderItem(
                    RENDER_STACK,
                    ModelTransformationMode.GROUND,
                    light,
                    OverlayTexture.DEFAULT_UV,
                    matrices,
                    consumers,
                    entity.getWorld(),
                    0
            );
            matrices.pop();
        }

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(BloodCrystalProjectileEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
