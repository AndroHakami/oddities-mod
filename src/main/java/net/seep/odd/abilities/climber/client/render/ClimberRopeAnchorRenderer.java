package net.seep.odd.abilities.climber.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.climber.entity.ClimberRopeAnchorEntity;

import java.util.UUID;

public class ClimberRopeAnchorRenderer extends EntityRenderer<ClimberRopeAnchorEntity> {

    private static final Identifier LEAD = new Identifier("minecraft", "textures/entity/lead.png");

    public ClimberRopeAnchorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ClimberRopeAnchorEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        UUID ownerId = entity.getOwnerUuid();
        if (ownerId == null) return;

        PlayerEntity owner = entity.getWorld().getPlayerByUuid(ownerId);
        if (owner == null) return;

        // Anchor and waist positions (lerped)
        Vec3d anchor = entity.getLerpedPos(tickDelta);
        Vec3d waist = lerpPos(owner, tickDelta).add(0.0, owner.getHeight() * 0.45, 0.0);

        // Render rope in entity-local space
        matrices.push();
        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        matrices.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLeash());

        // Draw a segmented leash-like strip from (0,0,0) to (dx,dy,dz)
        float dx = (float) (waist.x - anchor.x);
        float dy = (float) (waist.y - anchor.y);
        float dz = (float) (waist.z - anchor.z);

        renderLeashLike(vc, matrices, dx, dy, dz, light);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private static Vec3d lerpPos(PlayerEntity p, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, p.prevX, p.getX());
        double y = MathHelper.lerp(tickDelta, p.prevY, p.getY());
        double z = MathHelper.lerp(tickDelta, p.prevZ, p.getZ());
        return new Vec3d(x, y, z);
    }

    /**
     * A lightweight “leash-like” strip. Uses RenderLayer.getLeash() so it looks like a lead.
     * This is not a full copy of vanilla’s knot renderer, but visually matches well.
     */
    static void renderLeashLike(VertexConsumer vc, MatrixStack matrices,
                                float dx, float dy, float dz, int light) {
        var m = matrices.peek().getPositionMatrix();

        // Slight sag based on length
        float dist = MathHelper.sqrt(dx*dx + dy*dy + dz*dz);
        float sag = Math.min(0.35f, dist * 0.03f);

        // Width of the ribbon
        float w = 0.025f;

        // Build a simple ribbon with 24 segments
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;

            float x0 = dx * t0;
            float z0 = dz * t0;
            float y0 = dy * t0 - sag * (4f * t0 * (1f - t0));

            float x1 = dx * t1;
            float z1 = dz * t1;
            float y1 = dy * t1 - sag * (4f * t1 * (1f - t1));

            // Per-segment “side” vector (perpendicular-ish in XZ)
            float sx = -dz;
            float sz = dx;
            float sl = MathHelper.sqrt(sx*sx + sz*sz);
            if (sl < 1.0e-4f) { sx = 1; sz = 0; sl = 1; }
            sx = sx / sl * w;
            sz = sz / sl * w;

            // Quad as two triangles (color is ignored by leash layer’s texture shading, but keep it neutral)
            add(vc, m, x0 - sx, y0, z0 - sz, light);
            add(vc, m, x0 + sx, y0, z0 + sz, light);
            add(vc, m, x1 + sx, y1, z1 + sz, light);

            add(vc, m, x0 - sx, y0, z0 - sz, light);
            add(vc, m, x1 + sx, y1, z1 + sz, light);
            add(vc, m, x1 - sx, y1, z1 - sz, light);
        }
    }

    private static void add(VertexConsumer vc, org.joml.Matrix4f m,
                            float x, float y, float z, int light) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, 255)
                .light(light)
                .next();
    }

    @Override
    public Identifier getTexture(ClimberRopeAnchorEntity entity) {
        return LEAD;
    }
}
