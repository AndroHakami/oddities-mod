package net.seep.odd.entity.dragoness.client;

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
import net.seep.odd.entity.dragoness.DragonessAttackType;
import net.seep.odd.entity.dragoness.DragonessEntity;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class DragonessDashPathFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/dragoness_dash_path.json");
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP = new Matrix4f();

    private static boolean inited = false;
    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uPathStart;
    private static Uniform3f uPathEnd;
    private static Uniform1f uHalfWidth;
    private static Uniform1f uHeight;
    private static Uniform1f uAge01;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    private DragonessDashPathFx() {}

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new DragonessDashPathFx());
    }

    private static void ensureInit() {
        if (shader != null) return;
        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (CLIENT != null && CLIENT.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });
        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos = shader.findUniform3f("CameraPosition");
        uPathStart = shader.findUniform3f("PathStart");
        uPathEnd = shader.findUniform3f("PathEnd");
        uHalfWidth = shader.findUniform1f("HalfWidth");
        uHeight = shader.findUniform1f("Height");
        uAge01 = shader.findUniform1f("Age01");
        uIntensity = shader.findUniform1f("Intensity");
        uTime = shader.findUniform1f("iTime");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || CLIENT.player == null) return;
        ensureInit();
        if (shader == null) return;

        Vec3d camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTime.set((CLIENT.player.age + tickDelta) / 20.0f);

        Box range = CLIENT.player.getBoundingBox().expand(192.0D);
        for (DragonessEntity dragoness : CLIENT.world.getEntitiesByClass(DragonessEntity.class, range, e -> e.isAlive() && !e.isRemoved())) {
            DragonessAttackType type = dragoness.getAttackType();
            if (type != DragonessAttackType.SLIDE_DASH_STANCE && type != DragonessAttackType.COMBO_DASH_STANCE) continue;

            float denom = type == DragonessAttackType.SLIDE_DASH_STANCE
                    ? (float) DragonessEntity.SLIDE_DASH_STANCE_TOTAL_TICKS
                    : (float) DragonessEntity.COMBO_DASH_STANCE_TOTAL_TICKS;
            float age01 = MathHelper.clamp((dragoness.getAttackTicks() + tickDelta) / Math.max(1.0f, denom), 0.0f, 1.0f);

            Vec3d start = dragoness.getDashPathStart();
            Vec3d end = dragoness.getDashPathEnd();
            if (start.squaredDistanceTo(end) < 1.0E-6D) continue;

            uPathStart.set((float) start.x, (float) start.y, (float) start.z);
            uPathEnd.set((float) end.x, (float) end.y, (float) end.z);
            uHalfWidth.set(type == DragonessAttackType.SLIDE_DASH_STANCE ? 1.65f : 1.35f);
            uHeight.set(0.55f);
            uAge01.set(age01);
            uIntensity.set(0.92f);
            shader.render(tickDelta);
        }
    }
}
