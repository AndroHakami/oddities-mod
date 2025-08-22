package net.seep.odd.abilities.tamer.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.tamer.projectile.EmeraldShurikenEntity;
import net.seep.odd.item.ModItems;

import net.minecraft.item.ItemStack;

public class EmeraldShurikenRenderer extends EntityRenderer<EmeraldShurikenEntity> {
    private final ItemRenderer itemRenderer;
    private static final ItemStack RENDER_STACK = new ItemStack(ModItems.EMERALD_SHURIKEN);

    public EmeraldShurikenRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0f;
        this.shadowOpacity = 0.0f;
    }

    @Override
    public void render(EmeraldShurikenEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();

        // Slight lift so it doesn't clip into ground on low trajectories
        matrices.translate(0.0, 0.05, 0.0);

        // Align roughly to travel direction for a nicer feel
        Vec3d v = entity.getVelocity();
        if (v.lengthSquared() > 1.0E-4) {
            float yRot = (float)(MathHelper.atan2(v.z, v.x) * 180.0 / Math.PI) + 90.0f;
            float xRot = (float)(MathHelper.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * 180.0 / Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yRot));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(xRot * 0.6f)); // partial pitch, keeps it readable
        }

        // Spin around forward axis
        float age = entity.age + tickDelta;
        float spin = (age * 40.0f) % 360.0f; // 40 deg/tick feels zippy
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));

        // Scale up a bit so it's more visible
        float s = 1.8f; // tweak to taste
        matrices.scale(s, s, s);

        // --- Fake thickness: render the flat sprite several times at different Y rotations ---
        // (0°, 45°, 90°, 135°) gives a quasi-3D star you can see from most angles.
        for (int i = 0; i < 4; i++) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 45.0f));
            itemRenderer.renderItem(
                    RENDER_STACK,
                    ModelTransformationMode.GROUND, // flat sprite
                    light,
                    OverlayTexture.DEFAULT_UV,
                    matrices,
                    consumers,
                    entity.getWorld(),
                    0 // random seed
            );
            matrices.pop();
        }

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    // Required by EntityRenderer: when rendering an item model, we point at the block atlas
    @Override
    public Identifier getTexture(EmeraldShurikenEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
