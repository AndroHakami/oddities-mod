package net.seep.odd.particles.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Ground-aligned footprint (flat on XZ) that ROTATES toward a direction:
 * - Server encodes the orientation in dx/dz (unit forward vector).
 * - No color tint; uses texture colors verbatim.
 */
public final class SpottedStepsParticle extends Particle {
    private final SpriteProvider sprites;
    private Sprite sprite;

    private final float halfSize;
    private final float yawCos;
    private final float yawSin;

    public SpottedStepsParticle(ClientWorld world, double x, double y, double z,
                                double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.sprite = sprites.getSprite(this.random);

        // orientation from server-provided vx/vz (unit forward vector)
        float len = (float)Math.max(1e-5, Math.sqrt(vx*vx + vz*vz));
        float dx = (float)(vx / len);
        float dz = (float)(vz / len);
        // yaw = atan2(sin, cos) = atan2(dz, dx)
        float yaw = (float)Math.atan2(dz, dx);
        this.yawCos = MathHelper.cos(yaw);
        this.yawSin = MathHelper.sin(yaw);

        // visuals
        this.maxAge = 40;           // particle itself lives ~2s; damage duration is controlled server-side
        this.alpha  = 1.0f;
        this.setColor(1f, 1f, 1f);  // no tint
        this.halfSize = 0.45f;      // ~0.9 block wide
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }
        float life = (float)this.age / (float)this.maxAge;
        this.alpha = 1.0f - life;
        this.sprite = sprites.getSprite(this.random);
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float tickDelta) {
        Vec3d cam = camera.getPos();
        float cx = (float)(MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cam.x);
        float cy = (float)(MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cam.y) + 0.001f;
        float cz = (float)(MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cam.z);

        float u0 = sprite.getMinU(), u1 = sprite.getMaxU();
        float v0 = sprite.getMinV(), v1 = sprite.getMaxV();
        int light = this.getBrightness(tickDelta);

        // build a rotated square in the XZ plane (normal +Y)
        float hs = this.halfSize;

        // local corners (before rotation)
        float lx0 = -hs, lz0 = -hs;
        float lx1 = -hs, lz1 =  hs;
        float lx2 =  hs, lz2 =  hs;
        float lx3 =  hs, lz3 = -hs;

        // rotate by yaw (cos/sin passed from server via vx/vz)
        float rx0 = lx0 * yawCos - lz0 * yawSin, rz0 = lx0 * yawSin + lz0 * yawCos;
        float rx1 = lx1 * yawCos - lz1 * yawSin, rz1 = lx1 * yawSin + lz1 * yawCos;
        float rx2 = lx2 * yawCos - lz2 * yawSin, rz2 = lx2 * yawSin + lz2 * yawCos;
        float rx3 = lx3 * yawCos - lz3 * yawSin, rz3 = lx3 * yawSin + lz3 * yawCos;

        vc.vertex(cx + rx0, cy, cz + rz0).texture(u1, v1).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vc.vertex(cx + rx1, cy, cz + rz1).texture(u1, v0).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vc.vertex(cx + rx2, cy, cz + rz2).texture(u0, v0).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vc.vertex(cx + rx3, cy, cz + rz3).texture(u0, v1).color(this.red, this.green, this.blue, this.alpha).light(light).next();
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    /* ---------- factory ---------- */
    public static final class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        public Factory(SpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld w, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SpottedStepsParticle(w, x, y, z, vx, vy, vz, sprites);
        }
    }
}
