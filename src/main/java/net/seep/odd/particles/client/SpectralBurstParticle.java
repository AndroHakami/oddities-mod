package net.seep.odd.particles.client;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

public class SpectralBurstParticle extends SpriteBillboardParticle {
    protected SpectralBurstParticle(ClientWorld world, double x, double y, double z,
                                    double vx, double vy, double vz) {
        super(world, x, y, z, vx, vy, vz);
        this.setColor(1.0f, 0.15f, 0.15f);
        this.setAlpha(0.9f);
        this.velocityX = vx; this.velocityY = vy; this.velocityZ = vz;
        this.scale = 0.25f + this.random.nextFloat() * 0.35f;
        this.maxAge = 16 + this.random.nextInt(10);
        this.gravityStrength = 0.0f;
        this.collidesWithWorld = false;
    }

    @Override public void tick() {
        super.tick();
        float life = (float)this.age / (float)this.maxAge;
        this.alpha = MathHelper.clamp(0.9f * (1.0f - life), 0f, 0.9f);
        this.scale *= 0.98f;
    }

    @Override public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        public Factory(SpriteProvider sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(DefaultParticleType type, ClientWorld w, double x, double y, double z,
                                                 double vx, double vy, double vz) {
            SpectralBurstParticle p = new SpectralBurstParticle(w, x, y, z, vx, vy, vz);
            p.setSprite(this.sprites);
            return p;
        }
    }
}
