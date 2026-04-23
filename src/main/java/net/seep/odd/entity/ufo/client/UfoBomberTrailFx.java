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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.ufo.UfoBomberEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class UfoBomberTrailFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/ufo_bomber_trail.json");

    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();
    private static boolean inited = false;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uTrailStart;
    private static Uniform3f uTrailEnd;
    private static Uniform1f uWidth;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    private UfoBomberTrailFx() {}

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new UfoBomberTrailFx());
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (client != null && client.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos = shader.findUniform3f("CameraPosition");
        uTrailStart = shader.findUniform3f("TrailStart");
        uTrailEnd = shader.findUniform3f("TrailEnd");
        uWidth = shader.findUniform1f("Width");
        uIntensity = shader.findUniform1f("Intensity");
        uTime = shader.findUniform1f("iTime");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || client.player == null) return;

        ensureInit();
        if (shader == null) return;

        Box range = client.player.getBoundingBox().expand(256.0);

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((client.player.age + tickDelta) / 20.0f);

        // Slightly thinner overall.
        uWidth.set(0.22f);

        for (UfoBomberEntity bomber : client.world.getEntitiesByClass(UfoBomberEntity.class, range, e -> e.isAlive() && !e.isRemoved())) {
            // Compute directly from the bomber every frame so multiple bombers always work.
            Vec3d leftStart  = bomber.bomberLocalToWorld(new Vec3d(-1.90, 0.08, -0.20));
            Vec3d leftEnd    = bomber.bomberLocalToWorld(new Vec3d(-1.90, 0.08, -16.80));
            Vec3d rightStart = bomber.bomberLocalToWorld(new Vec3d( 1.90, 0.08, -0.20));
            Vec3d rightEnd   = bomber.bomberLocalToWorld(new Vec3d( 1.90, 0.08, -16.80));

            double distToPlayer = bomber.distanceTo(client.player);
            float speedGlow = (float) Math.min(1.0, bomber.getVelocity().length() / 1.65);

            // A bit lighter normally...
            float baseIntensity = 0.52f + speedGlow * 0.24f;

            // ...but easier to see from far away up to ~120 blocks.
            float rangeBoost = 1.0f + 0.35f * MathHelper.clamp((float) ((distToPlayer - 40.0) / 80.0), 0.0f, 1.0f);

            float intensity = baseIntensity * rangeBoost;

            uIntensity.set(intensity);

            uTrailStart.set((float) leftStart.x, (float) leftStart.y, (float) leftStart.z);
            uTrailEnd.set((float) leftEnd.x, (float) leftEnd.y, (float) leftEnd.z);
            shader.render(tickDelta);

            uTrailStart.set((float) rightStart.x, (float) rightStart.y, (float) rightStart.z);
            uTrailEnd.set((float) rightEnd.x, (float) rightEnd.y, (float) rightEnd.z);
            shader.render(tickDelta);
        }
    }
}