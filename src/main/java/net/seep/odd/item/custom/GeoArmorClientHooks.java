package net.seep.odd.item.custom;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class GeoArmorClientHooks {
    private GeoArmorClientHooks() {}

    static Supplier<Object> createRenderProvider(GeoItem item) {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                ? GeoItem.makeRenderer(item)
                : () -> null;
    }

    static void createArmorRenderer(Consumer<Object> consumer, String rendererClassName) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            Client.createArmorRenderer(consumer, rendererClassName);
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class Client {
        private Client() {}

        private static void createArmorRenderer(Consumer<Object> consumer, String rendererClassName) {
            consumer.accept(new software.bernie.geckolib.animatable.client.RenderProvider() {
                private software.bernie.geckolib.renderer.GeoArmorRenderer<?> renderer;

                @Override
                public net.minecraft.client.render.entity.model.BipedEntityModel<net.minecraft.entity.LivingEntity> getHumanoidArmorModel(
                        net.minecraft.entity.LivingEntity living,
                        net.minecraft.item.ItemStack stack,
                        net.minecraft.entity.EquipmentSlot slot,
                        net.minecraft.client.render.entity.model.BipedEntityModel<net.minecraft.entity.LivingEntity> original) {

                    if (renderer == null) {
                        renderer = instantiate(rendererClassName);
                    }

                    renderer.prepForRender(living, stack, slot, original);
                    return renderer;
                }
            });
        }

        private static software.bernie.geckolib.renderer.GeoArmorRenderer<?> instantiate(String rendererClassName) {
            try {
                return (software.bernie.geckolib.renderer.GeoArmorRenderer<?>) Class.forName(rendererClassName)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to create armor renderer: " + rendererClassName, e);
            }
        }
    }
}
