// FILE: src/main/java/net/seep/odd/item/wizard/client/WalkingStickItemRenderer.java
package net.seep.odd.item.wizard.client;

import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.Oddities;
import net.seep.odd.item.wizard.WalkingStickItem;

import net.minecraft.client.render.VertexConsumerProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class WalkingStickItemRenderer extends GeoItemRenderer<WalkingStickItem> {
    private ItemStack renderStack = ItemStack.EMPTY;

    public WalkingStickItemRenderer() {
        super(new WalkingStickItemModel());

        // Auto-glow: provide a matching *_glowmask.png next to each base texture.
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode transformType, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        this.renderStack = stack;
        super.render(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        this.renderStack = ItemStack.EMPTY;
    }

    @Override
    public Identifier getTextureLocation(WalkingStickItem animatable) {
        String element = "none";
        if (!this.renderStack.isEmpty() && this.renderStack.hasNbt()) {
            element = this.renderStack.getNbt().getString(WalkingStickItem.NBT_ELEMENT_KEY);
            if (element == null || element.isBlank()) element = "none";
        }

        // fire -> textures/item/walking_stick_fire.png
        return new Identifier(Oddities.MOD_ID, "textures/item/walking_stick_" + element + ".png");
    }
}
