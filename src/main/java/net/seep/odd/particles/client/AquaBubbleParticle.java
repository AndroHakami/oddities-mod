// src/main/java/net/seep/odd/particles/client/AquaBubbleParticle.java
package net.seep.odd.particles.client;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

public class AquaBubbleParticle extends SpriteBillboardParticle {

    private final float baseR = 0.20f;
    private final float baseG = 0.92f;
    private final float baseB = 1.00f;

    private final float pulseAmp = 0.10f;
    private final float pulseSpeed = 0.33f;
    private final float pulseJitter;

    protected AquaBubbleParticle(ClientWorld world, double x, double y, double z,
                                 double vx, double vy, double vz) {
        super(world, x, y, z, vx, vy, vz);

        this.velocityX = vx * 0.65;
        this.velocityY = vy * 0.65 + 0.01;
        this.velocityZ = vz * 0.65;

        this.scale = 0.20f + this.random.nextFloat() * 0.18f;
        this.maxAge = 7 + this.random.nextInt(6);
        this.gravityStrength = -0.02f;
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
        float r = MathHelper.clamp(baseR - swing * 0.03f, 0f, 1f);
        float g = MathHelper.clamp(baseG + swing * 0.02f, 0f, 1f);
        float b = MathHelper.clamp(baseB - swing * 0.02f, 0f, 1f);
        this.setColor(r, g, b);
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0;
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
            AquaBubbleParticle p = new AquaBubbleParticle(w, x, y, z, vx, vy, vz);
            p.setSprite(this.sprites);
            return p;
        }
    }
}
