package net.seep.odd.item.custom;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CrownItem extends ArmorItem implements GeoItem {
    private static final String RENDERER_CLASS = "net.seep.odd.item.custom.client.CrownRenderer";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoArmorClientHooks.createRenderProvider(this);

    public CrownItem(ArmorMaterial material, Settings settings) {
        super(material, Type.HELMET, settings);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return renderProvider;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        GeoArmorClientHooks.createArmorRenderer(consumer, RENDERER_CLASS);
    }
}
