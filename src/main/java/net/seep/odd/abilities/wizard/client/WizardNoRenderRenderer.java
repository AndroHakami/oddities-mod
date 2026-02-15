// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardNoRenderRenderer.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class WizardNoRenderRenderer<T extends Entity> extends EntityRenderer<T> {

    public WizardNoRenderRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        // do nothing
    }

    @Override
    public Identifier getTexture(T entity) {
        return null;
    }
}
