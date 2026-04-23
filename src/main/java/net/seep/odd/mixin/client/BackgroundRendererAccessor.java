package net.seep.odd.mixin.client;

import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BackgroundRenderer.class)
public interface BackgroundRendererAccessor {
    @Accessor("red")
    static float odd$getRed() {
        throw new UnsupportedOperationException();
    }

    @Accessor("green")
    static float odd$getGreen() {
        throw new UnsupportedOperationException();
    }

    @Accessor("blue")
    static float odd$getBlue() {
        throw new UnsupportedOperationException();
    }

    @Accessor("red")
    static void odd$setRed(float value) {
        throw new UnsupportedOperationException();
    }

    @Accessor("green")
    static void odd$setGreen(float value) {
        throw new UnsupportedOperationException();
    }

    @Accessor("blue")
    static void odd$setBlue(float value) {
        throw new UnsupportedOperationException();
    }
}