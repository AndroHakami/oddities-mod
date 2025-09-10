package net.seep.odd.abilities.voids.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.seep.odd.abilities.overdrive.client.CpmHooks;
import net.seep.odd.sound.ModSounds;
import org.joml.Vector3f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.sound.SoundEvents;

/**
 * Plays CPM "void_open", switches to 3rd person, applies a smooth zoom-in for a given duration,
 * and adds purple/enchanted particles that gather into the right hand. A bright flash triggers
 * just before the animation ends.
 */
public final class VoidCpmBridge {
    private VoidCpmBridge() {}

    // ===== palette (0..1) =====
    private static final Vector3f PURPLE_BRIGHT = new Vector3f(0.82f, 0.48f, 1.00f);
    private static final Vector3f PURPLE_DARK   = new Vector3f(0.32f, 0.09f, 0.52f);

    // ===== state =====
    private static boolean active = false;
    private static int ticksLeft = 0;
    private static Perspective prevPerspective = Perspective.FIRST_PERSON;

    // zoom profile
    private static float totalSeconds = 1.2f; // default
    private static float maxZoom = 0.70f;     // FOV scale (smaller = more zoom; 0.70 ~ 30% zoom-in)

    /** Register per-tick hook once from client init. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active) return;
            if (client.world == null) return;
            ClientPlayerEntity p = client.player;
            if (p == null) return;

            // --- particle gather into right hand ---
            Vec3d hand = rightHandPos(p, 0.42f, 0.35f); // side offset, pullback
            for (int i = 0; i < 10; i++) {
                // spawn around the hand in a small cube and drift inward
                double rx = (client.world.random.nextDouble() - 0.5) * 1.6;
                double ry = (client.world.random.nextDouble() - 0.2) * 1.2;
                double rz = (client.world.random.nextDouble() - 0.5) * 1.6;
                double sx = hand.x + rx;
                double sy = hand.y + ry;
                double sz = hand.z + rz;

                double vx = (hand.x - sx) * 0.15;
                double vy = (hand.y - sy) * 0.15;
                double vz = (hand.z - sz) * 0.15;

                client.world.addParticle(ParticleTypes.ENCHANT, sx, sy, sz, vx, vy, vz);
                client.world.addParticle(new DustColorTransitionParticleEffect(PURPLE_BRIGHT, PURPLE_DARK, 1.2f),
                        sx, sy, sz, vx * 0.7, vy * 0.7, vz * 0.7);
            }

            // --- final flash two ticks before we expect the portal ---
            if (ticksLeft == 2) {
                // small ring of END_ROD + reverse portal puff at the hand
                for (int j = 0; j < 16; j++) {
                    double th = j * (Math.PI * 2.0 / 16.0);
                    double r = 0.55;
                    double fx = hand.x + Math.cos(th) * r;
                    double fz = hand.z + Math.sin(th) * r;
                    double fy = hand.y + (client.world.random.nextDouble() - 0.5) * 0.25;
                    client.world.addParticle(ParticleTypes.END_ROD, fx, fy, fz, 0.0, 0.01, 0.0);
                }
                client.world.addParticle(ParticleTypes.REVERSE_PORTAL, hand.x, hand.y, hand.z, 0.35, 0.65, 0.35);

                // sting sfx (client-side)
                p.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.6f);
            }

            // safety timeout if server never sends end
            if (--ticksLeft <= 0) {
                endOpenCinematic();
            }
        });
    }

    /** Called from VoidNet S2C: begin the cinematic. */
    public static void startOpenCinematic(float seconds) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        totalSeconds = Math.max(0.1f, seconds);

        // remember perspective and switch to 3rd person (behind)
        prevPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);

        // CPM animation (client-side)
        CpmHooks.play("void_open");

        // screen fx zoom controller (your existing helper)
        VoidZoomFx.begin(totalSeconds, maxZoom);

        // flag active
        ticksLeft = Math.round(totalSeconds * 20f);
        active = true;
    }

    /** Called from VoidNet S2C: end and restore camera. */
    public static void endOpenCinematic() {
        if (!active) return;
        active = false;
        ticksLeft = 0;

        // stop zoom & CPM in case they kept looping
        VoidZoomFx.end();
        CpmHooks.stop("void_open");

        // restore camera perspective
        var mc = MinecraftClient.getInstance();
        if (mc != null) mc.options.setPerspective(prevPerspective);
    }

    // ===== helpers =====

    /** Approximate world-space position of the player’s right hand (client-side). */
    private static Vec3d rightHandPos(ClientPlayerEntity p, float rightOffset, float pullback) {
        // forward & right from player yaw (XZ plane)
        float yaw = p.getYaw();
        double yawRad = Math.toRadians(yaw);
        Vec3d fwd = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3d right = new Vec3d(fwd.z, 0, -fwd.x).normalize();

        double y = p.getY() + 1.35;          // hand-ish height
        Vec3d base = new Vec3d(p.getX(), y, p.getZ());
        return base.add(right.multiply(rightOffset)).add(fwd.multiply(-pullback));
    }
}