package net.seep.odd.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import net.seep.odd.mixin.client.accessor.LivingEntityRendererAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class VacuumAimPoseMixin {

    private static final float BASE_AIM_PITCH_RAD = -1.20f; // arm forward
    private static final float SUPPORT_FACTOR     = 0.60f;  // offhand mirror

    @Inject(
            method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void odd$aimVacuum(AbstractClientPlayerEntity player,
                               float entityYaw, float tickDelta,
                               MatrixStack matrices,
                               VertexConsumerProvider consumers,
                               int light,
                               CallbackInfo ci) {

        if (!player.isUsingItem()) return;
        ItemStack active = player.getActiveItem();
        if (!(active.getItem() instanceof ArtificerVacuumItem)) return;

        // Read the model via accessor and cast to PlayerEntityModel
        PlayerEntityModel<AbstractClientPlayerEntity> m =
                (PlayerEntityModel<AbstractClientPlayerEntity>)
                        (Object) ((LivingEntityRendererAccessor) this).odd$getModel();

        // Track camera (head) instead of torso
        float yawRad   = (float) Math.toRadians(player.headYaw - player.bodyYaw);
        float pitchRad = (float) Math.toRadians(player.getPitch());

        Hand activeHand = player.getActiveHand();
        Arm  mainArm    = player.getMainArm();
        Arm  armUsing   = (activeHand == Hand.MAIN_HAND) ? mainArm
                : (mainArm == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT);

        if (armUsing == Arm.RIGHT) {
            m.rightArm.yaw   += yawRad;
            m.rightArm.pitch  = BASE_AIM_PITCH_RAD - pitchRad;

            m.leftArm.pitch   = m.rightArm.pitch * SUPPORT_FACTOR;
            m.leftArm.yaw     = m.rightArm.yaw   * SUPPORT_FACTOR;
        } else {
            m.leftArm.yaw    += yawRad;
            m.leftArm.pitch   = BASE_AIM_PITCH_RAD - pitchRad;

            m.rightArm.pitch  = m.leftArm.pitch * SUPPORT_FACTOR;
            m.rightArm.yaw    = m.leftArm.yaw   * SUPPORT_FACTOR;
        }
    }
}
