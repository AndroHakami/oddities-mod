package net.seep.odd.particles.client;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

public class IceFlakeParticle extends SpriteBillboardParticle {
    // base tint (icy blue) + gentle color pulse
    private final float baseR = 0.55f;
    private final float baseG = 0.86f;
    private final float baseB = 1.00f;
    private final float pulseAmp = 0.10f;         // how “magical” the tint swing is
    private final float pulseSpeed = 0.30f;       // how fast the hue pulses
    private final float pulseJitter;              // per-particle phase so they don’t sync

    protected IceFlakeParticle(ClientWorld world, double x, double y, double z,
                               double vx, double vy, double vz) {
        super(world, x, y, z, vx, vy, vz);
        this.velocityX = vx; this.velocityY = vy; this.velocityZ = vz;

        this.scale = 0.25f + this.random.nextFloat() * 0.35f;
        this.maxAge = 16 + this.random.nextInt(10);
        this.gravityStrength = 0.5f;
        this.collidesWithWorld = false;

        this.pulseJitter = this.random.nextFloat() * (float)(Math.PI * 2.0);

        // initial icy tint
        this.setColor(baseR, baseG, baseB);
        // start slightly translucent so it’s not blinding up close
        this.alpha = 0.85f;
    }

    @Override
    public void tick() {
        super.tick();

        // fade out + slight shrink over lifetime
        float life = (float)this.age / (float)this.maxAge;
        this.alpha = MathHelper.clamp(0.85f * (1.0f - life), 0f, 0.85f);
        this.scale *= 0.68f;

        // light blue “magical” pulse (tiny, not distracting)
        float swing = (MathHelper.sin(this.age * pulseSpeed + pulseJitter) * 0.5f + 0.5f) * pulseAmp;
        float r = MathHelper.clamp(baseR - swing * 0.15f, 0f, 1f);
        float g = MathHelper.clamp(baseG + swing * 0.05f, 0f, 1f);
        float b = MathHelper.clamp(baseB + swing * 0.10f, 0f, 1f);
        this.setColor(r, g, b);
    }

    // keep fullbright so it “glows”, tint is handled via setColor()
    @Override
    public int getBrightness(float tint) {
        return 0xF000F0; // full-bright lightmap (glow) — color comes from setColor()
    }

    @Override
    public ParticleTextureSheet getType() {
        // translucent sheet plays nicely with other renderers and UI; no custom GL state
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        public Factory(SpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld w, double x, double y, double z,
                                       double vx, double vy, double vz) {
            IceFlakeParticle p = new IceFlakeParticle(w, x, y, z, vx, vy, vz);
            p.setSprite(this.sprites);
            return p;
        }
    }
}
