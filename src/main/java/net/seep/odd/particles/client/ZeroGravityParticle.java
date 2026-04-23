package net.seep.odd.particles.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.seep.odd.status.ModStatusEffects;

public class ZeroGravityParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    private int targetId = -1;
    private int anchorSearchTicks = 6;

    private final float baseScale;
    private final float startAlpha;

    private double orbitRadius;
    private double orbitAngle;
    private double orbitSpeed;
    private double yOffset;
    private double bobSpeed;
    private double bobAmount;

    protected ZeroGravityParticle(ClientWorld world, double x, double y, double z,
                                  double vx, double vy, double vz,
                                  SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.sprites = sprites;

        this.gravityStrength = 0f;
        this.collidesWithWorld = false;
        this.velocityX = this.velocityY = this.velocityZ = 0.0;

        // a bit bigger / more noticeable
        this.baseScale = 0.30f + this.random.nextFloat() * 0.18f; // ~0.30 - 0.48
        this.scale = this.baseScale;

        // longer life than before, but still not super long
        this.maxAge = 16 + this.random.nextInt(7); // 16-22 ticks

        // more visible
        this.setColor(1.00f, 0.68f, 0.25f);
        this.startAlpha = 0.72f + this.random.nextFloat() * 0.14f; // ~0.72 - 0.86
        this.alpha = this.startAlpha;

        this.setSprite(this.sprites);
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;

        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        LivingEntity target = null;

        if (this.targetId >= 0) {
            Entity e = this.world.getEntityById(this.targetId);
            if (e instanceof LivingEntity le && le.isAlive() && le.hasStatusEffect(ModStatusEffects.GRAVITY_SUSPEND)) {
                target = le;
            } else {
                this.markDead();
                return;
            }
        } else if (this.anchorSearchTicks-- > 0) {
            double r = 1.75;
            var list = this.world.getEntitiesByClass(
                    LivingEntity.class,
                    new Box(this.x - r, this.y - r, this.z - r, this.x + r, this.y + r, this.z + r),
                    le -> le.isAlive() && le.hasStatusEffect(ModStatusEffects.GRAVITY_SUSPEND)
            );

            if (!list.isEmpty()) {
                target = list.get(0);
                this.targetId = target.getId();

                this.orbitRadius = 0.26D + this.random.nextDouble() * 0.20D;
                this.orbitAngle = this.random.nextDouble() * (Math.PI * 2.0D);
                this.orbitSpeed = 0.11D + this.random.nextDouble() * 0.10D;

                // chest / torso area
                this.yOffset = target.getHeight() * (0.32D + this.random.nextDouble() * 0.30D);

                this.bobSpeed = 0.16D + this.random.nextDouble() * 0.10D;
                this.bobAmount = 0.035D + this.random.nextDouble() * 0.03D;
            } else {
                this.markDead();
                return;
            }
        } else {
            this.markDead();
            return;
        }

        this.orbitAngle += this.orbitSpeed;

        double cx = target.getX();
        double cy = target.getY() + this.yOffset;
        double cz = target.getZ();

        this.x = cx + Math.cos(this.orbitAngle) * this.orbitRadius;
        this.z = cz + Math.sin(this.orbitAngle) * this.orbitRadius;
        this.y = cy + Math.sin(this.age * this.bobSpeed + this.orbitAngle) * this.bobAmount;

        float life = (float) this.age / (float) this.maxAge;

        // stays visible for most of its life, then fades near the end
        if (life < 0.7f) {
            this.alpha = this.startAlpha;
        } else {
            float fade = 1.0f - ((life - 0.7f) / 0.3f);
            this.alpha = this.startAlpha * Math.max(0.0f, fade);
        }

        // slight shrink near the end only
        this.scale = this.baseScale * (1.0f - life * 0.18f);
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    public static class Factory implements ParticleFactory<net.minecraft.particle.DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(net.minecraft.particle.DefaultParticleType type, ClientWorld w,
                                       double x, double y, double z, double vx, double vy, double vz) {
            ZeroGravityParticle p = new ZeroGravityParticle(w, x, y, z, vx, vy, vz, this.sprites);
            p.setSprite(this.sprites);
            return p;
        }
    }
}