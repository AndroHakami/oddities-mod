// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardComboTargetClient.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardCombo;
import net.seep.odd.item.ModItems;

@Environment(EnvType.CLIENT)
public final class WizardComboTargetClient {
    private WizardComboTargetClient() {}

    private static WizardCombo pending = null;

    // edge detect for left click (robust even if other code calls wasPressed())
    private static boolean prevAttackDown = false;

    public static boolean isTargeting() {
        return pending != null;
    }

    public static void begin(WizardCombo combo) {
        pending = combo;
        prevAttackDown = false;
    }

    public static void cancel() {
        pending = null;
        WizardSummonCircleClient.setActive(false, null, 0f, 0f, WizardSummonCircleClient.Style.COMBO_BLUE_GOLD);
        prevAttackDown = false;
    }

    public static void tick(MinecraftClient mc) {
        if (pending == null) return;
        if (mc.player == null || mc.world == null) { cancel(); return; }

        // don’t confirm while in any GUI
        if (mc.currentScreen != null) {
            prevAttackDown = mc.options.attackKey.isPressed();
            return;
        }

        boolean holdingStick = mc.player.getMainHandStack().isOf(ModItems.WALKING_STICK);
        if (!holdingStick) { cancel(); return; }

        Vec3d at = WizardTargetingClient.getCirclePos(mc);

        if (at != null) {
            WizardSummonCircleClient.setActive(true, at, 2.0f, 1.0f, WizardSummonCircleClient.Style.COMBO_BLUE_GOLD);
        } else {
            WizardSummonCircleClient.setActive(false, null, 0f, 0f, WizardSummonCircleClient.Style.COMBO_BLUE_GOLD);
        }

        // ✅ confirm with LEFT CLICK (edge detect)
        boolean attackDown = mc.options.attackKey.isPressed();
        boolean justPressed = attackDown && !prevAttackDown;

        if (justPressed) {
            if (at != null) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(pending.id);
                buf.writeDouble(at.x);
                buf.writeDouble(at.y);
                buf.writeDouble(at.z);

                ClientPlayNetworking.send(WizardPower.C2S_CAST_COMBO_AT, buf);
                cancel();
            }
        }

        prevAttackDown = attackDown;

        // optional cancel: sneak
        if (mc.options.sneakKey.isPressed()) {
            cancel();
        }
    }
}
