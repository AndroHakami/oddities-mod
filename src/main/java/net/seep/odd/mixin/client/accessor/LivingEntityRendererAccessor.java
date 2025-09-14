package net.seep.odd.mixin.client.accessor;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    /** Access the protected final 'model' field on LivingEntityRenderer. */
    @Accessor("model")
    EntityModel<?> odd$getModel();
}
