package net.seep.odd.item.custom.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.item.custom.BlueTrumpetAxeItem;
import net.seep.odd.item.custom.TrumpetAxeItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class BlueTrumpetAxeRenderer extends GeoItemRenderer<BlueTrumpetAxeItem> {
    public BlueTrumpetAxeRenderer() {
        super(new BlueTrumpetAxeModel());
        TrumpetAxeWaveFx.init();
    }

    private static final Pose NORMAL_THIRD_RIGHT = new Pose(
            0f, 0f, 0f,
            -0.75f, -6f, 1.75f,
            1f, 1f, 1f
    );

    private static final Pose NORMAL_THIRD_LEFT = new Pose(
            0f, 0f, 0f,
            0.25f, -6f, 1.75f,
            1f, 1f, 1f
    );

    private static final Pose NORMAL_FIRST_RIGHT = new Pose(
            0f, 0f, -3.5f,
            -2f, -3f, 0f,
            0.77f, 0.77f, 0.77f
    );

    private static final Pose NORMAL_FIRST_LEFT = new Pose(
            -19f, 9f, -10.06f,
            -5f, -1f, 6.75f,
            0.71f, 0.72f, 0.72f
    );

    private static final Pose BLOW_THIRD_RIGHT = new Pose(
            -24f, 0f, 0f,
            -1f, -4.25f, 3.25f,
            1f, 1f, 1f
    );

    private static final Pose BLOW_THIRD_LEFT = new Pose(
            -24f, 0f, 0f,
            0.25f, -2f, 1.75f,
            1f, 1f, 1f
    );

    private static final Pose BLOW_FIRST_RIGHT = new Pose(
            -92.25f, 0f, -3.5f,
            -5.25f, 5f, 2.6f,
            0.77f, 0.77f, 0.77f
    );

    private static final Pose BLOW_FIRST_LEFT = new Pose(
            -92.25f, 0f, -3.5f,
            -5.25f, 5f, 2.6f,
            0.77f, 0.77f, 0.77f
    );

    @Override
    public void render(ItemStack stack, ModelTransformationMode transformType, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        matrices.push();

        Pose pose = choosePose(stack, transformType);
        if (pose != null) {
            pose.apply(matrices);
        }

        super.render(stack, transformType, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }

    private Pose choosePose(ItemStack stack, ModelTransformationMode transformType) {
        boolean blowing = isBlowing(stack);

        return switch (transformType) {
            case THIRD_PERSON_RIGHT_HAND -> blowing ? BLOW_THIRD_RIGHT : NORMAL_THIRD_RIGHT;
            case THIRD_PERSON_LEFT_HAND -> blowing ? BLOW_THIRD_LEFT : NORMAL_THIRD_LEFT;
            case FIRST_PERSON_RIGHT_HAND -> blowing ? BLOW_FIRST_RIGHT : NORMAL_FIRST_RIGHT;
            case FIRST_PERSON_LEFT_HAND -> blowing ? BLOW_FIRST_LEFT : NORMAL_FIRST_LEFT;
            default -> null;
        };
    }

    private boolean isBlowing(ItemStack stack) {
        if (!stack.hasNbt()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }

        long until = stack.getNbt().getLong(TrumpetAxeItem.BLOW_POSE_UNTIL_NBT);
        return until > client.world.getTime();
    }

    private record Pose(
            float rotX, float rotY, float rotZ,
            float tx, float ty, float tz,
            float sx, float sy, float sz
    ) {
        void apply(MatrixStack matrices) {
            matrices.translate(tx / 16.0F, ty / 16.0F, tz / 16.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
            matrices.scale(sx, sy, sz);
        }
    }
}
