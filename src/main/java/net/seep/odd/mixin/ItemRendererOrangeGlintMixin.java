package net.seep.odd.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.abilities.power.SuperChargePower;
import net.seep.odd.render.FlatColorVertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Full-takeover orange look that still respects sprite alpha (no white billboards):
 *  • Keep the atlas-bound layer; disable vanilla purple glint.
 *  • Force base color to near-white orange at full-bright with a FlatColor consumer.
 *  • Draw two animated glow overlays right after the base call, also atlas-bound so alpha cuts the shape.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererOrangeGlintMixin {

    private static final int FULL_BRIGHT = 0xF000F0;

    @Shadow
    private void renderBakedItemModel(BakedModel model, ItemStack stack, int light, int overlay,
                                      MatrixStack matrices, VertexConsumer vertices) {}

    private static final ThreadLocal<ItemStack> ODD$STACK          = new ThreadLocal<>();
    private static final ThreadLocal<Boolean>   ODD$IN_OVERLAY     = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean>   ODD$DREW_THIS_CALL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD")
    )
    private void odd$remember(ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                              MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay,
                              BakedModel model, CallbackInfo ci) {
        ODD$STACK.set(stack);
        ODD$DREW_THIS_CALL.set(false);
    }

    /** Keep the original layer (atlas bound) so sprite alpha masks correctly; only kill the purple glint. */
    @ModifyArgs(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderer;getItemGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;ZZ)Lnet/minecraft/client/render/VertexConsumer;"
            )
    )
    private void odd$keepAtlasButDisableVanillaGlint(Args args) {
        ItemStack s = ODD$STACK.get();
        if (s == null || !SuperChargePower.isSupercharged(s)) return;
        // args: [0]=providers, [1]=layer, [2]=solid, [3]=glint
        args.set(3, false); // no vanilla purple glint
        // leave layer as-is so we stay bound to the atlas (preserves alpha cutout)
    }

    /** Force base color to near-white orange at full bright. */
    @ModifyArgs(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"
            )
    )
    private void odd$flatColorBase(Args args) {
        if (Boolean.TRUE.equals(ODD$IN_OVERLAY.get())) return;
        ItemStack s = ODD$STACK.get();
        if (s == null || !SuperChargePower.isSupercharged(s)) return;

        // Fullbright base
        args.set(2, FULL_BRIGHT);

        // Near-white orange (keeps sprite alpha; wipes original RGB)
        VertexConsumer orig = (VertexConsumer) args.get(5);
        VertexConsumer flat = new FlatColorVertexConsumer(orig, 1.00f, 0.96f, 0.65f, 1.0f);

        args.set(5, flat);
    }

    /** Glow overlays (atlas bound so alpha cuts the shape), drawn right after the base call while matrices are valid. */
    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void odd$overlaysAfterBase(ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                       MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay,
                                       BakedModel model, CallbackInfo ci) {
        if (ODD$DREW_THIS_CALL.get()) return;
        if (!SuperChargePower.isSupercharged(stack)) return;
        ODD$DREW_THIS_CALL.set(true);

        final MinecraftClient mc = MinecraftClient.getInstance();
        final float partial = mc.getLastFrameDuration();
        final int   age     = (mc.player != null ? mc.player.age : 0);
        final float t       = age + partial;

        float pulse   = 0.80f + 0.20f * MathHelper.sin(t * 0.25f);
        float twinkle = 0.88f + 0.12f * MathHelper.sin(t * 3.10f + (stack.hashCode() & 0xFF) * 0.07f);

        matrices.push();
        matrices.scale(1.012f, 1.012f, 1.012f); // soft halo edge

        ODD$IN_OVERLAY.set(Boolean.TRUE);
        try {
            // 1) “Eyes-like” glow but bound to the ATLASED texture to keep alpha cutout
            VertexConsumer atlasGlow = vcp.getBuffer(RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));

            VertexConsumer glowCol   = new FlatColorVertexConsumer(atlasGlow, 1.00f, 0.97f, 0.75f, pulse);
            RenderSystem.setShaderColor(1F, 1F, 1F, pulse);
            this.renderBakedItemModel(model, stack, FULL_BRIGHT, overlay, matrices, glowCol);

            // 2) Orange-tinted glint twinkle
            VertexConsumer glint = vcp.getBuffer(RenderLayer.getDirectGlint());
            VertexConsumer glintCol = new FlatColorVertexConsumer(glint, 1.00f, 0.92f, 0.60f, twinkle * 0.85f);
            RenderSystem.setShaderColor(1F, 1F, 1F, twinkle * 0.85f);
            this.renderBakedItemModel(model, stack, FULL_BRIGHT, overlay, matrices, glintCol);
            // 3) your textured overlay (glint-style, uses custom texture)
            VertexConsumer tex = vcp.getBuffer(net.seep.odd.render.SuperchargeLayers.overlay());
            this.renderBakedItemModel(model, stack, FULL_BRIGHT, overlay, matrices, tex);


            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        } finally {
            matrices.pop();
            ODD$IN_OVERLAY.set(Boolean.FALSE);
        }
    }
}
