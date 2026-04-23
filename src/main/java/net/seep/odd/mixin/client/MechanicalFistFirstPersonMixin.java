package net.seep.odd.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.seep.odd.item.custom.MechanicalFistItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class MechanicalFistFirstPersonMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void odd$applyMechanicalFistChargePose(AbstractClientPlayerEntity player,
                                                   float tickDelta,
                                                   float pitch,
                                                   Hand hand,
                                                   float swingProgress,
                                                   ItemStack item,
                                                   float equipProgress,
                                                   MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers,
                                                   int light,
                                                   CallbackInfo ci) {
        if (!(item.getItem() instanceof MechanicalFistItem)) return;
        if (!player.isUsingItem() || player.getActiveHand() != hand) return;

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int dir = arm == Arm.RIGHT ? 1 : -1;

        float usedTicks = player.getItemUseTime() + tickDelta;
        float pull = MathHelper.clamp(usedTicks / (float) MechanicalFistItem.MAX_CHARGE_TICKS, 0.0f, 1.0f);
        pull = MathHelper.clamp((pull * pull + pull * 2.0f) / 3.0f, 0.0f, 1.0f);

        // Bow-style first-person pullback without touching third-person arm posing.
        matrices.translate(dir * -0.2785682F * pull, 0.18344387F * pull, 0.15731531F * pull);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(dir * (13.935F * pull)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(dir * (35.3F * pull)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-9.785F * pull));
    }
}
