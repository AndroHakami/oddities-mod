// FILE: src/main/java/net/seep/odd/mixin/client/GameRendererSniperFovMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import net.seep.odd.abilities.sniper.client.SniperClientState;
import net.seep.odd.abilities.sniper.item.SniperItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererSniperFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void odd_sniperFov(net.minecraft.client.render.Camera camera, float tickDelta, boolean changingFov,
                               CallbackInfoReturnable<Double> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off  = mc.player.getOffHandStack();
        boolean holding = main.getItem() instanceof SniperItem || off.getItem() instanceof SniperItem;
        if (!holding) return;

        float a = SniperClientState.scopeAmount(tickDelta); // 0..1
        if (a <= 0.001f) return;

        double baseFov = cir.getReturnValue();

        // Full zoom factor at a=1
        double zoomFactor = 0.25; // tweak if you want (0.30 = less zoom)
        double factor = 1.0 - (a * (1.0 - zoomFactor)); // smooth ramp
        cir.setReturnValue(baseFov * factor);
    }
}
