package net.seep.odd.mixin;

import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    // package-private GameRenderer#loadPostProcessor(Identifier)
    @Invoker("loadPostProcessor")
    void odd$loadPostProcessor(Identifier id);

    // field: private PostEffectProcessor postProcessor;
    @Accessor("postProcessor")
    PostEffectProcessor odd$getPostProcessor();

    @Accessor("postProcessor")
    void odd$setPostProcessor(PostEffectProcessor fx);
}
