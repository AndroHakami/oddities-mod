package net.seep.odd.entity.bosswitch.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class BossWitchHexZoneClient implements PostWorldRenderCallback {
    private BossWitchHexZoneClient() {}

    private static final Identifier POST_ID = new Identifier("odd", "shaders/post/boss_witch_hex_zones.json");
    private static final int FLAME_TICKS = 9;
    private static final float TILE_SIZE = 1.0f;

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    private static final Matrix4f TMP_MAT = new Matrix4f();
    private static final Long2ObjectOpenHashMap<Wave> WAVES = new Long2ObjectOpenHashMap<>();

    private static boolean inited = false;
    private static long nextWaveId = 1L;

    private static ManagedShaderEffect shader;
    private static UniformMat4 uInvTransform;
    private static Uniform3f uCameraPos;
    private static Uniform3f uArenaCenter;
    private static Uniform1f uArenaRadius;
    private static Uniform1f uHazardParity;
    private static Uniform1f uTileSize;
    private static Uniform1f uCharge01;
    private static Uniform1f uFlame01;
    private static Uniform1f uIntensity;
    private static Uniform1f uTime;

    private record Wave(Vec3d center, double radius, int hazardParity, long startTick, int chargeTicks, int flameTicks) {
        int totalTicks() {
            return this.chargeTicks + this.flameTicks;
        }
    }

    public static void init() {
        if (inited) return;
        inited = true;

        PostWorldRenderCallback.EVENT.register(new BossWitchHexZoneClient());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null || WAVES.isEmpty()) return;
            long now = client.world.getTime();
            var it = WAVES.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var entry = it.next();
                Wave wave = entry.getValue();
                if ((now - wave.startTick()) >= wave.totalTicks()) {
                    it.remove();
                }
            }
        });
    }

    public static void spawnWave(int entityId, Vec3d center, double radius, int hazardParity, int chargeTicks) {
        init();
        if (CLIENT == null || CLIENT.world == null) return;

        Vec3d groundedCenter = groundCenter(CLIENT.world, center);

        long id = (nextWaveId++ << 10) ^ (entityId & 1023L);
        WAVES.put(id, new Wave(groundedCenter, radius, hazardParity, CLIENT.world.getTime(), chargeTicks, FLAME_TICKS));
    }

    private static Vec3d groundCenter(ClientWorld world, Vec3d center) {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        int x = MathHelper.floor(center.x);
        int z = MathHelper.floor(center.z);

        int preferredTop = Math.min(world.getTopY() - 1, MathHelper.floor(center.y + 8.0D));
        int preferredBottom = Math.max(world.getBottomY(), MathHelper.floor(center.y - 16.0D));

        for (int y = preferredTop; y >= preferredBottom; y--) {
            pos.set(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isSolidBlock(world, pos)) continue;

            BlockPos above = pos.up();
            if (world.getBlockState(above).isSolidBlock(world, above)) continue;

            return new Vec3d(center.x, y + 1.0D, center.z);
        }

        int top = world.getTopY() - 1;
        int bottom = world.getBottomY();

        for (int y = top; y >= bottom; y--) {
            pos.set(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isSolidBlock(world, pos)) continue;

            BlockPos above = pos.up();
            if (world.getBlockState(above).isSolidBlock(world, above)) continue;

            return new Vec3d(center.x, y + 1.0D, center.z);
        }

        return center;
    }

    private static void ensureInit() {
        if (shader != null) return;

        shader = ShaderEffectManager.getInstance().manage(POST_ID, s -> {
            if (CLIENT != null && CLIENT.getFramebuffer() instanceof ReadableDepthFramebuffer rdf) {
                s.setSamplerUniform("DepthSampler", rdf.getStillDepthMap());
            }
        });

        uInvTransform = shader.findUniformMat4("InverseTransformMatrix");
        uCameraPos    = shader.findUniform3f("CameraPosition");
        uArenaCenter  = shader.findUniform3f("ArenaCenter");
        uArenaRadius  = shader.findUniform1f("ArenaRadius");
        uHazardParity = shader.findUniform1f("HazardParity");
        uTileSize     = shader.findUniform1f("TileSize");
        uCharge01     = shader.findUniform1f("Charge01");
        uFlame01      = shader.findUniform1f("Flame01");
        uIntensity    = shader.findUniform1f("Intensity");
        uTime         = shader.findUniform1f("iTime");
    }

    @Override
    public void onWorldRendered(Camera camera, float tickDelta, long limitTime) {
        if (CLIENT == null || CLIENT.world == null || WAVES.isEmpty()) return;

        ensureInit();
        if (shader == null) return;

        ClientWorld world = CLIENT.world;
        long now = world.getTime();
        Vec3d camPos = camera.getPos();

        uInvTransform.set(GlMatrices.getInverseTransformMatrix(TMP_MAT));
        uCameraPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        uTileSize.set(TILE_SIZE);
        uTime.set(((CLIENT.player != null ? CLIENT.player.age : 0) + tickDelta) / 20.0f);

        var it = WAVES.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            Wave wave = entry.getValue();

            float ageTicks = (float) ((now - wave.startTick()) + tickDelta);
            float charge01 = MathHelper.clamp(ageTicks / (float) wave.chargeTicks(), 0.0f, 1.0f);
            float flame01 = MathHelper.clamp((ageTicks - wave.chargeTicks()) / (float) wave.flameTicks(), 0.0f, 1.0f);
            float total01 = MathHelper.clamp(ageTicks / (float) wave.totalTicks(), 0.0f, 1.0f);

            if (total01 >= 1.0f) {
                it.remove();
                continue;
            }

            float chargeFade = smoothstep(0.00f, 0.20f, charge01);
            float flameFadeIn = smoothstep(0.00f, 0.16f, flame01);
            float flameFadeOut = smoothstep(0.00f, 0.45f, 1.0f - flame01);
            float intensity = Math.max(chargeFade * (1.0f - flame01 * 0.55f), flameFadeIn * flameFadeOut);

            uArenaCenter.set((float) wave.center().x, (float) wave.center().y, (float) wave.center().z);
            uArenaRadius.set((float) wave.radius());
            uHazardParity.set((float) wave.hazardParity());
            uCharge01.set(charge01);
            uFlame01.set(flame01);
            uIntensity.set(intensity);

            shader.render(tickDelta);
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}