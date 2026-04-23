package net.seep.odd.item.custom;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FrogHatItem extends ArmorItem implements GeoItem {
    private static final String RENDERER_CLASS = "net.seep.odd.item.custom.client.FrogHatRenderer";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoArmorClientHooks.createRenderProvider(this);

    public FrogHatItem(ArmorMaterial material, Settings settings) {
        super(material, Type.HELMET, settings);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // no code-side controllers needed for now
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
