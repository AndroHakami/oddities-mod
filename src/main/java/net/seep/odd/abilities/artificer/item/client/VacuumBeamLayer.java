// FILE: src/main/java/net/seep/odd/abilities/artificer/item/client/VacuumBeamLayer.java
package net.seep.odd.abilities.artificer.item.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public final class VacuumBeamLayer extends GeoRenderLayer<ArtificerVacuumItem> {

    // beam length
    private static final double LENGTH = 6.0;

    // ✅ much thinner tornado
    private static final double RAD_NEAR = 0.015;
    private static final double RAD_FAR  = 0.55;

    // spawn budget (smaller = cleaner, less “thick”)
    private static final int COUNT_PER_TICK = 9;

    private static long lastSpawnTick = Long.MIN_VALUE;

    public VacuumBeamLayer(GeoItemRenderer<ArtificerVacuumItem> r) { super(r); }

    @Override
    public void render(MatrixStack matrices,
                       ArtificerVacuumItem animatable,
                       BakedGeoModel bakedModel,
                       RenderLayer renderType,
                       VertexConsumerProvider buffers,
                       VertexConsumer buffer,
                       float partialTick,
                       int light,
                       int overlay) {

        if (!shouldRenderBeam()) return;

        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        long now = mc.world.getTime();
        if (now == lastSpawnTick) return; // once per tick
        lastSpawnTick = now;

        Vec3d dir = mc.player.getRotationVec(1f).normalize();
        Vec3d nozzle = nozzlePos(mc.player.getEyePos(), dir);

        // ✅ inherit player motion so particles “stick” while moving
        Vec3d playerVel = mc.player.getVelocity();

        // stable perpendicular basis
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = dir.crossProduct(up);
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();
        Vec3d up2 = right.crossProduct(dir).normalize();

        double time = (now + partialTick) * 0.85;

        for (int i = 0; i < COUNT_PER_TICK; i++) {
            // distance along beam (bias more towards the far end so you “see” the tornado)
            double d = Math.pow(mc.world.random.nextDouble(), 0.70) * LENGTH;
            double t01 = d / LENGTH;

            // ✅ tighter near nozzle
            double rad = lerp(RAD_NEAR, RAD_FAR, t01);
            rad *= (0.85 + mc.world.random.nextDouble() * 0.30);

            // swirl angle (higher = more “tornado”)
            double ang = time * 4.0 + d * 10.0 + mc.world.random.nextDouble() * 0.8;

            Vec3d swirlOff = right.multiply(Math.cos(ang) * rad)
                    .add(up2.multiply(Math.sin(ang) * rad));

            Vec3d pos = nozzle.add(dir.multiply(d)).add(swirlOff);

            // pull toward nozzle + slight inward pull + inherit player velocity
            Vec3d toNozzle = nozzle.subtract(pos).normalize();
            Vec3d inward = swirlOff.lengthSquared() > 1e-6 ? swirlOff.normalize().multiply(-0.09) : Vec3d.ZERO;

            Vec3d vel = toNozzle.multiply(0.38 + 0.14 * mc.world.random.nextDouble())
                    .add(inward)
                    .add(playerVel); // ✅ key: moves with you

            // ✅ tiny “ash” wisps (small particle)
            mc.world.addParticle(
                    ParticleTypes.WHITE_ASH,
                    pos.x, pos.y, pos.z,
                    vel.x, vel.y, vel.z
            );

            // occasional spark flecks
            if (mc.world.random.nextFloat() < 0.18f) {
                mc.world.addParticle(
                        ParticleTypes.ELECTRIC_SPARK,
                        pos.x, pos.y, pos.z,
                        vel.x * 0.25, vel.y * 0.25, vel.z * 0.25
                );
            }
        }
    }

    private static Vec3d nozzlePos(Vec3d eyePos, Vec3d dir) {
        // tweak if needed to better match your model nozzle
        return eyePos.add(dir.multiply(0.75)).add(0.0, -0.14, 0.0);
    }

    private static boolean shouldRenderBeam() {
        var mc = MinecraftClient.getInstance();
        return mc != null && mc.player != null
                && mc.player.isUsingItem()
                && mc.player.getActiveItem().getItem() instanceof ArtificerVacuumItem;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}