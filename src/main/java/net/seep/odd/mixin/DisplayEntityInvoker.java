// src/main/java/net/seep/odd/mixin/DisplayEntityInvoker.java
package net.seep.odd.mixin;

import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.class)
public interface DisplayEntityInvoker {

    @Invoker("setTransformation")
    void odd$setTransformation(AffineTransformation transform);

    // 1.20.1 uses net.minecraft.entity.decoration.Brightness (NOT DisplayEntity.Brightness)
    @Invoker("setBrightness")
    void odd$setBrightness(@Nullable Brightness brightness);

    @Invoker("setViewRange")
    void odd$setViewRange(float range);

    @Invoker("setShadowRadius")
    void odd$setShadowRadius(float radius);

    @Invoker("setShadowStrength")
    void odd$setShadowStrength(float strength);

    @Invoker("setInterpolationDuration")
    void odd$setInterpolationDuration(int ticks);

    @Invoker("setDisplayWidth")
    void odd$setDisplayWidth(float width);

    @Invoker("setDisplayHeight")
    void odd$setDisplayHeight(float height);

    @Invoker("setGlowColorOverride")
    void odd$setGlowColorOverride(int glowColorOverride);
}
