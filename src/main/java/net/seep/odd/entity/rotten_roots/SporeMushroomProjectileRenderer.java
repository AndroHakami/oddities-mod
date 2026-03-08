// FILE: src/main/java/net/seep/odd/entity/rotten_roots/SporeMushroomProjectileRenderer.java
package net.seep.odd.entity.rotten_roots;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public final class SporeMushroomProjectileRenderer extends EntityRenderer<SporeMushroomProjectileEntity> {

    public SporeMushroomProjectileRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SporeMushroomProjectileEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider providers, int light) {

        matrices.push();

        // face flight direction
        float y = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
        float p = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(y - 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p));

        // make it more readable than vanilla item-render-in-flight
        matrices.scale(1.25f, 1.25f, 1.25f);

        ItemRenderer ir = MinecraftClient.getInstance().getItemRenderer();
        ItemStack stack = entity.getStack();
        if (stack.isEmpty()) stack = new ItemStack(net.minecraft.item.Items.RED_MUSHROOM);

        // render twice as a cross so it doesn't vanish at certain angles
        ir.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                matrices, providers, entity.getWorld(), entity.getId());

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
        ir.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                matrices, providers, entity.getWorld(), entity.getId() + 1);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, providers, light);
    }

    @Override
    public Identifier getTexture(SporeMushroomProjectileEntity entity) {
        // not used for ItemRenderer path
        return net.minecraft.util.Identifier.of("minecraft", "textures/misc/white.png");
    }
}