package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import net.seep.odd.event.alien.client.AlienInvasionClientState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererNoCloudsMixin {

    @Inject(
            method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void odd$noClouds(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta,
                              double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;

        if (AlienInvasionClientState.active()) {
            ci.cancel();
        }
    }
}