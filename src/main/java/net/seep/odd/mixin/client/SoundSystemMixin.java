// FILE: src/main/java/net/seep/odd/mixin/client/SoundSystemMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.client.audio.DistantIslesMusicVolume;
import net.seep.odd.client.audio.DistantIslesRoutedSound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Shadow @Final private GameOptions settings;

    @Inject(
            method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void odd$useDistantIslesBus(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!(sound instanceof DistantIslesRoutedSound)) return;

        float base = MathHelper.clamp(sound.getVolume(), 0.0f, 1.0f);
        float master = MathHelper.clamp(this.settings.getSoundVolume(SoundCategory.MASTER), 0.0f, 1.0f);
        float custom = DistantIslesMusicVolume.getFloat();

        cir.setReturnValue(MathHelper.clamp(base * master * custom, 0.0f, 1.0f));
    }
}