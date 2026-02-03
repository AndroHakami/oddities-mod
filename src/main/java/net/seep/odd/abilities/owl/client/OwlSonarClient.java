// FILE: src/main/java/net/seep/odd/abilities/owl/client/OwlSonarClient.java
package net.seep.odd.abilities.owl.client;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class OwlSonarClient {
    private OwlSonarClient() {}

    private static final String TEAM_NAME = "odd_owl_sonar";

    // entityId -> endTick (client world time). Very short TTL, refreshed constantly while active.
    private static final Int2LongOpenHashMap GLOW_UNTIL = new Int2LongOpenHashMap();

    // ~0.2s at 20 TPS
    private static final int REFRESH_TTL_TICKS = 4;

    private static boolean visionActive = false;

    // wave visuals
    private static Vec3d waveOrigin = Vec3d.ZERO;
    private static long waveStartTick = -1;
    private static int waveRange = 100;
    private static float waveSpeed = 4.0f;

    // light-blue sonar color
    private static final int WAVE_R = 120;
    private static final int WAVE_G = 205;
    private static final int WAVE_B = 255;

    public static void registerClient() {
        OwlSonarFx.init();
        WorldRenderEvents.END.register(ctx -> renderWave(ctx.matrixStack(), ctx.tickDelta()));
    }

    public static void setVisionActive(boolean v) {
        visionActive = v;
        OwlSonarFx.setActive(v);

        if (!v) {
            // hard stop everything so nothing can "stick"
            GLOW_UNTIL.clear();
            waveStartTick = -1;
        } else {
            // ensure team exists when turning on
            getOrCreateSonarTeam();
        }
    }

    public static boolean isVisionActive() {
        return visionActive;
    }

    /** Used by the Entity#getScoreboardTeam mixin to tint the outline LIGHT BLUE. */
    public static Team getOrCreateSonarTeam() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;

        Scoreboard sb = client.world.getScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) {
            team = sb.addTeam(TEAM_NAME);
            team.setDisplayName(Text.literal("Owl Sonar"));
            team.setColor(Formatting.AQUA); // ✅ light blue outlines
            team.setFriendlyFireAllowed(true);
        } else {
            // ✅ if it already existed from earlier runs (yellow), force it to our new color
            team.setColor(Formatting.AQUA);
        }
        return team;
    }

    /**
     * Packet is only used for the expanding wave visuals.
     * (Glow is handled locally every tick while vision is active.)
     */
    public static void handleSonarPacket(MinecraftClient client, net.minecraft.network.PacketByteBuf buf) {
        final double ox = buf.readDouble();
        final double oy = buf.readDouble();
        final double oz = buf.readDouble();
        buf.readLong(); // server time anchor (unused)

        final int range = buf.readVarInt();
        final float speed = buf.readFloat();
        buf.readVarInt(); // tagTicks (unused now)

        final int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            buf.readVarInt(); // entity id
            buf.readVarInt(); // delay
        }

        client.execute(() -> {
            if (client.world == null) return;
            waveOrigin = new Vec3d(ox, oy, oz);
            waveStartTick = client.world.getTime();
            waveRange = range;
            waveSpeed = speed;
        });
    }

    public static void clientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        long now = client.world.getTime();

        // Maintain short-lived glow tags while vision is active
        if (!visionActive) {
            if (!GLOW_UNTIL.isEmpty()) GLOW_UNTIL.clear();
        } else {
            // Refresh nearby living entities every tick (no cooldown dependency)
            Box box = client.player.getBoundingBox().expand(waveRange);
            List<Entity> found = client.world.getOtherEntities(
                    client.player,
                    box,
                    e -> e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity)
            );

            long until = now + REFRESH_TTL_TICKS;
            for (Entity e : found) {
                if (e == client.player) continue;
                GLOW_UNTIL.put(e.getId(), until);
            }

            // Expire quickly if not refreshed
            int[] keys = GLOW_UNTIL.keySet().toIntArray();
            for (int id : keys) {
                long end = GLOW_UNTIL.get(id);
                if (now > end) GLOW_UNTIL.remove(id);
            }
        }

        // Wave cleanup
        if (waveStartTick >= 0) {
            double r = (now - waveStartTick) * waveSpeed;
            if (r > waveRange + 3) waveStartTick = -1;
        }
    }

    /** Called by your isGlowing / scoreboard team mixins. */
    public static boolean shouldGlow(Entity e) {
        if (!visionActive) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        long now = client.world.getTime();
        long end = GLOW_UNTIL.getOrDefault(e.getId(), -1L);
        return end >= now;
    }

    private static void renderWave(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (waveStartTick < 0) return;

        long now = client.world.getTime();
        float age = (now - waveStartTick) + tickDelta;
        float radius = age * waveSpeed;
        if (radius <= 0.05f) return;

        // Fade as it expands
        float maxR = (float) waveRange + 3f;
        float fade = 1.0f - (radius / maxR);
        if (fade <= 0.0f) return;

        Vec3d cam = client.gameRenderer.getCamera().getPos();
        double cx = waveOrigin.x - cam.x;
        double cy = waveOrigin.y - cam.y + 0.05;
        double cz = waveOrigin.z - cam.z;

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Main ring + 2 trailing ripples (classic sonar feel)
        drawRing(matrices, cx, cy, cz, radius, (int)(175 * fade));
        drawRing(matrices, cx, cy, cz, radius - 1.1f, (int)(110 * fade));
        drawRing(matrices, cx, cy, cz, radius - 2.2f, (int)(70 * fade));

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void drawRing(MatrixStack matrices, double cx, double cy, double cz, float radius, int alpha) {
        if (radius <= 0.08f || alpha <= 1) return;

        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);

        int seg = 96;
        for (int i = 0; i <= seg; i++) {
            double t = (Math.PI * 2.0) * (i / (double) seg);
            double x = cx + Math.cos(t) * radius;
            double z = cz + Math.sin(t) * radius;

            bb.vertex(matrices.peek().getPositionMatrix(), (float) x, (float) cy, (float) z)
                    .color(WAVE_R, WAVE_G, WAVE_B, alpha)
                    .next();
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }
}
