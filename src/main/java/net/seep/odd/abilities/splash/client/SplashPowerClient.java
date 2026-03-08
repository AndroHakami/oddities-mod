package net.seep.odd.abilities.splash.client;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.seep.odd.abilities.power.SplashPower;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class SplashPowerClient {

    private SplashPowerClient() {}

    private static boolean INITED = false;

    /* =========================
       World visuals state (everyone)
       ========================= */

    private static final class Visual {
        int modeOrd;
        boolean aura;
        boolean hose;
        long lastWorldTime;
    }

    private static final Map<UUID, Visual> VISUALS = new Object2ObjectOpenHashMap<>();

    /* =========================
       Owner-only state (resource + local loops + UI)
       ========================= */

    private static boolean gotState = false;
    private static long lastHudWorldTime = -1;

    private static SplashPower.Mode mode = SplashPower.Mode.LEAP;
    private static int resource = 1000;
    private static int resourceMax = 1000;
    private static boolean auraActive = false;
    private static boolean hoseActive = false;

    // mode-change sound gating
    private static int lastModeOrdForSfx = -1;

    /* =========================
       Textures
       ========================= */

    private static final Identifier WATER_TEX = new Identifier("minecraft", "textures/block/water_still.png");

    private static final Identifier HUD_BG      = new Identifier("odd", "textures/gui/splash_bg.png");
    private static final Identifier HUD_OVERLAY = new Identifier("odd", "textures/gui/splash_overlay.png");

    /* =========================
       Aura tuning (clean circle, slow smooth pulse)
       ========================= */

    // must match server aura radius / bubble edge
    private static final float AURA_RADIUS = 10.0f;

    // more segs = smoother circle
    private static final int SIGIL_SEGS = 144;

    /* =========================
       Looping sounds
       - Local: for the owner (always)
       - Remote: for nearby players (based on VISUALS)
       ========================= */

    private static final int FADE_IN_TICKS  = 8;
    private static final int FADE_OUT_TICKS = 10;

    private static FadingLoopSound bubblesLoop = null; // owner
    private static FadingLoopSound streamLoop  = null; // owner

    private static final Map<UUID, FadingLoopSound> REMOTE_BUBBLES = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, FadingLoopSound> REMOTE_STREAM  = new Object2ObjectOpenHashMap<>();

    public static void init() {
        if (INITED) return;
        INITED = true;

        // Owner-only state (HUD + owner loops)
        ClientPlayNetworking.registerGlobalReceiver(SplashPower.S2C_SPLASH_STATE, (client, handler, buf, responseSender) -> {
            UUID who = buf.readUuid();
            int modeOrd = buf.readVarInt();
            int res = buf.readVarInt();
            int max = buf.readVarInt();
            boolean aura = buf.readBoolean();
            boolean hose = buf.readBoolean();

            client.execute(() -> {
                if (client.player == null || client.world == null) return;
                if (!client.player.getUuid().equals(who)) return;

                // mode sfx (local-only)
                maybePlayModeSwitchSfx(client, modeOrd);

                SplashPower.Mode[] vals = SplashPower.Mode.values();
                if (modeOrd >= 0 && modeOrd < vals.length) mode = vals[modeOrd];

                resource = res;
                resourceMax = Math.max(1, max);

                boolean powerless = client.player.hasStatusEffect(ModStatusEffects.POWERLESS);
                auraActive = aura && !powerless;
                hoseActive = hose && !powerless;

                gotState = true;
                lastHudWorldTime = client.world.getTime();

                updateOwnerLoops(client);
            });
        });

        // Everyone visuals (beam render + remote hearing)
        ClientPlayNetworking.registerGlobalReceiver(SplashPower.S2C_SPLASH_VISUAL, (client, handler, buf, responseSender) -> {
            UUID owner = buf.readUuid();
            int modeOrd = buf.readVarInt();
            boolean aura = buf.readBoolean();
            boolean hose = buf.readBoolean();

            client.execute(() -> {
                if (client.world == null) return;
                Visual v = VISUALS.computeIfAbsent(owner, u -> new Visual());
                v.modeOrd = modeOrd;
                v.aura = aura;
                v.hose = hose;
                v.lastWorldTime = client.world.getTime();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VISUALS.clear();
            gotState = false;
            auraActive = false;
            hoseActive = false;
            stopAllLoopsHard(client);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            VISUALS.clear();
            gotState = false;
            auraActive = false;
            hoseActive = false;
            stopAllLoopsHard(client);
        });

        HudRenderCallback.EVENT.register(SplashPowerClient::renderHud);
        WorldRenderEvents.AFTER_ENTITIES.register(SplashPowerClient::renderWorld);
    }

    /* =========================
       Color helpers
       ========================= */

    private static int modeColor(SplashPower.Mode m) {
        return switch (m) {
            case LEAP -> 0x2EF46A;
            case TONGUE -> 0x42F5F5;
            case SKIN -> 0xFF66CC;
        };
    }

    private static int modeColorByOrd(int ord) {
        SplashPower.Mode[] vals = SplashPower.Mode.values();
        if (ord < 0 || ord >= vals.length) return 0x42F5F5;
        return modeColor(vals[ord]);
    }

    private static int beamCycleColor(long worldTime, float tickDelta) {
        float t = (worldTime + tickDelta);
        int idx = (int) (Math.floor(t / 6.0f) % 3);
        return switch (idx) {
            case 0 -> 0x42F5F5;
            case 1 -> 0xFF66CC;
            default -> 0x2EF46A;
        };
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    /* =========================
       Mode switch SFX (LOCAL ONLY)
       ========================= */

    private static void maybePlayModeSwitchSfx(MinecraftClient mc, int newModeOrd) {
        if (newModeOrd < 0 || newModeOrd >= SplashPower.Mode.values().length) return;

        if (lastModeOrdForSfx == -1) {
            lastModeOrdForSfx = newModeOrd; // first sync: no sound
            return;
        }

        if (newModeOrd == lastModeOrdForSfx) return;
        lastModeOrdForSfx = newModeOrd;

        float pitch = switch (newModeOrd) {
            case 0 -> 1.75f; // LEAP
            case 1 -> 1.10f; // TONGUE
            default -> 0.50f; // SKIN
        };

        // ✅ local-only (does not broadcast)
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, pitch, 3.2f));
    }

    /* =========================
       Owner loops (local player)
       ========================= */

    private static void updateOwnerLoops(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        if (bubblesLoop != null && bubblesLoop.isFinished()) bubblesLoop = null;
        if (streamLoop != null && streamLoop.isFinished()) streamLoop = null;

        boolean powerless = mc.player.hasStatusEffect(ModStatusEffects.POWERLESS);
        boolean wantBubbles = auraActive && !powerless;
        boolean wantStream  = hoseActive && !powerless;

        if (wantBubbles) {
            if (bubblesLoop == null) {
                float pitch = 0.97f + mc.world.random.nextFloat() * 0.08f;
                long seed = mc.world.random.nextLong();
                bubblesLoop = new FadingLoopSound(ModSounds.BUBBLES, SoundCategory.PLAYERS, mc.player,
                        0.95f, pitch, seed, FADE_IN_TICKS, FADE_OUT_TICKS);
                mc.getSoundManager().play(bubblesLoop);
            }
            bubblesLoop.setDesired(true);
        } else {
            if (bubblesLoop != null) bubblesLoop.setDesired(false);
        }

        if (wantStream) {
            if (streamLoop == null) {
                float pitch = 0.95f + mc.world.random.nextFloat() * 0.10f;
                long seed = mc.world.random.nextLong();
                streamLoop = new FadingLoopSound(ModSounds.STREAM, SoundCategory.PLAYERS, mc.player,
                        0.95f, pitch, seed, FADE_IN_TICKS, FADE_OUT_TICKS);
                mc.getSoundManager().play(streamLoop);
            }
            streamLoop.setDesired(true);
        } else {
            if (streamLoop != null) streamLoop.setDesired(false);
        }
    }

    /* =========================
       Remote loops (nearby players)
       ========================= */

    private static void updateRemoteLoops(MinecraftClient mc, ClientWorld world) {
        if (mc.player == null || world == null) return;

        UUID self = mc.player.getUuid();

        // Create/refresh desired loops for remote players
        for (Map.Entry<UUID, Visual> en : VISUALS.entrySet()) {
            UUID id = en.getKey();
            if (id.equals(self)) continue;

            PlayerEntity pe = findPlayer(world, id);
            if (pe == null) continue;

            Visual v = en.getValue();
            boolean powerless = pe.hasStatusEffect(ModStatusEffects.POWERLESS);

            // remote bubbles
            if (v.aura && !powerless) {
                FadingLoopSound s = REMOTE_BUBBLES.get(id);
                if (s == null || s.isFinished()) {
                    float pitch = 0.98f + world.random.nextFloat() * 0.06f;
                    long seed = world.random.nextLong();
                    s = new FadingLoopSound(ModSounds.BUBBLES, SoundCategory.PLAYERS, pe,
                            0.55f, pitch, seed, FADE_IN_TICKS, FADE_OUT_TICKS);
                    REMOTE_BUBBLES.put(id, s);
                    mc.getSoundManager().play(s);
                }
                s.setDesired(true);
            }

            // remote stream
            if (v.hose && !powerless) {
                FadingLoopSound s = REMOTE_STREAM.get(id);
                if (s == null || s.isFinished()) {
                    float pitch = 0.95f + world.random.nextFloat() * 0.08f;
                    long seed = world.random.nextLong();
                    s = new FadingLoopSound(ModSounds.STREAM, SoundCategory.PLAYERS, pe,
                            0.60f, pitch, seed, FADE_IN_TICKS, FADE_OUT_TICKS);
                    REMOTE_STREAM.put(id, s);
                    mc.getSoundManager().play(s);
                }
                s.setDesired(true);
            }
        }

        // Fade-out loops that are no longer desired
        for (var it = REMOTE_BUBBLES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            UUID id = e.getKey();
            FadingLoopSound s = e.getValue();

            Visual v = VISUALS.get(id);
            PlayerEntity pe = findPlayer(world, id);
            boolean desired = (v != null && pe != null && !id.equals(self) && v.aura && !pe.hasStatusEffect(ModStatusEffects.POWERLESS));

            if (!desired) s.setDesired(false);
            if (s.isFinished()) it.remove();
        }

        for (var it = REMOTE_STREAM.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            UUID id = e.getKey();
            FadingLoopSound s = e.getValue();

            Visual v = VISUALS.get(id);
            PlayerEntity pe = findPlayer(world, id);
            boolean desired = (v != null && pe != null && !id.equals(self) && v.hose && !pe.hasStatusEffect(ModStatusEffects.POWERLESS));

            if (!desired) s.setDesired(false);
            if (s.isFinished()) it.remove();
        }
    }

    private static void stopAllLoopsHard(MinecraftClient mc) {
        if (mc == null) return;

        if (bubblesLoop != null) {
            mc.getSoundManager().stop(bubblesLoop);
            bubblesLoop = null;
        }
        if (streamLoop != null) {
            mc.getSoundManager().stop(streamLoop);
            streamLoop = null;
        }

        for (FadingLoopSound s : REMOTE_BUBBLES.values()) mc.getSoundManager().stop(s);
        for (FadingLoopSound s : REMOTE_STREAM.values()) mc.getSoundManager().stop(s);
        REMOTE_BUBBLES.clear();
        REMOTE_STREAM.clear();
    }

    /**
     * Entity-following loop with fade in/out.
     * Start volume must not be 0 for some stream sounds.
     */
    private static final class FadingLoopSound extends EntityTrackingSoundInstance {
        private final Entity tracked;
        private final float baseVolume;
        private final int fadeInTicks;
        private final int fadeOutTicks;

        private float curVol;
        private boolean desired = true;
        private boolean finished = false;

        public FadingLoopSound(SoundEvent sound, SoundCategory category, Entity entity,
                               float baseVolume, float pitch, long seed,
                               int fadeInTicks, int fadeOutTicks) {
            super(sound, category, 0.001f, pitch, entity, seed);
            this.tracked = entity;
            this.baseVolume = baseVolume;
            this.fadeInTicks = Math.max(1, fadeInTicks);
            this.fadeOutTicks = Math.max(1, fadeOutTicks);
            this.curVol = Math.min(0.08f, baseVolume);
            this.volume = this.curVol;

            this.repeat = true;
            this.repeatDelay = 0;
            this.attenuationType = SoundInstance.AttenuationType.LINEAR;
            this.relative = false;
        }

        public void setDesired(boolean on) { this.desired = on; }
        public boolean isFinished() { return finished; }

        @Override
        public void tick() {
            super.tick();
            if (finished) return;

            if (tracked == null || tracked.isRemoved()) {
                this.volume = 0f;
                this.setDone();
                finished = true;
                return;
            }

            float stepIn  = baseVolume / (float) fadeInTicks;
            float stepOut = baseVolume / (float) fadeOutTicks;

            if (desired) curVol = Math.min(baseVolume, curVol + stepIn);
            else         curVol = Math.max(0f, curVol - stepOut);

            this.volume = curVol;

            if (!desired && curVol <= 0.0005f) {
                this.volume = 0f;
                this.setDone();
                finished = true;
            }
        }
    }

    /* =========================
       HUD (circular) — ABOVE text, RIGHT
       ========================= */

    private static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!gotState) return;

        long now = mc.world.getTime();
        long age = (lastHudWorldTime < 0) ? 9999 : (now - lastHudWorldTime);

        if (age > 60) {
            gotState = false;
            auraActive = false;
            hoseActive = false;
            updateOwnerLoops(mc);
            return;
        }

        if (mc.player.hasStatusEffect(ModStatusEffects.POWERLESS)) {
            auraActive = false;
            hoseActive = false;
        }

        updateOwnerLoops(mc);

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        final int size = 64;
        final int pad = 8;

        final int x = w - size - pad;
        final int y = h - size - 32;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 200);

        ctx.drawTexture(HUD_BG, x, y, 0, 0, size, size, size, size);

        float pct = resourceMax <= 0 ? 0f : (resource / (float) resourceMax);
        pct = MathHelper.clamp(pct, 0f, 1f);

        int rgb = modeColor(mode);
        int alpha = (auraActive || hoseActive) ? 200 : 160;

        float pulse = 0.08f * MathHelper.sin((now + tickDelta) * 0.12f);
        float inner = size * (0.24f + pulse * 0.02f);
        float outer = size * (0.46f + pulse * 0.03f);

        drawRing2D(ctx, x + size / 2f, y + size / 2f, inner, outer, pct, alpha, rgb);

        ctx.drawTexture(HUD_OVERLAY, x, y, 0, 0, size, size, size, size);

        ctx.getMatrices().pop();
    }

    private static void drawRing2D(DrawContext ctx, float cx, float cy,
                                   float innerR, float outerR,
                                   float pct, int alpha, int rgb) {
        if (pct <= 0.001f) return;

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        final int segs = 96;
        final float startAng = -((float) Math.PI / 2f);
        final float arc = (float) (Math.PI * 2.0) * pct;

        int drawSegs = Math.max(1, (int) Math.floor(segs * pct));
        for (int i = 0; i < drawSegs; i++) {
            float t0 = i / (float) drawSegs;
            float t1 = (i + 1) / (float) drawSegs;

            float a0 = startAng + arc * t0;
            float a1 = startAng + arc * t1;

            float cos0 = MathHelper.cos(a0);
            float sin0 = MathHelper.sin(a0);
            float cos1 = MathHelper.cos(a1);
            float sin1 = MathHelper.sin(a1);

            float ox0 = cx + cos0 * outerR;
            float oy0 = cy + sin0 * outerR;
            float ix0 = cx + cos0 * innerR;
            float iy0 = cy + sin0 * innerR;

            float ox1 = cx + cos1 * outerR;
            float oy1 = cy + sin1 * outerR;
            float ix1 = cx + cos1 * innerR;
            float iy1 = cy + sin1 * innerR;

            bb.vertex(mat, ox0, oy0, 0).color(r, g, b, alpha).next();
            bb.vertex(mat, ix0, iy0, 0).color(r, g, b, alpha).next();
            bb.vertex(mat, ix1, iy1, 0).color(r, g, b, alpha).next();

            bb.vertex(mat, ox0, oy0, 0).color(r, g, b, alpha).next();
            bb.vertex(mat, ix1, iy1, 0).color(r, g, b, alpha).next();
            bb.vertex(mat, ox1, oy1, 0).color(r, g, b, alpha).next();
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableDepthTest();
    }

    /* =========================
       World render: aura + beam (visible to all)
       ========================= */

    private static void renderWorld(WorldRenderContext context) {
        ClientWorld world = context.world();
        if (world == null) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        MatrixStack matrices = context.matrixStack();
        Vec3d camPos = context.camera().getPos();
        float tickDelta = context.tickDelta();
        long now = world.getTime();

        // stale cleanup
        Iterator<Map.Entry<UUID, Visual>> it = VISUALS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Visual> e = it.next();
            if ((now - e.getValue().lastWorldTime) > 40) it.remove();
        }

        // remote hearing (nearby people)
        updateRemoteLoops(mc, world);

        VertexConsumer beamVc = consumers.getBuffer(RenderLayer.getEntityTranslucent(WATER_TEX));

        for (Map.Entry<UUID, Visual> entry : VISUALS.entrySet()) {
            UUID id = entry.getKey();
            Visual v = entry.getValue();

            PlayerEntity pe = findPlayer(world, id);
            if (pe == null) continue;
            if (pe.hasStatusEffect(ModStatusEffects.POWERLESS)) continue;

            if (v.aura) {
                renderAuraSigil(matrices, camPos, pe, v.modeOrd, now, tickDelta);
            }

            if (v.hose) {
                renderSmoothHose(beamVc, matrices, world, camPos, pe, now, tickDelta);
            }
        }
    }

    private static PlayerEntity findPlayer(ClientWorld world, UUID id) {
        for (PlayerEntity p : world.getPlayers()) {
            if (p.getUuid().equals(id)) return p;
        }
        return null;
    }

    /* =========================
       Aura: remove middle ring + slow smooth pulse
       ========================= */

    private static void renderAuraSigil(MatrixStack matrices, Vec3d camPos,
                                        PlayerEntity pe, int modeOrd,
                                        long worldTime, float tickDelta) {

        double px = MathHelper.lerp(tickDelta, pe.prevX, pe.getX());
        double py = MathHelper.lerp(tickDelta, pe.prevY, pe.getY());
        double pz = MathHelper.lerp(tickDelta, pe.prevZ, pe.getZ());

        double y = py + 0.04;

        int rgb = modeColorByOrd(modeOrd);
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = (rgb) & 255;

        float t = (float) (worldTime + tickDelta);
        float seed = (pe.getId() % 997) * 0.0137f;

        // ✅ slower + smoother pulse
        float pulse = 0.90f + 0.10f * MathHelper.sin(t * 0.35f + seed);
        float pulse2 = 0.92f + 0.08f * MathHelper.sin(t * 0.50f + seed * 1.7f);

        float outer = AURA_RADIUS * (0.995f + 0.015f * (pulse - 0.90f));
        float edgeW = Math.max(0.08f, outer * 0.018f);
        float diskR = outer * 0.90f;

        // ✅ scan ring: keep it near the edge (no middle stationary ring look)
        float prog = fract(t * 0.12f + seed); // slower sweep
        float scanR = outer * (0.60f + 0.40f * prog);
        float scanFade = (1f - prog);
        scanFade *= scanFade;

        matrices.push();
        matrices.translate(px - camPos.x, y - camPos.y, pz - camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // soft fill
        int aCenter = clampAlpha((int) (8f * pulse2), 0, 60);
        int aEdge   = clampAlpha((int) (45f * pulse), 0, 110);
        drawFilledDiskGradient(mat, diskR, r, g, b, aCenter, aEdge);

        // outer ring
        int aEdgeRing = clampAlpha((int) (105f * pulse), 0, 155);
        drawRingBand(mat, outer - edgeW, outer + edgeW, r, g, b, aEdgeRing);

        // scan ring (near edge)
        int aScan = clampAlpha((int) (140f * scanFade), 0, 180);
        drawRingBand(mat, scanR - edgeW * 0.70f, scanR + edgeW * 0.70f, r, g, b, aScan);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void drawFilledDiskGradient(Matrix4f mat, float radius,
                                               int r, int g, int b,
                                               int aCenter, int aEdge) {
        if (radius <= 0.02f) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        final float TAU = (float) (Math.PI * 2.0);
        for (int i = 0; i < SIGIL_SEGS; i++) {
            float t0 = TAU * (i / (float) SIGIL_SEGS);
            float t1 = TAU * ((i + 1) / (float) SIGIL_SEGS);

            float x0 = MathHelper.cos(t0) * radius;
            float z0 = MathHelper.sin(t0) * radius;

            float x1 = MathHelper.cos(t1) * radius;
            float z1 = MathHelper.sin(t1) * radius;

            buf.vertex(mat, 0f, 0f, 0f).color(r, g, b, aCenter).next();
            buf.vertex(mat, x0, 0f, z0).color(r, g, b, aEdge).next();
            buf.vertex(mat, x1, 0f, z1).color(r, g, b, aEdge).next();
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawRingBand(Matrix4f mat, float innerR, float outerR,
                                     int r, int g, int b, int a) {
        if (outerR <= 0.02f || a <= 0) return;
        innerR = Math.max(0.001f, innerR);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        final float TAU = (float) (Math.PI * 2.0);
        for (int i = 0; i < SIGIL_SEGS; i++) {
            float t0 = TAU * (i / (float) SIGIL_SEGS);
            float t1 = TAU * ((i + 1) / (float) SIGIL_SEGS);

            float c0 = MathHelper.cos(t0), s0 = MathHelper.sin(t0);
            float c1 = MathHelper.cos(t1), s1 = MathHelper.sin(t1);

            float in0x = c0 * innerR, in0z = s0 * innerR;
            float out0x = c0 * outerR, out0z = s0 * outerR;

            float in1x = c1 * innerR, in1z = s1 * innerR;
            float out1x = c1 * outerR, out1z = s1 * outerR;

            buf.vertex(mat, in0x, 0f, in0z).color(r, g, b, a).next();
            buf.vertex(mat, out0x, 0f, out0z).color(r, g, b, a).next();
            buf.vertex(mat, out1x, 0f, out1z).color(r, g, b, a).next();

            buf.vertex(mat, in0x, 0f, in0z).color(r, g, b, a).next();
            buf.vertex(mat, out1x, 0f, out1z).color(r, g, b, a).next();
            buf.vertex(mat, in1x, 0f, in1z).color(r, g, b, a).next();
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static int clampAlpha(int a, int lo, int hi) {
        return MathHelper.clamp(a, lo, hi);
    }

    /* =========================
       Smooth hose beam (all players)
       ========================= */

    private static void renderSmoothHose(VertexConsumer vc, MatrixStack matrices, ClientWorld world,
                                         Vec3d camPos, PlayerEntity pe, long worldTime, float tickDelta) {

        double px = MathHelper.lerp(tickDelta, pe.prevX, pe.getX());
        double py = MathHelper.lerp(tickDelta, pe.prevY, pe.getY());
        double pz = MathHelper.lerp(tickDelta, pe.prevZ, pe.getZ());

        Vec3d dir = pe.getRotationVec(tickDelta).normalize();

        double eyeY = py + pe.getEyeHeight(pe.getPose());
        Vec3d start = new Vec3d(px, eyeY, pz).add(0, -0.18, 0);

        Vec3d end = start.add(dir.multiply(SplashPower.HOSE_RANGE));
        HitResult hr = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY,
                pe
        ));
        if (hr != null && hr.getType() != HitResult.Type.MISS) end = hr.getPos();

        double dist = Math.max(0.2, end.distanceTo(start));

        int cycle = beamCycleColor(worldTime, tickDelta);
        int r = (cycle >> 16) & 0xFF;
        int g = (cycle >> 8) & 0xFF;
        int b = cycle & 0xFF;

        float width = 0.090f;
        int alpha = 175;
        int light = 0xF000F0;

        float scroll = (float) ((worldTime + tickDelta) * 0.08 % 1.0);
        float u0 = scroll;
        float u1 = scroll + (float) dist * 0.75f;

        Quaternionf q = safeRotateTo(new Vector3f(0f, 1f, 0f), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z));

        matrices.push();
        matrices.translate(start.x - camPos.x, start.y - camPos.y, start.z - camPos.z);
        matrices.multiply(q);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        beamQuad(vc, mat,
                new Vec3d( width, 0, 0),
                new Vec3d(-width, 0, 0),
                new Vec3d(-width, dist, 0),
                new Vec3d( width, dist, 0),
                u0, 0f, u1, 1f, light, r, g, b, alpha);

        beamQuad(vc, mat,
                new Vec3d(0, 0,  width),
                new Vec3d(0, 0, -width),
                new Vec3d(0, dist, -width),
                new Vec3d(0, dist,  width),
                u0, 0f, u1, 1f, light, r, g, b, alpha);

        matrices.pop();
    }

    private static Quaternionf safeRotateTo(Vector3f from, Vector3f to) {
        to.normalize();
        float dot = from.dot(to);
        if (dot > 0.9999f) return new Quaternionf();
        if (dot < -0.9999f) return new Quaternionf().rotateX((float) Math.PI);
        return new Quaternionf().rotateTo(from, to);
    }

    private static void beamQuad(VertexConsumer vc, Matrix4f mat,
                                 Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                                 float u0, float v0, float u1, float v1,
                                 int light, int r, int g, int bcol, int alpha) {

        vc.vertex(mat, (float) a.x, (float) a.y, (float) a.z)
                .color(r, g, bcol, alpha).texture(u0, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 1f, 0f).next();

        vc.vertex(mat, (float) b.x, (float) b.y, (float) b.z)
                .color(r, g, bcol, alpha).texture(u0, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 1f, 0f).next();

        vc.vertex(mat, (float) c.x, (float) c.y, (float) c.z)
                .color(r, g, bcol, alpha).texture(u1, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 1f, 0f).next();

        vc.vertex(mat, (float) d.x, (float) d.y, (float) d.z)
                .color(r, g, bcol, alpha).texture(u1, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 1f, 0f).next();
    }
}