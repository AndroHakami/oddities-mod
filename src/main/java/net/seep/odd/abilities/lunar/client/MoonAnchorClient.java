package net.seep.odd.abilities.lunar.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.Oddities;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class MoonAnchorClient {
    private MoonAnchorClient() {}

    private static final Identifier ICON = new Identifier(Oddities.MOD_ID, "textures/gui/hud/moon_icon.png");

    private static BlockPos anchorPos = null;
    private static int anchorEntityId = -1; // -1 => none
    private static boolean registered = false;

    /** Register the world-render hook once. Call from client init. */
    public static void init() {
        if (registered) return;
        registered = true;
        WorldRenderEvents.AFTER_ENTITIES.register(MoonAnchorClient::render);
    }

    /* ================= API used by packets ================= */

    /** Show a marker above this block position (clears entity anchor). */
    public static void set(BlockPos pos) {
        anchorEntityId = -1;
        anchorPos = pos == null ? null : pos.toImmutable();
    }

    /** Show a marker above the entity with this id (clears block anchor). */
    public static void setEntity(int id) {
        anchorPos = null;
        anchorEntityId = id;
    }

    /** Remove any active marker. */
    public static void clear() {
        anchorEntityId = -1;
        anchorPos = null;
    }

    /* ---- Back-compat aliases (safe to remove later) ---- */
    @Deprecated public static void setAnchorPos(BlockPos pos) { set(pos); }
    @Deprecated public static void setAnchorEntity(int id)    { setEntity(id); }

    /* ================= Rendering ================= */

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        if (anchorPos == null && anchorEntityId < 0) return;

        // Determine anchor world position
        Vec3d pos;
        if (anchorEntityId >= 0) {
            Entity e = mc.world.getEntityById(anchorEntityId);
            if (e == null) return;
            pos = e.getPos().add(0.0, e.getHeight() + 0.6, 0.0);
        } else {
            pos = Vec3d.ofCenter(anchorPos).add(0.0, 1.2, 0.0);
        }

        // Camera-relative transform
        Vec3d cam = ctx.camera().getPos();
        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        // billboard facing camera
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-ctx.camera().getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ctx.camera().getPitch()));
        // world size of the icon
        ms.scale(0.75f, 0.75f, 0.75f);

        // Make sure it renders through walls
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        VertexConsumerProvider.Immediate buf = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = buf.getBuffer(RenderLayer.getEntityTranslucent(ICON));
        Matrix4f mat = ms.peek().getPositionMatrix();
        Matrix3f nrm = ms.peek().getNormalMatrix();

        // Draw a unit quad centered at origin (UVs cover the whole icon)
        add(vc, mat, nrm, -0.5f, -0.5f, 0f, 0f, 1f);
        add(vc, mat, nrm,  0.5f, -0.5f, 0f, 1f, 1f);
        add(vc, mat, nrm,  0.5f,  0.5f, 0f, 1f, 0f);
        add(vc, mat, nrm, -0.5f,  0.5f, 0f, 0f, 0f);

        buf.draw();

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        ms.pop();
    }

    private static void add(VertexConsumer vc, Matrix4f m, Matrix3f n,
                            float x, float y, float z, float u, float v) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(n, 0f, 1f, 0f)
                .next();
    }
}
