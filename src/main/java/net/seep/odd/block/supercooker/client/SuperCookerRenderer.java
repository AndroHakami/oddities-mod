package net.seep.odd.block.supercooker.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import net.seep.odd.block.supercooker.SuperCookerBlockEntity;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SuperCookerRenderer extends GeoBlockRenderer<SuperCookerBlockEntity> {

    public SuperCookerRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new SuperCookerModel());
    }

    @Override
    public void preRender(MatrixStack poseStack,
                          SuperCookerBlockEntity be,
                          BakedGeoModel model,
                          VertexConsumerProvider bufferSource,
                          VertexConsumer buffer,
                          boolean isReRender,
                          float partialTick,
                          int packedLight,
                          int packedOverlay,
                          float red, float green, float blue, float alpha) {

        float geoHeightBlocks = 18f / 16f;

        int t = be.getEmergeTicks();
        float p = Math.min(1f, (t + partialTick) / 20f);

        float startY = -geoHeightBlocks - 0.05f;
        float offsetY = startY * (1f - p);

        poseStack.translate(0, offsetY, 0);

        super.preRender(poseStack, be, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderFinal(MatrixStack poseStack,
                            SuperCookerBlockEntity be,
                            BakedGeoModel model,
                            VertexConsumerProvider bufferSource,
                            VertexConsumer buffer,
                            float partialTick,
                            int packedLight,
                            int packedOverlay,
                            float red, float green, float blue, float alpha) {

        super.renderFinal(poseStack, be, model, bufferSource, buffer, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);

        // if finished, show the cooked item
        if (be.isFinished() && !be.getResultForRender().isEmpty()) {
            renderResult(be, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // otherwise render ingredients (placed or cooking display)
        renderIngredients(be, partialTick, poseStack, bufferSource, packedLight);
    }

    private void renderResult(SuperCookerBlockEntity be, float partialTick,
                              MatrixStack matrices, VertexConsumerProvider buffers, int light) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        float time = (mc.world.getTime() + partialTick);

        matrices.push();
        matrices.translate(0.5f, 1.04f + 0.03f * MathHelper.sin(time * 0.15f), 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(time * 0.03f));
        matrices.scale(0.65f, 0.65f, 0.65f);

        mc.getItemRenderer().renderItem(
                be.getResultForRender(),
                ModelTransformationMode.GROUND,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                buffers,
                mc.world,
                (int)(be.getPos().asLong())
        );

        matrices.pop();
    }

    private void renderIngredients(SuperCookerBlockEntity be, float partialTick,
                                   MatrixStack matrices, VertexConsumerProvider buffers, int light) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        var list = be.getRenderIngredients();
        int count = 0;
        for (int i = 0; i < list.size(); i++) if (!list.get(i).isEmpty()) count++;
        if (count == 0) return;

        float swirlBoost = be.getStirVisualTicks() > 0 ? (be.getStirVisualTicks() / 12f) : 0f;
        float time = (mc.world.getTime() + partialTick);
        float baseSpin = time * (0.06f + swirlBoost * 0.22f);

        float y = 1.02f;

        int idx = 0;
        for (int i = 0; i < list.size(); i++) {
            var stack = list.get(i);
            if (stack.isEmpty()) continue;

            float ang = (float) (idx / (double)count * Math.PI * 2.0) + baseSpin;
            float radius = 0.18f + 0.05f * MathHelper.sin(time * 0.2f + idx);
            radius *= (1.0f - swirlBoost * 0.35f);

            float x = 0.5f + MathHelper.cos(ang) * radius;
            float z = 0.5f + MathHelper.sin(ang) * radius;

            matrices.push();
            matrices.translate(x, y, z);
            matrices.translate(0, 0.02f * MathHelper.sin(time * 0.25f + idx), 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(ang));
            matrices.scale(0.45f, 0.45f, 0.45f);

            mc.getItemRenderer().renderItem(
                    stack,
                    ModelTransformationMode.GROUND,
                    light,
                    OverlayTexture.DEFAULT_UV,
                    matrices,
                    buffers,
                    mc.world,
                    (int)(be.getPos().asLong() + i)
            );

            matrices.pop();
            idx++;
        }
    }
}
