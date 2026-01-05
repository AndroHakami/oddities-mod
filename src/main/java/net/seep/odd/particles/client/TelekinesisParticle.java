// src/main/java/net/seep/odd/particles/client/TelekinesisParticle.java
package net.seep.odd.particles.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/**
 * TELEKINESIS particle:
 * - Spawns near an entity and "sticks" to a single jittered offset around it.
 * - Quickly fades out (fast fade).
 * - Full-bright / lit sheet for a glowy look.
 *
 * IMPORTANT:
 * - Does NOT modify RGB color at all (no setColor).
 * - Base alpha is 0.4f (then fades to 0).
 * - Factory drops ~20% of spawns (returns null) to reduce total count.
 * - NO orbiting / continuous movement: only a single offset is chosen once, then it stays there.
 */
public class TelekinesisParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    // anchor
    private int targetId = -1;
    private int anchorSearchTicks = 8;

    // fixed jitter offset around the entity (chosen once)
    private double offX, offY, offZ;

    // visuals
    private final float baseScale;
    private final float baseAlpha;

    protected TelekinesisParticle(ClientWorld world,
                                  double x, double y, double z,
                                  double vx, double vy, double vz,
                                  SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.sprites = sprites;

        this.gravityStrength = 0f;
        this.collidesWithWorld = false;
        this.velocityX = this.velocityY = this.velocityZ = 0.0;

        // short lifetime + fast fade
        this.maxAge = 6 + this.random.nextInt(6); // 6–11 ticks (snappier/glitchier)

        this.baseScale = 0.20f + this.random.nextFloat() * 0.18f;
        this.scale = baseScale;

        // DO NOT TOUCH RGB COLOR
        this.baseAlpha = 0.40f;
        this.alpha = baseAlpha;

        // sprite atlas animation handled by your .mcmeta if present
        this.setSprite(this.sprites);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) { this.markDead(); return; }

        LivingEntity target = null;

        // get cached target if we already have one
        if (targetId >= 0) {
            var e = this.world.getEntityById(targetId);
            if (e instanceof LivingEntity le && le.isAlive()) target = le;
        } else if (anchorSearchTicks-- > 0) {
            // find nearest living entity around spawn (since we spawn near the controlled entity)
            double r = 1.75;
            var list = this.world.getEntitiesByClass(
                    LivingEntity.class,
                    new Box(this.x - r, this.y - r, this.z - r, this.x + r, this.y + r, this.z + r),
                    le -> le.isAlive()
            );
            if (!list.isEmpty()) {
                // choose closest
                LivingEntity best = list.get(0);
                double bestD = best.squaredDistanceTo(this.x, this.y, this.z);
                for (int i = 1; i < list.size(); i++) {
                    LivingEntity le = list.get(i);
                    double d = le.squaredDistanceTo(this.x, this.y, this.z);
                    if (d < bestD) { best = le; bestD = d; }
                }
                target = best;
                this.targetId = target.getId();

                // Pick ONE random "glitch" offset around the entity and keep it fixed.
                double w = Math.max(0.35, target.getWidth() * 0.75);
                double h = Math.max(0.55, target.getHeight() * 0.85);

                this.offX = (this.random.nextDouble() - 0.5) * 2.0 * w;
                this.offZ = (this.random.nextDouble() - 0.5) * 2.0 * w;
                this.offY = (this.random.nextDouble()) * h; // 0..h
            }
        }

        // If anchored, hard-snap to the target + fixed offset each tick (no drift, no orbit)
        if (target != null) {
            this.prevPosX = this.x;
            this.prevPosY = this.y;
            this.prevPosZ = this.z;

            this.x = target.getX() + this.offX;
            this.y = target.getY() + this.offY;
            this.z = target.getZ() + this.offZ;
        }

        // FAST fade-out (alpha only; does not affect RGB)
        float fadeT = (float) this.age / (this.maxAge * 0.65f);
        fadeT = MathHelper.clamp(fadeT, 0.0f, 1.0f);
        this.alpha = this.baseAlpha * (1.0f - fadeT);

        // tiny shrink as it fades
        this.scale = this.baseScale * (0.90f + 0.10f * (1.0f - fadeT));
    }

    @Override public int getBrightness(float tint) { return 0xF000F0; }

    @Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_LIT; }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        public Factory(SpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld w,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            // ~20% fewer particles overall
            if (w.random.nextFloat() < 0.20f) return null;

            TelekinesisParticle p = new TelekinesisParticle(w, x, y, z, vx, vy, vz, sprites);
            p.setSprite(sprites);
            return p;
        }
    }
}
