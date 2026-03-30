package net.seep.odd.item.custom;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.seep.odd.item.custom.client.GhastShoesRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class GhastShoesItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public GhastShoesItem(ArmorMaterial material, Settings settings) {
        super(material, Type.BOOTS, settings);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // no code-side animation controller needed for now
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
            private GhastShoesRenderer renderer;

            @Override
            public BipedEntityModel<LivingEntity> getHumanoidArmorModel(
                    LivingEntity living,
                    ItemStack stack,
                    EquipmentSlot slot,
                    BipedEntityModel<LivingEntity> original) {

                if (renderer == null) {
                    renderer = new GhastShoesRenderer();
                }

                renderer.prepForRender(living, stack, slot, original);
                return renderer;
            }
        });
    }
}