package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import net.seep.odd.event.alien.client.AlienInvasionClientState;
import net.seep.odd.event.alien.client.sky.AlienOverworldSkyCore;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererAlienSkyMixin {

    @Inject(
            method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At("TAIL")
    )
    private void odd$alienSky(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta,
                              Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;
        if (!AlienInvasionClientState.active()) return;

        ShaderProgram shader = AlienOverworldSkyCore.ALIEN_SKY;
        if (shader == null) return;

        float t = (float)((mc.world.getTime() + tickDelta) / 20.0);

        float prog  = AlienInvasionClientState.skyProgress01(tickDelta);
        float cubes = AlienInvasionClientState.cubes01(tickDelta);

        // inv projection from the SKY pass projection matrix
        Matrix4f invProj = new Matrix4f(projectionMatrix).invert(new Matrix4f());

        // ✅ IMPORTANT: inv view from the CURRENT sky-pass matrix stack (Atheneum-style)
        Matrix4f view = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix4f invView = view.invert(new Matrix4f());

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShader(() -> shader);

        setFloat(shader, "iTime", t);
        setFloat(shader, "Progress", prog);
        setFloat(shader, "CubeIntensity", cubes);
        setMat4(shader, "InvProjMat", invProj);
        setMat4(shader, "InvViewMat", invView);

        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        bb.vertex(-1f, -1f, 0f).next();
        bb.vertex( 1f, -1f, 0f).next();
        bb.vertex( 1f,  1f, 0f).next();
        bb.vertex(-1f,  1f, 0f).next();
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void setFloat(ShaderProgram p, String name, float v) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setMat4(ShaderProgram p, String name, Matrix4f m) {
        GlUniform u = p.getUniform(name);
        if (u != null) u.set(m);
    }
}