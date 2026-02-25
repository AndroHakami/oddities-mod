package net.seep.odd.abilities.artificer.mixer.brew.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class BrambleTetherRenderer {
    private BrambleTetherRenderer() {}

    private static boolean inited = false;

    // aura state for THIS client’s view (driven externally by setAuraActiveTicks)
    private static int auraTicksLeft = 0;
    private static int auraTicksMax  = 1;

    // strike thorns (shoot out + retract)
    private static final Int2ObjectOpenHashMap<Strike> STRIKES = new Int2ObjectOpenHashMap<>();
    private static int nextKey = 1;

    public static void init() {
        if (inited) return;
        inited = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());

        // tick strikes down (client-time)
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null) return;

            if (!STRIKES.isEmpty()) {
                var it = STRIKES.int2ObjectEntrySet().fastIterator();
                while (it.hasNext()) {
                    var e = it.next();
                    Strike s = e.getValue();
                    s.age++;
                    if (s.age >= s.duration) it.remove();
                }
            }
        });

        // draw LAST so we’re definitely after vanilla flush points
        WorldRenderEvents.LAST.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) return;

            if (auraTicksLeft <= 0 && STRIKES.isEmpty()) return;

            Vec3d cam = ctx.camera().getPos();
            MatrixStack matrices = ctx.matrixStack();

            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);

            var consumers = ctx.consumers();
            VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

            float time = (mc.world.getTime() + ctx.tickDelta()) * 0.55f;

            // aura thorns around local player
            if (auraTicksLeft > 0) {
                float strength = MathHelper.clamp(auraTicksLeft / (float)Math.max(1, auraTicksMax), 0f, 1f);
                drawAura(vc, matrices, mc.player, strength, time);
            }

            // strike thorns
            for (Strike s : STRIKES.values()) {
                Entity victim = mc.world.getEntityById(s.victimId);
                Entity target = mc.world.getEntityById(s.targetId);
                if (victim == null || target == null) continue;

                Vec3d start = victim.getPos().add(0, victim.getHeight() * 0.62, 0);
                Vec3d end   = target.getPos().add(0, target.getHeight() * 0.55, 0);

                drawStrike(vc, matrices, start, end, s, time);
            }

            matrices.pop();

            // ✅ flush so it actually appears
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.draw();
            }
        });
    }

    /* ================= PUBLIC API ================= */

    /** Drive aura explicitly (RadiantBrambleEffect.Client calls this every tick). */
    public static void setAuraActiveTicks(int ticksLeft, int ticksMax) {
        auraTicksLeft = Math.max(0, ticksLeft);
        auraTicksMax  = Math.max(1, ticksMax);
    }

    /** Compatibility for your current call-site: trigger(victim, attacker, lifeTicks). */
    public static void trigger(int victimId, int targetId, int lifeTicks) {
        if (STRIKES.size() > 6) STRIKES.clear();
        STRIKES.put(nextKey++, new Strike(victimId, targetId, MathHelper.clamp(lifeTicks, 2, 40)));
    }

    /** Optional alias (if you want it elsewhere). */
    public static void triggerStrike(int victimId, int targetId) {
        trigger(victimId, targetId, 9);
    }

    public static void clearAll() {
        auraTicksLeft = 0;
        auraTicksMax = 1;
        STRIKES.clear();
    }

    /* ================= RENDER: AURA ================= */
    private static void drawSquiggleBundle(VertexConsumer vc, MatrixStack matrices,
                                           Vec3d a, Vec3d b,
                                           int segs, double amp, float seed,
                                           int strands, double radius,
                                           int r, int g, int bl, int al) {

        Vec3d dir = b.subtract(a);
        double len = dir.length();
        if (len < 1.0e-4) return;

        Vec3d d = dir.normalize();

        // Build two perpendicular axes around the direction so we can offset in a ring
        Vec3d upAxis = Math.abs(d.y) > 0.85 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d perp1 = d.crossProduct(upAxis);
        if (perp1.lengthSquared() < 1.0e-6) perp1 = d.crossProduct(new Vec3d(0, 0, 1));
        perp1 = perp1.normalize();

        Vec3d perp2 = d.crossProduct(perp1);
        if (perp2.lengthSquared() < 1.0e-6) perp2 = new Vec3d(0, 1, 0);
        perp2 = perp2.normalize();

        for (int s = 0; s < strands; s++) {
            double ang = (s / (double) strands) * (Math.PI * 2.0);
            Vec3d off = perp1.multiply(Math.cos(ang) * radius).add(perp2.multiply(Math.sin(ang) * radius));

            // Tiny variation so strands don't overlap perfectly
            float sSeed = seed + s * 1.37f;

            drawSquiggleLine(vc, matrices,
                    a.add(off), b.add(off),
                    segs, amp, sSeed,
                    r, g, bl, al);
        }
    }


    private static void drawAura(VertexConsumer vc, MatrixStack matrices,
                                 Entity player, float strength, float time) {
        Vec3d base = player.getPos();
        double px = base.x;
        double py = base.y;
        double pz = base.z;

        int r = 65, g = 38, b = 18;
        int a = (int)(255 * MathHelper.clamp(0.25f + 0.75f * strength, 0f, 1f));

        int thorns = 20 + (int)(18 * strength);

        // ✅ aura thickness tuning
        int auraStrands = 5;                           // was 1 line, now 5 strands
        double auraRadius = 0.012 + 0.020 * strength;  // ring radius (thickness)

        for (int i = 0; i < thorns; i++) {
            float fi = i / (float)thorns;

            double ang = fi * (Math.PI * 2.0) + time * 0.55 + Math.sin(fi * 9.0 + time) * 0.35;
            double rad = 0.35 + 0.18 * Math.sin(fi * 6.0 + time * 0.8);
            double y   = 0.25 + 1.25 * (0.5 + 0.5 * Math.sin(fi * 7.0 + time * 0.9));

            Vec3d start = new Vec3d(
                    px + Math.cos(ang) * rad,
                    py + y,
                    pz + Math.sin(ang) * rad
            );

            Vec3d out = new Vec3d(Math.cos(ang), 0.0, Math.sin(ang)).normalize();
            Vec3d end = start.add(out.multiply(0.18 + 0.22 * strength));

            drawSquiggleBundle(vc, matrices,
                    start, end,
                    8,
                    0.06 * strength,
                    time + i * 0.37f,
                    auraStrands, auraRadius,
                    r, g, b, a);
        }
    }


    /* ================= RENDER: STRIKE ================= */

    private static void drawStrike(VertexConsumer vc, MatrixStack matrices,
                                   Vec3d start, Vec3d end, Strike s, float time) {

        float p = s.age / (float)Math.max(1, s.duration);

        float extend = MathHelper.clamp(p / 0.35f, 0f, 1f);
        float retract = MathHelper.clamp((p - 0.55f) / 0.45f, 0f, 1f);
        float k = extend * (1.0f - retract);

        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.01) return;

        Vec3d tip = start.add(dir.normalize().multiply(len * k));

        int r = 75, g = 42, b = 20;
        int a = 235;

        // ✅ strike thickness tuning (a LOT thicker)
        int strikeStrands = 13;                               // was 3
        double strikeRadius = 0.035 + 0.060 * (1.0 - retract); // thick when extended, shrinks as it retracts

        drawSquiggleBundle(vc, matrices,
                start, tip,
                14,
                0.10 * (1.0 - retract),
                time + 1.2f,
                strikeStrands, strikeRadius,
                r, g, b, a);
    }


    /* ================= LOW-LEVEL DRAW ================= */

    private static void drawSquiggleLine(VertexConsumer vc, MatrixStack matrices,
                                         Vec3d a, Vec3d b,
                                         int segs, double amp, float seed,
                                         int r, int g, int bl, int al) {
        Vec3d dir = b.subtract(a);
        double len = dir.length();
        if (len < 1.0e-4) return;

        Vec3d d = dir.normalize();
        Vec3d up = Math.abs(d.y) > 0.85 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d p = d.crossProduct(up).normalize();

        Vec3d prev = a;

        for (int i = 1; i <= segs; i++) {
            float t = i / (float)segs;
            Vec3d pos = a.add(dir.multiply(t));

            double mid = Math.sin(t * Math.PI);
            double wob = Math.sin(seed + t * 10.0) * amp * mid;
            double wob2 = Math.cos(seed * 1.7 + t * 12.0) * amp * 0.65 * mid;

            Vec3d now = pos.add(p.multiply(wob)).add(up.multiply(wob2));

            line(vc, matrices, prev, now, r, g, bl, al);
            prev = now;
        }
    }

    private static void line(VertexConsumer vc, MatrixStack matrices, Vec3d a, Vec3d b,
                             int r, int g, int bl, int al) {
        vc.vertex(matrices.peek().getPositionMatrix(), (float)a.x, (float)a.y, (float)a.z)
                .color(r, g, bl, al)
                .normal(matrices.peek().getNormalMatrix(), 0, 1, 0)
                .next();

        vc.vertex(matrices.peek().getPositionMatrix(), (float)b.x, (float)b.y, (float)b.z)
                .color(r, g, bl, al)
                .normal(matrices.peek().getNormalMatrix(), 0, 1, 0)
                .next();
    }

    private static final class Strike {
        final int victimId;
        final int targetId;
        final int duration;
        int age = 0;

        Strike(int victimId, int targetId, int duration) {
            this.victimId = victimId;
            this.targetId = targetId;
            this.duration = Math.max(1, duration);
        }
    }
}
