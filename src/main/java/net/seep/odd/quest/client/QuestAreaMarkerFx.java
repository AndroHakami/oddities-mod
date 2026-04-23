package net.seep.odd.quest.client;

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
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.Oddities;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestObjectiveData;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class QuestAreaMarkerFx implements PostWorldRenderCallback {
    private static final Identifier POST_ID =
            new Identifier(Oddities.MOD_ID, "shaders/post/quest_area_marker.json");

    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final Matrix4f tmpMat = new Matrix4f();

    private static boolean inited;
    private static ManagedShaderEffect shader;

    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uCenter;
    private static Uniform1f uRadius;
    private static Uniform1f uTime;
    private static Uniform1f uIntensity;

    private static float cx;
    private static float cy;
    private static float cz;
    private static float radius = 4.2F;
    private static float strength = 0.0F;
    private static float target = 0.0F;

    private QuestAreaMarkerFx() {
    }

    public static void init() {
        if (inited) return;
        inited = true;
        PostWorldRenderCallback.EVENT.register(new QuestAreaMarkerFx());
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
        uCenter = shader.findUniform3f("Center");
        uRadius = shader.findUniform1f("Radius");
        uTime = shader.findUniform1f("iTime");
        uIntensity = shader.findUniform1f("Intensity");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (client == null || client.world == null || client.player == null) return;

        QuestClientState state = QuestClientState.INSTANCE;
        QuestDefinition def = state.activeQuestDefinition();

        if (def != null
                && (def.objective.type == QuestObjectiveData.Type.CALM_RETURN
                    || def.objective.type == QuestObjectiveData.Type.RACE_RETURN
                    || def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL
                    || def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL_2)
                && (state.activeStage() == QuestClientState.ActiveStage.TRAVEL || state.activeStage() == QuestClientState.ActiveStage.RETURN)
                && state.activeTarget() != null) {
            BlockPos pos = state.activeTarget();
            cx = pos.getX() + 0.5F;
            cy = pos.getY() + 0.03F;
            cz = pos.getZ() + 0.5F;
            radius = def.objective.type == QuestObjectiveData.Type.RACE_RETURN ? 5.0F : 4.0F;
            target = 1.0F;
        } else if (def != null && (def.objective.type == QuestObjectiveData.Type.FIND_HIM
                || def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL
                || def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL_2)) {
            Entity tracked = resolvePreferredTrackedEntity(state, def);
            if (tracked != null) {
                cx = (float) tracked.getX();
                cy = (float) tracked.getY() + 0.05F;
                cz = (float) tracked.getZ();
                radius = def.objective.type == QuestObjectiveData.Type.FIND_HIM ? 2.15F : 2.55F;
                target = 1.0F;
            } else if (state.activeTarget() != null) {
                BlockPos pos = state.activeTarget();
                cx = pos.getX() + 0.5F;
                cy = pos.getY() + 0.03F;
                cz = pos.getZ() + 0.5F;
                radius = def.objective.type == QuestObjectiveData.Type.FIND_HIM ? 2.8F : 4.0F;
                target = 1.0F;
            } else {
                target = 0.0F;
            }
        } else {
            target = 0.0F;
        }

        strength += (target - strength) * 0.16F;
        if (strength < 0.001F) return;

        ensureInit();
        if (shader == null) return;

        var camPos = camera.getPos();
        uInvTransform.set(GlMatrices.getInverseTransformMatrix(tmpMat));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uCenter.set(cx, cy, cz);
        uRadius.set(radius);
        uTime.set(((client.player.age) + tickDelta) / 20.0F);
        uIntensity.set(strength);

        shader.render(tickDelta);
    }

    private static Entity resolvePreferredTrackedEntity(QuestClientState state, QuestDefinition def) {
        if (client == null || client.world == null) return null;

        Entity fallback = null;
        for (int entityId : state.trackedEntityIds()) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            if (def.objective.type == QuestObjectiveData.Type.FIND_HIM) {
                return entity;
            }

            if (def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL) {
                if (entity instanceof net.seep.odd.entity.scared_rascal.ScaredRascalEntity) {
                    return entity;
                }
                if (fallback == null) {
                    fallback = entity;
                }
                continue;
            }

            if (def.objective.type == QuestObjectiveData.Type.SCARED_RASCAL_2) {
                if (entity instanceof net.seep.odd.entity.scared_rascal_fight.ScaredRascalFightEntity) {
                    return entity;
                }
                if (fallback == null) {
                    fallback = entity;
                }
            }
        }
        return fallback;
    }
}
