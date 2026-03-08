// FILE: src/main/java/net/seep/odd/abilities/ghostlings/client/GhostlingRenderer.java
package net.seep.odd.abilities.ghostlings.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;

public class GhostlingRenderer extends GeoEntityRenderer<GhostlingEntity> {

    // bone in geo.json
    private static final String ITEM_BONE = "LeftHandItem";

    // make it smaller so it doesn't clip/glint-sheet the whole model
    private static final float ITEM_SCALE = 0.40f;

    public GhostlingRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GhostlingModel());
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

        if (ITEM_BONE.equals(bone.getName())) {
            ItemStack stack = ghost.getMainHandStack(); // MUST be client-synced (DataTracker solution)
            if (!stack.isEmpty()) {
                matrices.push();

                // ✅ Apply the bone transform and STAY at the pivot (do NOT translateAway)
                RenderUtils.translateToPivotPoint(matrices, bone);
                RenderUtils.rotateMatrixAroundBone(matrices, bone);
                RenderUtils.scaleMatrixForBone(matrices, bone);

                // ✅ now we're sitting at the pivot - render item here
                matrices.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);

                // orientation for held items
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));

                // If you want tiny grip offsets, add them here later:
                // matrices.translate(0.0f, 0.0f, -0.05f);

                // Use LivingEntity overload (more stable for entity context)
                MinecraftClient.getInstance().getItemRenderer().renderItem(
                        ghost,
                        stack,
                        ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
                        false,
                        matrices,
                        buffers,
                        ghost.getWorld(),
                        light,
                        overlay,
                        ghost.getId()
                );

                matrices.pop();
            }

            // ✅ VERY IMPORTANT: let GeckoLib run its normal path for this bone using a fresh buffer
            // This tends to prevent the “weird zombie texture” render-state leak.
            VertexConsumer reset = buffers.getBuffer(layer);
            super.renderRecursively(matrices, ghost, bone, layer, buffers, reset,
                    isReRender, partialTick, light, overlay, red, green, blue, alpha);
            return;
        }

        super.renderRecursively(matrices, ghost, bone, layer, buffers, buffer,
                isReRender, partialTick, light, overlay, red, green, blue, alpha);
    }
}