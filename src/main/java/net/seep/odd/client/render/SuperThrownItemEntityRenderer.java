package net.seep.odd.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.entity.supercharge.SuperThrownItemEntity;

/** Renders the thrown item with a satisfying tumble/spin. */
public class SuperThrownItemEntityRenderer extends EntityRenderer<SuperThrownItemEntity> {

    public SuperThrownItemEntityRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(SuperThrownItemEntity ent, float entityYaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vcp, int light) {

        ItemStack stack = ent.getStack();
        if (stack.isEmpty()) return;

        matrices.push();

        // Base facing like FlyingItemEntityRenderer does
        matrices.multiply(this.dispatcher.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));

        // Tumble: deterministic speed/axes from id so all clients match
        final int seed = ent.getId() * 1103515245 + 12345;
        float t     = (ent.age + tickDelta);
        float spd   = 18.0f + ((seed >>> 8) & 31); // 18..49 deg per tick
        float ang   = (float) Math.toRadians(spd) * t;

        // Mix three axes to get a nice wobble
        float ax = 0.35f + ((seed & 0xF) / 60f);
        float ay = 0.55f + (((seed >> 4) & 0xF) / 50f);
        float az = 0.25f + (((seed >> 8) & 0xF) / 70f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(ang * ax));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(ang * ay));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(ang * az));

        // Render item (your ItemRenderer mixin will apply the orange takeover + overlays)
        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV,
                matrices, vcp, ent.getWorld(), 0
        );

        matrices.pop();
        super.render(ent, entityYaw, tickDelta, matrices, vcp, light);
    }

    @Override public Identifier getTexture(SuperThrownItemEntity entity) { return null; }
}
