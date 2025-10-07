package net.seep.odd.abilities.ghostlings.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;


import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;

/**
 * Renders the Ghostling and attaches its tool stack to the "right_arm" bone.
 * NOTE: GhostlingEntity must expose `public ItemStack getToolStack()` (slot 0).
 */
public class GhostlingRenderer extends GeoEntityRenderer<GhostlingEntity> {

    public GhostlingRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GhostlingModel()); // your GeoModel<T>
    }

    @Override
    public void renderRecursively(MatrixStack matrices,
                                  GhostlingEntity ghost,
                                  GeoBone bone,
                                  RenderLayer layer,
                                  VertexConsumerProvider buffers,
                                  VertexConsumer buffer,
                                  boolean isReRender,
                                  float partialTick,
                                  int light,
                                  int overlay,
                                  float red, float green, float blue,
                                  float alpha) {

        // Render the held tool on the "right_arm" bone
        if ("right_arm".equals(bone.getName())) {
            ItemStack stack = ghost.getToolStack();   // make sure this getter exists in the entity
            if (!stack.isEmpty()) {
                matrices.push();

                // tweak to match your model’s hand position/orientation
                matrices.translate(0.08f, 0.10f, -0.12f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));

                MinecraftClient.getInstance().getItemRenderer().renderItem(
                        stack,
                        ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
                        light,
                        overlay,
                        matrices,
                        buffers,
                        ghost.getWorld(),   // 1.20.1 signature requires world + seed
                        ghost.getId()
                );

                matrices.pop();
            }
        }

        // Let GeckoLib render the rest of the model/bones
        super.renderRecursively(matrices, ghost, bone, layer, buffers, buffer,
                isReRender, partialTick, light, overlay, red, green, blue, alpha);
    }

}
