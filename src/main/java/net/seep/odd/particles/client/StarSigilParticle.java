package net.seep.odd.particles.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

public class StarSigilParticle extends SpriteBillboardParticle {
    private final float baseScale;
    private final float pulseOffset;

    protected StarSigilParticle(ClientWorld world, double x, double y, double z,
                                double vx, double vy, double vz) {
        super(world, x, y, z, vx, vy, vz);

        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;

        // keep each spawned star crisp and singular instead of leaving a long chain behind it
        this.baseScale = 0.24f + this.random.nextFloat() * 0.04f;
        this.scale = this.baseScale;
        this.maxAge = 2;
        this.gravityStrength = 0.0f;
        this.collidesWithWorld = false;

        this.red = 1.00f;
        this.green = 0.95f;
        this.blue = 0.58f;
        this.alpha = 1.00f;

        this.angle = 0.0f;
        this.pulseOffset = this.random.nextFloat() * ((float)Math.PI * 2.0f);
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        this.prevAngle = this.angle;

        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        this.x += this.velocityX;
        this.y += this.velocityY;
        this.z += this.velocityZ;

        float life = (float)this.age / (float)this.maxAge;
        float pulse = 0.96f + (MathHelper.sin((this.age * 0.7f) + this.pulseOffset) * 0.04f);

        this.scale = this.baseScale * pulse;
        this.alpha = MathHelper.clamp(1.0f - (life * 0.45f), 0.0f, 1.0f);

        this.red = 1.00f;
        this.green = 0.94f;
        this.blue = 0.58f;
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

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld w, double x, double y, double z,
                                       double vx, double vy, double vz) {
            StarSigilParticle p = new StarSigilParticle(w, x, y, z, vx, vy, vz);
            p.setSprite(this.sprites);
            return p;
        }
    }
}
