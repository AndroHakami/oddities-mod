package net.seep.odd.particles.client;

import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.status.ModStatusEffects; // <-- your registry that exposes ZERO_GRAVITY

public class ZeroGravityParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    // anchor
    private int targetId = -1;
    private int anchorSearchTicks = 12; // keep trying to find the entity for a short while
    private double yOffset = 0.0;

    // visuals
    private final float baseScale;       // bigger than before
    private final float pulseAmp = 0.10f; // gentle size pulse
    private final float pulseSpd = 0.20f;

    protected ZeroGravityParticle(ClientWorld world, double x, double y, double z,
                                  double vx, double vy, double vz,
                                  SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.sprites = sprites;

        // we render one persistent ring; no gravity, no collisions, no motion
        this.gravityStrength = 0f;
        this.collidesWithWorld = false;
        this.velocityX = this.velocityY = this.velocityZ = 0;

        // BIGGER default size
        this.baseScale = 0.85f + this.random.nextFloat() * 0.25f; // ~0.85–1.10
        this.scale = baseScale;

        // long lifetime; we’ll self-destroy when the status ends or entity disappears
        this.maxAge = 500; // safety cap; usually removed sooner

        // glowing orange
        this.setColor(1.00f, 0.62f, 0.20f);
        this.alpha = 0.95f;

        // atlas-animated sprite (use your .mcmeta); do NOT call setSpriteForAge
        this.setSprite(this.sprites);
    }

    @Override
    public void tick() {
        // die if we somehow hang around too long
        if (this.age++ >= this.maxAge) { this.markDead(); return; }

        // find & cache the target entity once (or for a short window)
        LivingEntity target = null;
        if (targetId >= 0) {
            var e = this.world.getEntityById(targetId);
            if (e instanceof LivingEntity le) target = le;
        } else if (anchorSearchTicks-- > 0) {
            // look for the nearest living entity with ZERO_GRAVITY status around spawn point
            double r = 1.25;
            var list = this.world.getEntitiesByClass(
                    LivingEntity.class,
                    new Box(this.x - r, this.y - r, this.z - r, this.x + r, this.y + r, this.z + r),
                    le -> le.isAlive() && le.hasStatusEffect(ModStatusEffects.GRAVITY_SUSPEND)
            );
            if (!list.isEmpty()) {
                target = list.get(0);
                this.targetId = target.getId();
                // anchor slightly above torso
                this.yOffset = target.getHeight() * 0.52;
            }
        }

        // if anchored, follow; if effect is gone, remove
        if (target != null) {
            if (!target.isAlive() || !target.hasStatusEffect(ModStatusEffects.GRAVITY_SUSPEND)) {
                this.markDead();
                return;
            }
            // snap onto target each tick (no interpolation drift)
            this.prevPosX = this.x = target.getX();
            this.prevPosY = this.y = target.getY() + this.yOffset;
            this.prevPosZ = this.z = target.getZ();
        }

        // subtle breathing/pulse in size
        float t = this.age * pulseSpd + (targetId * 0.1f);
        float pulse = 1.0f + pulseAmp * MathHelper.sin(t);
        this.scale = baseScale * pulse;

        // keep full bright
        // alpha stays constant so your ring stays crisp
    }

    // glow
    @Override public int getBrightness(float tint) { return 0xF000F0; }

    // additive or translucent both work; additive pops more for rings
    @Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_LIT; }

    public static class Factory implements ParticleFactory<net.minecraft.particle.DefaultParticleType> {
        private final SpriteProvider sprites;
        public Factory(SpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(net.minecraft.particle.DefaultParticleType type, ClientWorld w,
                                       double x, double y, double z, double vx, double vy, double vz) {
            ZeroGravityParticle p = new ZeroGravityParticle(w, x, y, z, vx, vy, vz, sprites);
            p.setSprite(sprites);
            return p;
        }
    }
}
