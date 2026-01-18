// src/main/java/net/seep/odd/mixin/client/VillagerClothingTintMixin.java
package net.seep.odd.mixin.client;

import net.minecraft.client.render.entity.feature.VillagerClothingFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(VillagerClothingFeatureRenderer.class)
public abstract class VillagerClothingTintMixin {

    private static final Identifier CORRUPTION_ID = new Identifier("odd", "corruption");

    @ModifyArgs(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;" +
                    "Lnet/minecraft/client/render/VertexConsumerProvider;" +
                    "ILnet/minecraft/entity/LivingEntity;FFFFFF)V", // <-- 6 floats (correct)
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;" +
                            "renderModel(Lnet/minecraft/client/render/entity/model/EntityModel;" +
                            "Lnet/minecraft/util/Identifier;" +
                            "Lnet/minecraft/client/util/math/MatrixStack;" +
                            "Lnet/minecraft/client/render/VertexConsumerProvider;" +
                            "ILnet/minecraft/entity/LivingEntity;FFF)V"
            ),
            require = 0
    )
    private void odd$tintCorruptedClothes(Args args) {
        // FeatureRenderer.renderModel args:
        // 0 model, 1 texture, 2 matrices, 3 vcp, 4 light, 5 entity, 6 r, 7 g, 8 b
        Object entObj = args.get(5);
        if (!(entObj instanceof LivingEntity le)) return;

        var effect = Registries.STATUS_EFFECT.get(CORRUPTION_ID);
        if (effect == null || !le.hasStatusEffect(effect)) return;

        // Multiply existing tint so you preserve vanilla layer intent (safer than hard replacing).
        float r = (Float) args.get(6);
        float g = (Float) args.get(7);
        float b = (Float) args.get(8);

        // Dark / cold tint (tweak these numbers)
        args.set(6, r * 0.35f);
        args.set(7, g * 0.35f);
        args.set(8, b * 0.45f);
    }
}
