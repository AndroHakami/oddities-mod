// src/main/java/net/seep/odd/particles/client/GreenBubbleParticle.java
package net.seep.odd.particles.client;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

public class GreenBubbleParticle extends SpriteBillboardParticle {

    private final float baseR = 0.18f;
    private final float baseG = 0.98f;
    private final float baseB = 0.42f;

    private final float pulseAmp = 0.10f;
    private final float pulseSpeed = 0.35f;
    private final float pulseJitter;

    protected GreenBubbleParticle(ClientWorld world, double x, double y, double z,
                                  double vx, double vy, double vz) {
        super(world, x, y, z, vx, vy, vz);

        this.velocityX = vx * 0.65;
        this.velocityY = vy * 0.65 + 0.01; // slight float-up
        this.velocityZ = vz * 0.65;

        this.scale = 0.20f + this.random.nextFloat() * 0.18f;
        this.maxAge = 7 + this.random.nextInt(6); // SHORT lifetime (smooth trails)
        this.gravityStrength = -0.02f; // negative gravity = gentle rise
        this.collidesWithWorld = false;

        this.pulseJitter = this.random.nextFloat() * (float)(Math.PI * 2.0);

        this.setColor(baseR, baseG, baseB);
        this.alpha = 0.85f;
    }

    @Override
    public void tick() {
        super.tick();

        float life = (float)this.age / (float)this.maxAge;
        this.alpha = MathHelper.clamp(0.85f * (1.0f - life), 0f, 0.85f);
        this.scale *= 0.96f;

        float swing = (MathHelper.sin(this.age * pulseSpeed + pulseJitter) * 0.5f + 0.5f) * pulseAmp;
        float r = MathHelper.clamp(baseR + swing * 0.02f, 0f, 1f);
        float g = MathHelper.clamp(baseG - swing * 0.05f, 0f, 1f);
        float b = MathHelper.clamp(baseB + swing * 0.05f, 0f, 1f);
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
        public Particle createParticle(DefaultParticleType type, ClientWorld w, double x, double y, double z,
                                       double vx, double vy, double vz) {
            GreenBubbleParticle p = new GreenBubbleParticle(w, x, y, z, vx, vy, vz);
            p.setSprite(this.sprites);
            return p;
        }
    }
}
