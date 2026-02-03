package net.seep.odd.abilities.rise.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.power.RisePower;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

@Environment(EnvType.CLIENT)
public final class RiseSoulOrbClient {

    private RiseSoulOrbClient() {}

    private static final byte STATE_NORMAL  = 0;
    private static final byte STATE_CASTING = 1;
    private static final byte STATE_FADE    = 2;

    private record SoulVisual(
            int id,
            Vec3d pos,
            long bornTick,
            long expireTick,
            byte state,
            long castStartTick,
            long castEndTick,
            long animEndTick
    ) {}

    private static final Int2ObjectOpenHashMap<SoulVisual> SOULS = new Int2ObjectOpenHashMap<>();

    // sphere quality (small orb)
    private static final int STACKS = 10;
    private static final int SEGS   = 20;

    // ring quality
    private static final int RING_SEGS = 48;

    // quick fade for expiry only (NOT for successful revive)
    private static final int FADE_TICKS = 8;

    // once-per-world sync request
    private static boolean syncSent = false;

    // -------- Perspective forcing while casting --------
    private static Perspective savedPerspective = null;
    private static boolean perspectiveForced = false;
    private static long perspectiveForceUntil = 0;

    private static void forceThirdPerson(int holdTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;

        long now = (mc.world != null) ? mc.world.getTime() : 0;
        perspectiveForceUntil = Math.max(perspectiveForceUntil, now + Math.max(1, holdTicks));

        if (!perspectiveForced) {
            savedPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            perspectiveForced = true;
        }
    }

    private static void restorePerspectiveIfDue(boolean forceNow) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!perspectiveForced || mc == null || mc.options == null) return;

        long now = (mc.world != null) ? mc.world.getTime() : 0;
        if (!forceNow && now < perspectiveForceUntil) return;

        try {
            if (savedPerspective != null) mc.options.setPerspective(savedPerspective);
        } catch (Throwable ignored) {}

        savedPerspective = null;
        perspectiveForced = false;
        perspectiveForceUntil = 0;
    }

    public static void init() {
        RiseCpmBridge.init();

        // add orb
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_SOUL_ADD_S2C, (client, handler, buf, responseSender) -> {
            final int id = buf.readVarInt();
            final double x = buf.readDouble();
            final double y = buf.readDouble();
            final double z = buf.readDouble();
            final int lifetimeTicks = buf.readVarInt();

            client.execute(() -> {
                if (client.world == null) return;
                long now = client.world.getTime();
                SOULS.put(id, new SoulVisual(
                        id, new Vec3d(x, y, z),
                        now, now + lifetimeTicks,
                        STATE_NORMAL,
                        0, 0, 0
                ));
            });
        });

        // remove orb
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_SOUL_REMOVE_S2C, (client, handler, buf, responseSender) -> {
            final int id = buf.readVarInt();
            client.execute(() -> {
                if (client.world == null) {
                    SOULS.remove(id);
                    return;
                }

                long now = client.world.getTime();
                SoulVisual s = SOULS.get(id);
                if (s == null) return;

                // ✅ If we were casting on it, it should disappear IMMEDIATELY when the mob spawns.
                // (server consumes soul + spawns mob same tick)
                if (s.state == STATE_CASTING) {
                    SOULS.remove(id);
                    return;
                }

                // otherwise (timeout/expire): quick fade out
                SOULS.put(id, new SoulVisual(
                        s.id, s.pos, s.bornTick,
                        now + FADE_TICKS,
                        STATE_FADE,
                        0, 0, now + FADE_TICKS
                ));
            });
        });

        // cast start (includes pos + lifetime on newer RisePower)
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_CAST_START_S2C, (client, handler, buf, responseSender) -> {
            final int soulId = buf.readVarInt();
            final int delayTicks = buf.readVarInt();
            final int animTicks = buf.readVarInt();

            final double x = buf.readDouble();
            final double y = buf.readDouble();
            final double z = buf.readDouble();
            final int remainingLifetime = buf.readVarInt();

            client.execute(() -> {
                if (client.world == null) return;

                RiseCpmBridge.playRise(animTicks);
                forceThirdPerson(animTicks);

                long now = client.world.getTime();

                SoulVisual existing = SOULS.get(soulId);
                if (existing == null) {
                    // if we missed the original add, create it now
                    SOULS.put(soulId, new SoulVisual(
                            soulId, new Vec3d(x, y, z),
                            now, now + Math.max(remainingLifetime, animTicks),
                            STATE_CASTING,
                            now, now + delayTicks,
                            now + animTicks
                    ));
                    return;
                }

                SOULS.put(soulId, new SoulVisual(
                        existing.id, existing.pos, existing.bornTick,
                        existing.expireTick,
                        STATE_CASTING,
                        now, now + delayTicks,
                        now + animTicks
                ));
            });
        });

        // cast cancel
        ClientPlayNetworking.registerGlobalReceiver(RisePower.RISE_CAST_CANCEL_S2C, (client, handler, buf, responseSender) -> {
            final int soulId = buf.readVarInt();
            client.execute(() -> {
                if (client.world == null) return;

                RiseCpmBridge.cancelRise();
                restorePerspectiveIfDue(true);

                SoulVisual s = SOULS.get(soulId);
                if (s == null) return;

                SOULS.put(soulId, new SoulVisual(
                        s.id, s.pos, s.bornTick,
                        s.expireTick,
                        STATE_NORMAL,
                        0, 0, 0
                ));
            });
        });

        // tick expiry + sync + perspective restore
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                SOULS.clear();
                syncSent = false;
                restorePerspectiveIfDue(true);
                return;
            }

            long now = client.world.getTime();

            // request sync once
            if (!syncSent) {
                syncSent = true;
                try {
                    ClientPlayNetworking.send(RisePower.RISE_SOUL_SYNC_C2S, PacketByteBufs.create());
                } catch (Throwable ignored) {}
            }

            // expire visuals
            var it = SOULS.int2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue().expireTick <= now) it.remove();
            }

            boolean anyCasting = false;
            for (SoulVisual s : SOULS.values()) {
                if (s.state == STATE_CASTING) { anyCasting = true; break; }
            }
            if (!anyCasting) restorePerspectiveIfDue(false);
        });

        WorldRenderEvents.LAST.register(RiseSoulOrbClient::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (SOULS.isEmpty()) return;

        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        float now = (client.world.getTime() + ctx.tickDelta());

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // keep it readable in darkness, but not "insanely bright"
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        try {
            for (SoulVisual s : SOULS.values()) {
                double dist2 = client.player.squaredDistanceTo(s.pos);
                if (dist2 > 120.0 * 120.0) continue;

                matrices.push();

                // hovering soul position
                float bob = 0.085f * MathHelper.sin(now * 0.85f + s.id * 0.13f);
                matrices.translate(s.pos.x, s.pos.y + 0.28 + bob, s.pos.z);

                // subtle spin
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(now * 18f + s.id * 9.0f));

                // ✅ slightly smaller + slightly less bright
                float baseRadius = 0.185f;

                // clean green
                int r = 40, g = 225, b = 125;

                float alphaMul = 1.0f;
                float ringMul = 0.85f;

                if (s.state == STATE_CASTING) {
                    // reacts while casting
                    float prog = clamp01(progress(now, s.castStartTick, s.castEndTick));
                    float ease = 1f - (float)Math.pow(1f - prog, 2.2);

                    // tighten + swell effect
                    baseRadius *= (1.0f + 0.22f * ease);

                    // stronger but still not nuclear
                    alphaMul *= (1.0f + 0.20f * ease);

                    // add a tiny "pull-in" wobble
                    float wob = 1.0f + 0.06f * MathHelper.sin(now * 2.4f + s.id);
                    matrices.scale(wob, 1.0f + 0.10f * ease, wob);

                    ringMul = 1.15f + 0.35f * ease;

                    // extra spin while casting
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(now * 40f));
                } else if (s.state == STATE_FADE) {
                    float prog = clamp01(progress(now, s.animEndTick - FADE_TICKS, s.animEndTick));
                    alphaMul *= (1.0f - prog);
                }

                float pulse = 0.88f + 0.12f * MathHelper.sin(now * 0.60f + s.id * 0.2f);

                float outer = baseRadius * (1.18f + 0.06f * pulse);
                float inner = baseRadius * (0.80f + 0.05f * pulse);

                // softer alpha values (cleaner)
                int aOuter = MathHelper.clamp((int)(90f  * alphaMul), 0, 140);
                int aInner = MathHelper.clamp((int)(155f * alphaMul), 0, 200);

                drawSphere(matrices, outer, r, g, b, aOuter);
                drawSphere(matrices, inner, r, g, b, aInner);

                // little orbit ring (helps it read as “soul” and looks nicer)
                float ringR = baseRadius * (1.45f + 0.10f * pulse) * ringMul;
                int aRing = MathHelper.clamp((int)(75f * alphaMul), 0, 120);

                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(65f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(now * 22f + s.id * 13f));
                drawRing(matrices, ringR, Math.max(0.012f, ringR * 0.08f), r, g, b, aRing);
                matrices.pop();

                matrices.pop();
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }

    private static float progress(float now, long startTick, long endTick) {
        if (endTick <= startTick) return 1f;
        return (now - (float)startTick) / (float)(endTick - startTick);
    }

    private static float clamp01(float v) { return MathHelper.clamp(v, 0f, 1f); }

    private static void drawSphere(MatrixStack matrices, float radius, int r, int g, int b, int a) {
        if (radius <= 0.001f || a <= 0) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < STACKS; i++) {
            double v0 = (double) i / (double) STACKS;
            double v1 = (double) (i + 1) / (double) STACKS;

            double phi0 = (v0 - 0.5) * Math.PI;
            double phi1 = (v1 - 0.5) * Math.PI;

            double y0 = Math.sin(phi0);
            double y1 = Math.sin(phi1);

            double cr0 = Math.cos(phi0);
            double cr1 = Math.cos(phi1);

            for (int j = 0; j < SEGS; j++) {
                double u0 = (double) j / (double) SEGS;
                double u1 = (double) (j + 1) / (double) SEGS;

                double th0 = u0 * Math.PI * 2.0;
                double th1 = u1 * Math.PI * 2.0;

                float x00 = (float) (Math.cos(th0) * cr0);
                float z00 = (float) (Math.sin(th0) * cr0);
                float y00 = (float) (y0);

                float x10 = (float) (Math.cos(th1) * cr0);
                float z10 = (float) (Math.sin(th1) * cr0);

                float x01 = (float) (Math.cos(th0) * cr1);
                float z01 = (float) (Math.sin(th0) * cr1);
                float y01 = (float) (y1);

                float x11 = (float) (Math.cos(th1) * cr1);
                float z11 = (float) (Math.sin(th1) * cr1);

                vtx(buf, mat, x00 * radius, y00 * radius, z00 * radius, r, g, b, a);
                vtx(buf, mat, x01 * radius, y01 * radius, z01 * radius, r, g, b, a);
                vtx(buf, mat, x11 * radius, y01 * radius, z11 * radius, r, g, b, a);

                vtx(buf, mat, x00 * radius, y00 * radius, z00 * radius, r, g, b, a);
                vtx(buf, mat, x11 * radius, y01 * radius, z11 * radius, r, g, b, a);
                vtx(buf, mat, x10 * radius, y00 * radius, z10 * radius, r, g, b, a);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void drawRing(MatrixStack matrices, float radius, float halfWidth,
                                 int r, int g, int b, int a) {
        if (radius <= 0.001f || a <= 0) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float inR = Math.max(0.001f, radius - halfWidth);
        float outR = radius + halfWidth;

        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < RING_SEGS; i++) {
            double t0 = (Math.PI * 2.0 * i) / RING_SEGS;
            double t1 = (Math.PI * 2.0 * (i + 1)) / RING_SEGS;

            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);

            float in0x = c0 * inR,  in0z = s0 * inR;
            float out0x = c0 * outR, out0z = s0 * outR;

            float in1x = c1 * inR,  in1z = s1 * inR;
            float out1x = c1 * outR, out1z = s1 * outR;

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out0x, 0f, out0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);

            vtx(buf, mat, in0x, 0f, in0z, r, g, b, a);
            vtx(buf, mat, out1x, 0f, out1z, r, g, b, a);
            vtx(buf, mat, in1x, 0f, in1z, r, g, b, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static void vtx(BufferBuilder b, Matrix4f mat,
                            float x, float y, float z,
                            int r, int g, int bb, int a) {
        b.vertex(mat, x, y, z).color(r, g, bb, a).next();
    }
}
