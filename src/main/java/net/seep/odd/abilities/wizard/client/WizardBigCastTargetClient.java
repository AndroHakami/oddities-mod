package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.wizard.WizardCasting;
import net.seep.odd.abilities.wizard.WizardElement;
import net.seep.odd.item.ModItems;

@Environment(EnvType.CLIENT)
public final class WizardBigCastTargetClient {
    private WizardBigCastTargetClient() {}

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        boolean holdingStick = mc.player.getMainHandStack().isOf(ModItems.WALKING_STICK);
        boolean using = mc.player.isUsingItem() && holdingStick;

        if (!using) {
            WizardSummonCircleClient.setActive(false, null, 0f, 0f, WizardSummonCircleClient.Style.BIG_CYAN);
            return;
        }

        Vec3d at = WizardTargetingClient.getCirclePos(mc);

        WizardElement e = WizardClientState.getElementSafe();
        int needed = WizardCasting.chargeTicksFor(e);
        int used = mc.player.getItemUseTime();
        float t = (needed <= 1) ? 1f : (used / (float) needed);
        t = MathHelper.clamp(t, 0f, 1f);

        if (at != null) {
            // radius + "grow" are purely visual; tweak as you want
            float baseRadius = 2.2f;
            float grow = 1.0f + t * 0.9f;
            WizardSummonCircleClient.setActive(true, at, baseRadius, grow, WizardSummonCircleClient.Style.BIG_CYAN);
        } else {
            WizardSummonCircleClient.setActive(false, null, 0f, 0f, WizardSummonCircleClient.Style.BIG_CYAN);
        }

        // ✅ AUTO RELEASE when fully charged (no infinite holding)
        if (used >= needed) {
            mc.player.stopUsingItem();
        }
    }
}
