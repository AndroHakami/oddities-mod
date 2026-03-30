package net.seep.odd.item.custom;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;

import net.seep.odd.item.custom.client.FrogHatRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FrogHatItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

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
        consumer.accept(new RenderProvider() {
            private FrogHatRenderer renderer;

            @Override
            public BipedEntityModel<LivingEntity> getHumanoidArmorModel(
                    LivingEntity living,
                    ItemStack stack,
                    EquipmentSlot slot,
                    BipedEntityModel<LivingEntity> original) {

                if (renderer == null) {
                    renderer = new FrogHatRenderer();
                }

                renderer.prepForRender(living, stack, slot, original);
                return renderer;
            }
        });
    }
}