// src/main/java/net/seep/odd/abilities/splash/client/SplashPowerClient.java
package net.seep.odd.abilities.splash.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.power.SplashPower;
import net.seep.odd.sound.ModSounds;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class SplashPowerClient {

    private SplashPowerClient() {}

    private static boolean INITED = false;

    private static final class Beam {
        boolean active;
        Vec3d start = Vec3d.ZERO;
        Vec3d end = Vec3d.ZERO;
        long lastWorldTime;
    }

    private static final Map<UUID, Beam> BEAMS = new Object2ObjectOpenHashMap<>();

    // HUD (local player only)
    private static boolean gotState = false;
    private static long lastHudWorldTime = -1;

    private static SplashPower.Mode mode = SplashPower.Mode.LEAP;
    private static int resource = 1000;
    private static int resourceMax = 1000;
    private static boolean auraActive = false;
    private static boolean hoseActive = false;

    private static final Identifier WATER_TEX = new Identifier("minecraft", "textures/block/water_still.png");

    // Looping sounds (client-only, so stop is perfect and doesn't affect others)
    private static LoopingEntitySound bubblesLoop = null;
    private static LoopingEntitySound streamLoop = null;

    public static void init() {
        if (INITED) return;
        INITED = true;

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

                SplashPower.Mode[] vals = SplashPower.Mode.values();
                if (modeOrd >= 0 && modeOrd < vals.length) mode = vals[modeOrd];

                resource = res;
                resourceMax = Math.max(1, max);

                auraActive = aura;
                hoseActive = hose;

                gotState = true;
                lastHudWorldTime = client.world.getTime();

                updateLoopSounds(client);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SplashPower.S2C_SPLASH_BEAM, (client, handler, buf, responseSender) -> {
            UUID owner = buf.readUuid();
            boolean active = buf.readBoolean();
            Vec3d start = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d end   = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());

            client.execute(() -> {
                if (client.world == null) return;
                Beam b = BEAMS.computeIfAbsent(owner, u -> new Beam());
                b.active = active;
                b.start = start;
                b.end = end;
                b.lastWorldTime = client.world.getTime();
            });
        });

        HudRenderCallback.EVENT.register(SplashPowerClient::renderHud);
        WorldRenderEvents.LAST.register(ctx -> renderBeams(ctx.matrixStack(), ctx.camera(), ctx.tickDelta()));
    }

    /* =========================
       Color helpers (NO .rgb field dependency)
       ========================= */

    private static int modeColor(SplashPower.Mode m) {
        return switch (m) {
            case LEAP -> 0x2EF46A;   // lime-green
            case TONGUE -> 0x42F5F5; // aqua
            case SKIN -> 0xFF66CC;   // pink
        };
    }

    /** Cycle beam tint AQUA -> PINK -> LIME while active */
    private static int beamCycleColor(long worldTime, float tickDelta) {
        float t = (worldTime + tickDelta);
        int idx = (int) (Math.floor(t / 6.0f) % 3); // change every ~6 ticks
        return switch (idx) {
            case 0 -> 0x42F5F5; // aqua
            case 1 -> 0xFF66CC; // pink
            default -> 0x2EF46A; // lime
        };
    }

    private static void updateLoopSounds(MinecraftClient mc) {
        if (mc.player == null) return;

        // Bubbles loop
        if (auraActive) {
            if (bubblesLoop == null || !mc.getSoundManager().isPlaying(bubblesLoop)) {
                bubblesLoop = new LoopingEntitySound(ModSounds.BUBBLES, SoundCategory.PLAYERS, mc.player, 0.95f, 1.0f);
                mc.getSoundManager().play(bubblesLoop);
            }
        } else {
            if (bubblesLoop != null) {
                mc.getSoundManager().stop(bubblesLoop);
                bubblesLoop = null;
            }
        }

        // Stream loop
        if (hoseActive) {
            if (streamLoop == null || !mc.getSoundManager().isPlaying(streamLoop)) {
                streamLoop = new LoopingEntitySound(ModSounds.STREAM, SoundCategory.PLAYERS, mc.player, 0.95f, 1.0f);
                mc.getSoundManager().play(streamLoop);
            }
        } else {
            if (streamLoop != null) {
                mc.getSoundManager().stop(streamLoop);
                streamLoop = null;
            }
        }
    }

    private static void stopAllLoops(MinecraftClient mc) {
        if (mc == null) return;
        if (bubblesLoop != null) {
            mc.getSoundManager().stop(bubblesLoop);
            bubblesLoop = null;
        }
        if (streamLoop != null) {
            mc.getSoundManager().stop(streamLoop);
            streamLoop = null;
        }
    }

    private static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (!gotState) return;

        long now = mc.world.getTime();
        long age = (lastHudWorldTime < 0) ? 9999 : (now - lastHudWorldTime);

        // If packets stop (swapped power), hide + stop loops.
        if (age > 60) {
            gotState = false;
            auraActive = false;
            hoseActive = false;
            stopAllLoops(mc);
            return;
        }

        int h = ctx.getScaledWindowHeight();

        int barW = 140;
        int barH = 10;

        int x = 8;
        int y = h - 74;

        ctx.fill(x - 2, y - 18, x + barW + 2, y + barH + 22, 0x99000000);

        ctx.drawTextWithShadow(mc.textRenderer,
                "Splash • " + mode.display.getString(),
                x, y - 14, 0xFFFFFFFF);

        ctx.fill(x, y, x + barW, y + barH, 0x66000000);

        float pct = resourceMax <= 0 ? 0f : (resource / (float) resourceMax);
        pct = MathHelper.clamp(pct, 0f, 1f);
        int fill = (int) (barW * pct);

        int col = 0xAA000000 | (modeColor(mode) & 0x00FFFFFF);
        ctx.fill(x, y, x + fill, y + barH, col);

        String flags = "Aura: " + (auraActive ? "ON" : "OFF") + "  •  Hose: " + (hoseActive ? "ON" : "OFF");
        ctx.drawTextWithShadow(mc.textRenderer, flags, x, y + 12, 0xFFBFBFBF);
    }

    private static void renderBeams(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Vec3d camPos = camera.getPos();
        long now = mc.world.getTime();

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(RenderLayer.getEntityTranslucent(WATER_TEX));

        int cycle = beamCycleColor(now, tickDelta);
        int r = (cycle >> 16) & 0xFF;
        int g = (cycle >> 8) & 0xFF;
        int b = cycle & 0xFF;

        for (Beam beam : BEAMS.values()) {
            if (!beam.active) continue;
            if ((now - beam.lastWorldTime) > 6) continue;

            Vec3d s = beam.start.subtract(camPos);
            Vec3d e = beam.end.subtract(camPos);

            Vec3d dir = e.subtract(s);
            double len = dir.length();
            if (len < 0.05) continue;

            Vec3d d = dir.normalize();

            Vec3d up = new Vec3d(0, 1, 0);
            Vec3d side = d.crossProduct(up);
            if (side.lengthSquared() < 1.0E-6) side = d.crossProduct(new Vec3d(1, 0, 0));
            side = side.normalize().multiply(0.085);

            Vec3d side2 = d.crossProduct(side).normalize().multiply(0.085);

            float scroll = (float) ((now + tickDelta) * 0.08 % 1.0);
            float u0 = scroll;
            float u1 = scroll + (float) len * 0.75f;

            int light = 0xF000F0;
            Matrix4f mat = matrices.peek().getPositionMatrix();

            quad(vc, mat, s.add(side),  s.subtract(side),  e.subtract(side),  e.add(side),  u0, 0f, u1, 1f, light, r, g, b);
            quad(vc, mat, s.add(side2), s.subtract(side2), e.subtract(side2), e.add(side2), u0, 0f, u1, 1f, light, r, g, b);
        }

        immediate.draw();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                             float u0, float v0, float u1, float v1,
                             int light,
                             int r, int g, int bcol) {

        int alpha = 175;

        vc.vertex(mat, (float) a.x, (float) a.y, (float) a.z)
                .color(r, g, bcol, alpha)
                .texture(u0, v0)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0f, 1f, 0f)
                .next();

        vc.vertex(mat, (float) b.x, (float) b.y, (float) b.z)
                .color(r, g, bcol, alpha)
                .texture(u0, v1)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0f, 1f, 0f)
                .next();

        vc.vertex(mat, (float) c.x, (float) c.y, (float) c.z)
                .color(r, g, bcol, alpha)
                .texture(u1, v1)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0f, 1f, 0f)
                .next();

        vc.vertex(mat, (float) d.x, (float) d.y, (float) d.z)
                .color(r, g, bcol, alpha)
                .texture(u1, v0)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(0f, 1f, 0f)
                .next();
    }

    /**
     * Looping entity-following sound.
     * FIX: Entity#getRandom() doesn't exist in 1.20.1 (only LivingEntity#getRandom()).
     * Use a stable per-entity seed.
     */
    private static final class LoopingEntitySound extends EntityTrackingSoundInstance {
        public LoopingEntitySound(SoundEvent sound, SoundCategory category, net.minecraft.entity.Entity entity, float volume, float pitch) {
            super(sound, category, volume, pitch, entity, (long) entity.getId());
            this.repeat = true;
            this.repeatDelay = 0;
            this.attenuationType = SoundInstance.AttenuationType.LINEAR;
            this.relative = false;
        }
    }
}
