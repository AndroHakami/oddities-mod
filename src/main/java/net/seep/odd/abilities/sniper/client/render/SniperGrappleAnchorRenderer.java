// FILE: src/main/java/net/seep/odd/abilities/sniper/client/render/SniperGrappleAnchorRenderer.java
package net.seep.odd.abilities.sniper.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
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
import net.seep.odd.abilities.sniper.entity.SniperGrappleAnchorEntity;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class SniperGrappleAnchorRenderer extends EntityRenderer<SniperGrappleAnchorEntity> {

    private static final Identifier DUMMY = new Identifier("minecraft", "textures/entity/lead.png");

    public SniperGrappleAnchorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public boolean shouldRender(SniperGrappleAnchorEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // never frustum-cull the cable
    }

    @Override
    public void render(SniperGrappleAnchorEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        UUID ownerId = entity.getOwnerUuid();
        if (ownerId == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity owner = (mc.player != null && mc.player.getUuid().equals(ownerId))
                ? mc.player
                : entity.getWorld().getPlayerByUuid(ownerId);

        if (owner == null) return;

        Vec3d anchorWorld = lerpPos(entity, tickDelta);
        Vec3d waistWorld  = lerpPos(owner, tickDelta).add(0.0, owner.getHeight() * 0.45, 0.0);

        Vec3d d = waistWorld.subtract(anchorWorld);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLeash());
        Vec3d camFromAnchor = mc.gameRenderer.getCamera().getPos().subtract(anchorWorld);

        SniperGrappleLeashRenderUtil.drawCableLocal(vc, matrices,
                (float)d.x, (float)d.y, (float)d.z,
                camFromAnchor,
                light);
    }

    private static Vec3d lerpPos(net.minecraft.entity.Entity e, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, e.prevX, e.getX());
        double y = MathHelper.lerp(tickDelta, e.prevY, e.getY());
        double z = MathHelper.lerp(tickDelta, e.prevZ, e.getZ());
        return new Vec3d(x, y, z);
    }

    @Override
    public Identifier getTexture(SniperGrappleAnchorEntity entity) {
        return DUMMY;
    }
}
