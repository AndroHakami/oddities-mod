package net.seep.odd.particles.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

public class PoisonParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final float startScale;
    private final float wobblePhase;
    private final float tintShift;

    protected PoisonParticle(ClientWorld world, double x, double y, double z,
                             double vx, double vy, double vz,
                             SpriteProvider sprites) {
        super(world, x, y, z);

        this.sprites = sprites;

        this.velocityX = vx * 0.45 + (this.random.nextDouble() - 0.5) * 0.01;
        this.velocityY = 0.015 + this.random.nextDouble() * 0.025 + vy * 0.35;
        this.velocityZ = vz * 0.45 + (this.random.nextDouble() - 0.5) * 0.01;

        this.startScale = 0.14f + this.random.nextFloat() * 0.16f;
        this.scale = this.startScale;

        this.maxAge = 16 + this.random.nextInt(12);
        this.gravityStrength = -0.002f;
        this.collidesWithWorld = false;

        this.wobblePhase = this.random.nextFloat() * ((float)Math.PI * 2f);
        this.tintShift = this.random.nextFloat() * 0.16f;

        float r = 0.38f + tintShift * 0.18f;
        float g = 0.78f + tintShift * 0.16f;
        float b = 0.22f + tintShift * 0.08f;
        this.setColor(r, g, b);

        this.alpha = 0.82f;
        this.setSpriteForAge(this.sprites);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.dead) return;

        float life = (float)this.age / (float)this.maxAge;
        float fade = 1.0f - life;

        this.alpha = 0.82f * fade;
        this.scale = this.startScale * (0.88f + 0.25f * fade);

        this.velocityX += Math.sin(this.age * 0.30f + this.wobblePhase) * 0.0009f;
        this.velocityZ += Math.cos(this.age * 0.26f + this.wobblePhase) * 0.0009f;

        float pulse = 0.90f + 0.10f * MathHelper.sin(this.age * 0.45f + this.wobblePhase);
        float r = MathHelper.clamp((0.38f + tintShift * 0.18f) * pulse, 0f, 1f);
        float g = MathHelper.clamp((0.78f + tintShift * 0.16f) * pulse, 0f, 1f);
        float b = MathHelper.clamp((0.22f + tintShift * 0.08f) * pulse, 0f, 1f);
        this.setColor(r, g, b);

        this.setSpriteForAge(this.sprites);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new PoisonParticle(world, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}