package net.seep.odd.abilities.climber.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
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

public final class ClimberPullTetherRenderer extends EntityRenderer<ClimberPullTetherEntity> {

    private static final Identifier DUMMY = new Identifier("minecraft", "textures/entity/lead.png");

    public ClimberPullTetherRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public boolean shouldRender(ClimberPullTetherEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ClimberPullTetherEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        UUID ownerId = entity.getOwnerUuid();
        if (ownerId == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity owner = (mc.player != null && mc.player.getUuid().equals(ownerId))
                ? mc.player
                : entity.getWorld().getPlayerByUuid(ownerId);

        if (owner == null) return;

        Entity target = entity.getWorld().getEntityById(entity.getTargetId());
        if (target == null) return;

        Vec3d startWorld = lerpPos(entity, tickDelta); // tether entity lives at target
        Vec3d waistWorld = lerpPos(owner, tickDelta).add(0.0, owner.getHeight() * 0.45, 0.0);

        Vec3d d = waistWorld.subtract(startWorld);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLeash());

        Vec3d camFromStart = mc.gameRenderer.getCamera().getPos().subtract(startWorld);

        ClimberLeashRenderUtil.drawLeashLocal(vc, matrices,
                (float)d.x, (float)d.y, (float)d.z,
                camFromStart,
                light);
    }

    private static Vec3d lerpPos(Entity e, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, e.prevX, e.getX());
        double y = MathHelper.lerp(tickDelta, e.prevY, e.getY());
        double z = MathHelper.lerp(tickDelta, e.prevZ, e.getZ());
        return new Vec3d(x, y, z);
    }

    @Override
    public Identifier getTexture(ClimberPullTetherEntity entity) {
        return DUMMY;
    }
}
