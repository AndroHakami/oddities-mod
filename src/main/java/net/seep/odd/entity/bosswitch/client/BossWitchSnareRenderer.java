package net.seep.odd.entity.bosswitch.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.seep.odd.entity.bosswitch.BossWitchSnareEntity;

public final class BossWitchSnareRenderer extends EntityRenderer<BossWitchSnareEntity> {
    private static final Identifier DUMMY = new Identifier("minecraft", "textures/misc/white.png");

    public BossWitchSnareRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(BossWitchSnareEntity entity,
                       float yaw,
                       float tickDelta,
                       MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers,
                       int light) {
        BossWitchSnareFx.track(entity, tickDelta);
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(BossWitchSnareEntity entity) {
        return DUMMY;
    }
}
