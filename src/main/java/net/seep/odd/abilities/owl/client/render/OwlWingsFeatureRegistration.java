package net.seep.odd.abilities.owl.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.EntityType;

@Environment(EnvType.CLIENT)
public final class OwlWingsFeatureRegistration {
    private OwlWingsFeatureRegistration() {}

    /** Call once from your client init. */
    public static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityType != EntityType.PLAYER) return;
            if (!(entityRenderer instanceof PlayerEntityRenderer playerRenderer)) return;

            ModelPart elytraPart = context.getPart(EntityModelLayers.ELYTRA);

            @SuppressWarnings("unchecked")
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx =
                    (FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>) (Object) playerRenderer;

            helper.register(new OwlWingsFeatureRenderer(ctx, elytraPart));
        });
    }
}
