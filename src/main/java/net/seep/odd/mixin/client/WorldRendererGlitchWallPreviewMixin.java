// src/main/java/net/seep/odd/mixin/client/WorldRendererGlitchWallPreviewMixin.java
package net.seep.odd.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.power.GlitchPower;
import net.seep.odd.block.ModBlocks;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class WorldRendererGlitchWallPreviewMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void odd$renderGlitchWallPreview(MatrixStack matrices, float tickDelta, long limitTime,
                                             boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                             LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix,
                                             CallbackInfo ci) {

        if (!GlitchPower.Client.isWallAimActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        int dist = MathHelper.clamp(GlitchPower.Client.wallAimDistanceBlocks(), 1, 16);

        int yawSteps = GlitchPower.Client.wallAimYawSteps();
        int pitchSteps = GlitchPower.Client.wallAimPitchSteps();

        // center = camera + look * dist
        Vec3d origin = mc.player.getCameraPosVec(tickDelta);
        Vec3d look = mc.player.getRotationVec(tickDelta);
        Vec3d centerVec = origin.add(look.multiply(dist));
        BlockPos center = BlockPos.ofFloored(centerVec);

        float yawDeg = mc.player.getYaw() + yawSteps * 45.0f;
        float pitchDeg = pitchSteps * 45.0f;

        // Build oriented axes exactly like server
        double yawRad = Math.toRadians(yawDeg);
        Vec3d normal = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad)).normalize();
        Vec3d right = new Vec3d(-normal.z, 0.0, normal.x).normalize();

        double pitchRad = Math.toRadians(pitchDeg);
        Vec3d tiltedNormal = rotateAroundAxis(normal, right, pitchRad).normalize();
        Vec3d up = tiltedNormal.crossProduct(right).normalize();

        // base at block center
        Vec3d base = new Vec3d(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);

        var state = ModBlocks.GLITCH_BLOCK.getDefaultState();
        var brm = mc.getBlockRenderManager();
        VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();

        Vec3d cam = camera.getPos();

        // half-alpha ghost
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.5f);

        int r = 4;
        int r2 = r * r;

        HashSet<Long> seen = new HashSet<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                if ((dx * dx + dy * dy) > r2) continue;

                Vec3d pos = base.add(right.multiply(dx)).add(up.multiply(dy));
                BlockPos p = BlockPos.ofFloored(pos);

                if (!seen.add(p.asLong())) continue;

                matrices.push();
                matrices.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);

                int light = WorldRenderer.getLightmapCoordinates(mc.world, state, p);
                brm.renderBlockAsEntity(state, matrices, vcp, light, OverlayTexture.DEFAULT_UV);

                matrices.pop();
            }
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        vcp.draw();
    }

    private static Vec3d rotateAroundAxis(Vec3d v, Vec3d axisUnit, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        Vec3d a = axisUnit;
        Vec3d term1 = v.multiply(cos);
        Vec3d term2 = a.crossProduct(v).multiply(sin);
        Vec3d term3 = a.multiply(a.dotProduct(v) * (1.0 - cos));
        return term1.add(term2).add(term3);
    }
}
