package net.seep.odd.abilities.owl.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.OwlPower;

@Environment(EnvType.CLIENT)
public final class OwlWingsFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    private static final Identifier OWL_WINGS =
            new Identifier(Oddities.MOD_ID, "textures/entity/owl/owl_wings.png");

    private final ElytraEntityModel<AbstractClientPlayerEntity> elytraModel;

    public OwlWingsFeatureRenderer(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx,
            ModelPart elytraPart
    ) {
        super(ctx);
        this.elytraModel = new ElytraEntityModel<>(elytraPart);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {

        if (!OwlPower.hasOwlAnySide(player)) return;
        if (player.isInvisible()) return;

        // If they actually wear a real elytra in chest slot, don’t double-render.
        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;

        matrices.push();

        // tiny offset so it sits nicely as a cosmetic layer
        matrices.translate(0.0, 0.0, 0.03);

        // ✅ correct in 1.20.1: copyStateTo works with ElytraEntityModel
        this.getContextModel().copyStateTo(this.elytraModel);

        this.elytraModel.setAngles(player, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(OWL_WINGS));
        this.elytraModel.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);

        matrices.pop();
    }
}
