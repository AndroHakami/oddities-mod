// FILE: src/main/java/net/seep/odd/mixin/client/SoundOptionsScreenMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.seep.odd.client.audio.DistantIslesMusicVolume;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {
    @Inject(method = "getVolumeOptions", at = @At("RETURN"), cancellable = true)
    private void odd$addDistantIslesSlider(CallbackInfoReturnable<SimpleOption<?>[]> cir) {
        SimpleOption<?>[] original = cir.getReturnValue();
        SimpleOption<?>[] extended = Arrays.copyOf(original, original.length + 1);
        extended[extended.length - 1] = DistantIslesMusicVolume.createOption();
        cir.setReturnValue(extended);
    }
}