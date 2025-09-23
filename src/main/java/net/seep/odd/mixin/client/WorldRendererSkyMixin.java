package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import net.seep.odd.sky.CelestialEventClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;


@Mixin(WorldRenderer.class)
public abstract class WorldRendererSkyMixin {

    // setShaderTexture(0, SUN)
    @ModifyArg(
            method =
                    "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V",
                    ordinal = 0), // first occurrence = sun
            index = 1,
            remap = true
    )
    private Identifier odd$swapSun(Identifier original) {
        return CelestialEventClient.getSunTextureOr(original);
    }

    // setShaderTexture(0, MOON)
    @ModifyArg(
            method =
                    "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V",
                    ordinal = 1), // second = moon
            index = 1,
            remap = true
    )
    private Identifier odd$swapMoon(Identifier original) {
        return CelestialEventClient.getMoonTextureOr(original);
    }

    // Vanilla sun size constant = 30.0F
    @ModifyConstant(
            method =
                    "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            constant = @Constant(floatValue = 30.0F)
    )
    private float odd$scaleSun(float original) {
        return original * CelestialEventClient.sunScale();
    }

    // Vanilla moon size constant = 20.0F
    @ModifyConstant(
            method =
                    "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            constant = @Constant(floatValue = 20.0F)
    )
    private float odd$scaleMoon(float original) {
        return original * CelestialEventClient.moonScale();
    }
}
