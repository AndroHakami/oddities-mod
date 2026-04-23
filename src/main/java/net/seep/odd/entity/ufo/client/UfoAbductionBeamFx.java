package net.seep.odd.entity.ufo.client;

import ladysnake.satin.api.event.PostWorldRenderCallback;
import ladysnake.satin.api.experimental.ReadableDepthFramebuffer;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import ladysnake.satin.api.managed.uniform.Uniform1f;
import ladysnake.satin.api.managed.uniform.Uniform3f;
import ladysnake.satin.api.managed.uniform.UniformMat4;
import ladysnake.satin.api.util.GlMatrices;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.entity.ufo.UfoSaucerEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class UfoAbductionBeamFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/ufo_abduction_beam.json");

    private static boolean inited = false;
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uBeamOrigin;
    private static Uniform1f uBottomY;
    private static Uniform1f uBeamLength;
    private static Uniform1f uTopRadius;
    private static Uniform1f uBottomRadius;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private UfoAbductionBeamFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new UfoAbductionBeamFx());
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");
        uBeamOrigin   = shader.findUniform3f("BeamOrigin");
        uBottomY      = shader.findUniform1f("BottomY");
        uBeamLength   = shader.findUniform1f("BeamLength");
        uTopRadius    = shader.findUniform1f("TopRadius");
        uBottomRadius = shader.findUniform1f("BottomRadius");
        uTime         = shader.findUniform1f("iTime");
        uIntensity    = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || client.player == null) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = client.world;
        Box range = client.player.getBoundingBox().expand(192.0);

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

        float time = ((client.player != null ? client.player.age : 0) + tickDelta) / 20.0f;
        uTime.set(time);

        for (UfoSaucerEntity saucer : world.getEntitiesByClass(UfoSaucerEntity.class, range, e -> e.isAlive() && e.isTractorBeamActive())) {
            float originX = (float) saucer.getX();
            float originY = (float) (saucer.getY() - 0.65);
            float originZ = (float) saucer.getZ();

            int groundY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    MathHelper.floor(saucer.getX()), MathHelper.floor(saucer.getZ()));

            float bottomY = groundY + 0.05f;
            float beamLength = Math.max(2.5f, originY - bottomY);
            float pulse = 0.88f + 0.12f * MathHelper.sin((saucer.age + tickDelta) * 0.35f);

            uBeamOrigin.set(originX, originY, originZ);
            uBottomY.set(bottomY);
            uBeamLength.set(beamLength);
            uTopRadius.set(0.95f);
            uBottomRadius.set(Math.min(4.35f, 2.6f + beamLength * 0.18f));
            uIntensity.set(pulse);

            shader.render(tickDelta);
        }
    }
}