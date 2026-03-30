// FILE: src/main/java/net/seep/odd/item/client/TrumpetAxeHornPose.java
package net.seep.odd.item.custom.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.item.custom.TrumpetAxeItem;

@Environment(EnvType.CLIENT)
public final class TrumpetAxeHornPose {
    private TrumpetAxeHornPose() {}

    public static boolean shouldApply(LivingEntity entity) {
        return entity != null
                && entity.isUsingItem()
                && entity.getActiveItem().getItem() instanceof TrumpetAxeItem;
    }

    public static void apply(BipedEntityModel<? extends LivingEntity> model, LivingEntity entity) {
        if (!shouldApply(entity)) {
            return;
        }

        Arm usingArm = getUsingArm(entity);
        ModelPart useArm = usingArm == Arm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart otherArm = usingArm == Arm.RIGHT ? model.leftArm : model.rightArm;

        float clampedHeadPitch = MathHelper.clamp(model.head.pitch, -0.5235988F, 1.5707964F);

        // active arm: close to goat-horn style, but tuned a bit to fit the trumpet axe
        useArm.pitch = useArm.pitch * 0.5F - 1.4835298F - clampedHeadPitch;
        useArm.yaw = (usingArm == Arm.RIGHT ? -0.2617994F : 0.2617994F) + model.head.yaw;
        useArm.roll = usingArm == Arm.RIGHT ? 0.08F : -0.08F;

        // off arm: keep it calmer so it doesn't look broken
        otherArm.pitch = otherArm.pitch * 0.35F - 0.20F;
        otherArm.yaw = (usingArm == Arm.RIGHT ? 0.15F : -0.15F) + model.head.yaw * 0.15F;
        otherArm.roll = usingArm == Arm.RIGHT ? -0.05F : 0.05F;
    }

    private static Arm getUsingArm(LivingEntity entity) {
        if (entity.getActiveHand() == Hand.MAIN_HAND) {
            return entity.getMainArm();
        }

        return entity.getMainArm() == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT;
    }
}