// FILE: src/main/java/net/seep/odd/mixin/client/SniperFovMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;

import net.minecraft.item.ItemStack;
import net.seep.odd.abilities.sniper.client.SniperClientState;

import net.seep.odd.abilities.sniper.item.SniperItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class SniperFovMixin {

    // Yarn name is typically: getFov(Camera, float, boolean)
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void odd_sniperFov(net.minecraft.client.render.Camera camera, float tickDelta, boolean changingFov,
                               CallbackInfoReturnable<Double> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        if (!SniperClientState.isTargetScoped()) return;

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off  = mc.player.getOffHandStack();
        boolean holding = main.getItem() instanceof SniperItem || off.getItem() instanceof SniperItem;
        if (!holding) return;

        double fov = cir.getReturnValue();
        // 0.25 = strong zoom, change to 0.30..0.40 if you want less
        cir.setReturnValue(fov * 0.25);
    }
}
