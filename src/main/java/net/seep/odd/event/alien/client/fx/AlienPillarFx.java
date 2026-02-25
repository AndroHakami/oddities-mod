package net.seep.odd.event.alien.client.fx;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.event.alien.client.AlienInvasionClientState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class AlienPillarFx {
    private AlienPillarFx() {}

    private static final class Pillar {
        final Vec3d center;
        final float radius;
        final float height;
        final int durationTicks;
        final long startWorldTime;

        Pillar(Vec3d c, float r, float h, int dur, long start) {
            center = c;
            radius = r;
            height = h;
            durationTicks = dur;
            startWorldTime = start;
        }
    }

    private static boolean inited = false;
    private static ManagedShaderEffect effect;
    private static final List<Pillar> pillars = new ArrayList<>();

    public static void init() {
        if (inited) return;
        inited = true;

        effect = ShaderEffectManager.getInstance().manage(
                new Identifier(Oddities.MOD_ID, "shaders/post/alien_pillar.json")
        );

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null) return;
            if (mc.world.getRegistryKey() != World.OVERWORLD) return;

            // pillars show even if you’re not “active” (in case packets arrive late),
            // but event usually controls them
            if (pillars.isEmpty()) return;

            float t = (float)(mc.world.getTime() + tickDelta);

            // cleanup + render each pillar additively
            long wt = mc.world.getTime();
            Iterator<Pillar> it = pillars.iterator();
            while (it.hasNext()) {
                Pillar p = it.next();

                float age = (float)((wt - p.startWorldTime) + tickDelta);
                float a01 = age / Math.max(1f, (float)p.durationTicks);

                if (a01 >= 1.0f) {
                    it.remove();
                    continue;
                }

                // intensity curve: slam in, then fade out
                float fadeIn = Math.min(1f, a01 / 0.25f);
                float fadeOut = 1f - smoothstep(0.60f, 1.0f, a01);
                float intensity = 1.0f * fadeIn * fadeOut;

                effect.setUniformValue("iTime", t);
                effect.setUniformValue("Center", (float)p.center.x, (float)p.center.y, (float)p.center.z);
                effect.setUniformValue("Radius", p.radius);
                effect.setUniformValue("Height", p.height);
                effect.setUniformValue("Age01", a01);
                effect.setUniformValue("Intensity", intensity);

                effect.render(tickDelta);
            }
        });
    }

    public static void add(Vec3d center, float radius, float height, int durationTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        pillars.add(new Pillar(center, radius, height, durationTicks, mc.world.getTime()));
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }
}