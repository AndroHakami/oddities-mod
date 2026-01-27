package net.seep.odd.abilities.climber.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.climber.entity.ClimberPullTetherEntity;

import java.util.UUID;

public class ClimberPullTetherRenderer extends EntityRenderer<ClimberPullTetherEntity> {

    private static final Identifier LEAD = new Identifier("minecraft", "textures/entity/lead.png");

    public ClimberPullTetherRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ClimberPullTetherEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        UUID ownerId = entity.getOwnerUuid();
        if (ownerId == null) return;

        PlayerEntity owner = entity.getWorld().getPlayerByUuid(ownerId);
        if (owner == null) return;

        Entity target = entity.getWorld().getEntityById(entity.getTargetId());
        if (target == null) return;

        Vec3d targetPos = lerpPos(target, tickDelta).add(0.0, target.getHeight() * 0.5, 0.0);
        Vec3d ownerPos  = lerpPos(owner, tickDelta).add(0.0, owner.getHeight() * 0.45, 0.0);

        matrices.push();
        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        matrices.translate(targetPos.x - cam.x, targetPos.y - cam.y, targetPos.z - cam.z);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLeash());

        float dx = (float) (ownerPos.x - targetPos.x);
        float dy = (float) (ownerPos.y - targetPos.y);
        float dz = (float) (ownerPos.z - targetPos.z);

        // Reuse the same “leash-like ribbon” as anchor
        ClimberRopeAnchorRenderer.renderLeashLike(vc, matrices, dx, dy, dz, light);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private static Vec3d lerpPos(Entity e, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, e.prevX, e.getX());
        double y = MathHelper.lerp(tickDelta, e.prevY, e.getY());
        double z = MathHelper.lerp(tickDelta, e.prevZ, e.getZ());
        return new Vec3d(x, y, z);
    }

    @Override
    public Identifier getTexture(ClimberPullTetherEntity entity) {
        return LEAD;
    }
}
