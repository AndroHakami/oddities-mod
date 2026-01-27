// src/main/java/net/seep/odd/particles/client/FairySparklesParticle.java
package net.seep.odd.particles.client;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * "fairy_sparkles" particle:
 * - simple translucent sparkle
 * - fullbright
 * - gentle fade + tiny twinkle
 *
 * Make sure you ALSO:
 * 1) have a particle json at: resources/assets/odd/particles/fairy_sparkles.json
 * 2) register the particle type server/common-side (ModParticles.FAIRY_SPARKLES)
 * 3) register the factory client-side in ParticleFactoryRegistry
 */
public class FairySparklesParticle extends SpriteBillboardParticle {

    // Twinkle phase so particles don't sync
    private final float jitter;

    protected FairySparklesParticle(ClientWorld world, double x, double y, double z,
                                    double vx, double vy, double vz) {
        super(world, x, y, z, vx, vy, vz);

        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;

        this.collidesWithWorld = false;
        this.gravityStrength = 0.0f;

        // Small, light, floaty sparkles
        this.scale = 0.10f + this.random.nextFloat() * 0.18f;
        this.maxAge = 14 + this.random.nextInt(14);

        // Start translucent
        this.alpha = 0.90f;

        // Initial tint (soft iridescent-ish baseline)
        this.setColor(0.95f, 0.85f, 1.00f);

        this.jitter = this.random.nextFloat() * (float)(Math.PI * 2.0);
    }

    @Override
    public void tick() {
        super.tick();

        float life = (float)this.age / (float)this.maxAge;

        // Smooth fade-out
        this.alpha = MathHelper.clamp(0.90f * (1.0f - life), 0f, 0.90f);

        // Tiny twinkle (size pulse)
        float tw = (MathHelper.sin((this.age * 0.55f) + jitter) * 0.5f + 0.5f);
        float sMul = 0.92f + tw * 0.18f;
        this.scale = this.scale * 0.92f + (this.scale * sMul) * 0.08f;

        // Very subtle color drift (still "simple", no fancy hue math)
        float swing = (MathHelper.sin(this.age * 0.25f + jitter) * 0.5f + 0.5f);
        float r = MathHelper.clamp(0.85f + swing * 0.15f, 0f, 1f);
        float g = MathHelper.clamp(0.75f + swing * 0.20f, 0f, 1f);
        float b = MathHelper.clamp(0.95f, 0f, 1f);
        this.setColor(r, g, b);
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0; // fullbright
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        public Factory(SpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld w,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            FairySparklesParticle p = new FairySparklesParticle(w, x, y, z, vx, vy, vz);
            p.setSprite(this.sprites);
            return p;
        }
    }
}
