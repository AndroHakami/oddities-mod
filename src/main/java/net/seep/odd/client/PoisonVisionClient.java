package net.seep.odd.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import net.seep.odd.fluid.ModFluids;

public final class PoisonVisionClient {
    private static final Identifier POISON_UNDERWATER =
            new Identifier(Oddities.MOD_ID, "textures/misc/poison_underwater.png");

    private PoisonVisionClient() {}

    public static void register() {
        HudRenderCallback.EVENT.register(PoisonVisionClient::onHudRender);
    }

    private static void onHudRender(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!client.options.getPerspective().isFirstPerson()) return;
        if (!isCameraSubmergedInPoison(client.gameRenderer.getCamera(), client.world)) return;

        int w = drawContext.getScaledWindowWidth();
        int h = drawContext.getScaledWindowHeight();

        float t = (client.world.getTime() + tickDelta) * 0.07f;
        float alpha = 0.30f + 0.05f * MathHelper.sin(t);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        drawContext.drawTexture(
                POISON_UNDERWATER,
                0, 0,
                0.0f, 0.0f,
                w, h,
                256, 256
        );

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    public static boolean isCameraSubmergedInPoison(Camera camera, ClientWorld world) {
        if (camera == null || world == null || !camera.isReady()) return false;

        Vec3d camPos = camera.getPos();
        BlockPos pos = BlockPos.ofFloored(camPos);
        FluidState state = world.getFluidState(pos);

        if (!state.getFluid().matchesType(ModFluids.STILL_POISON)) {
            return false;
        }

        double surfaceY = pos.getY() + state.getHeight(world, pos);
        return camPos.y < surfaceY;
    }
}