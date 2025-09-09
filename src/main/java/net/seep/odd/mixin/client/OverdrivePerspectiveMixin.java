
package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.seep.odd.abilities.client.ClientPowerHolder;
import net.seep.odd.abilities.overdrive.client.OverdriveCpmBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameOptions.class)
public class OverdrivePerspectiveMixin {
    @Inject(method = "getPerspective", at = @At("HEAD"), cancellable = true)
    private void odd$forceThirdPersonWhileOverdrive(CallbackInfoReturnable<Perspective> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        // Only when Overdrive is the active power
        if (!"overdrive".equals(ClientPowerHolder.get())) return;

        // Flip to third-person ONLY while charging or during punch
        if (OverdriveCpmBridge.isCharging() || OverdriveCpmBridge.isPunching()) {
            cir.setReturnValue(Perspective.THIRD_PERSON_BACK);
        }
    }
}
