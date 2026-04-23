package net.seep.odd.entity.outerman;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public final class OuterManGunnerRenderer extends GeoEntityRenderer<OuterManGunnerEntity> {
    private static final String HAND_BONE = "gun_item";

    public OuterManGunnerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new OuterManGunnerModel());

        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));

        this.addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Nullable
            @Override
            protected ItemStack getStackForBone(GeoBone bone, OuterManGunnerEntity animatable) {
                if (HAND_BONE.equals(bone.getName())) {
                    return animatable.getMainHandStack();
                }
                return null;
            }

            @Override
            protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, OuterManGunnerEntity animatable) {
                if (HAND_BONE.equals(bone.getName())) {
                    return ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
                }
                return super.getTransformTypeForStack(bone, stack, animatable);
            }

            @Override
            protected void renderStackForBone(MatrixStack poseStack, GeoBone bone, ItemStack stack,
                                              OuterManGunnerEntity animatable, VertexConsumerProvider bufferSource,
                                              float partialTick, int packedLight, int packedOverlay) {
                if (HAND_BONE.equals(bone.getName())) {
                    poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
                    poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));

                    // extra 180 flip so it stops pointing backwards
                    poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));

                    poseStack.translate(0.0D, 0.0D, 0.0D);
                    poseStack.scale(1.1f, 1.1f, 1.1f);
                }

                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
            }
        });

        this.shadowRadius = 0.7f;
    }

    @Override
    public RenderLayer getRenderType(OuterManGunnerEntity animatable, Identifier texture, VertexConsumerProvider buffers, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void render(OuterManGunnerEntity entity, float entityYaw, float partialTicks, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        if (entity.getVelocity().lengthSquared() > 0.01) {
            matrices.translate(0.0, Math.sin((entity.age + partialTicks) * 0.08) * 0.02, 0.0);
        }

        super.render(entity, entityYaw, partialTicks, matrices, buffers, light);
    }
}