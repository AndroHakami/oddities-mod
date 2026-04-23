package net.seep.odd.item.custom;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class GeoItemClientHooks {
    private GeoItemClientHooks() {}

    static Supplier<Object> createRenderProvider(GeoItem item) {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                ? GeoItem.makeRenderer(item)
                : () -> null;
    }

    static void createGeoItemRenderer(Consumer<Object> consumer, String rendererClassName) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            Client.createGeoItemRenderer(consumer, rendererClassName);
        }
    }

    static void createBuiltinItemRenderer(Consumer<Object> consumer, String rendererClassName) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            Client.createBuiltinItemRenderer(consumer, rendererClassName);
        }
    }

    static PlayerEntity getClientPlayerOrNull() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return null;
        }
        return Client.getClientPlayerOrNull();
    }

    @Environment(EnvType.CLIENT)
    private static final class Client {
        private Client() {}

        private static void createGeoItemRenderer(Consumer<Object> consumer, String rendererClassName) {
            consumer.accept(new software.bernie.geckolib.animatable.client.RenderProvider() {
                private software.bernie.geckolib.renderer.GeoItemRenderer<?> renderer;

                @Override
                public software.bernie.geckolib.renderer.GeoItemRenderer<?> getCustomRenderer() {
                    if (renderer == null) {
                        renderer = instantiateGeoItemRenderer(rendererClassName);
                    }
                    return renderer;
                }
            });
        }

        private static void createBuiltinItemRenderer(Consumer<Object> consumer, String rendererClassName) {
            consumer.accept(new software.bernie.geckolib.animatable.client.RenderProvider() {
                private net.minecraft.client.render.item.BuiltinModelItemRenderer renderer;

                @Override
                public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = instantiateBuiltinRenderer(rendererClassName);
                    }
                    return renderer;
                }
            });
        }

        private static software.bernie.geckolib.renderer.GeoItemRenderer<?> instantiateGeoItemRenderer(String rendererClassName) {
            try {
                return (software.bernie.geckolib.renderer.GeoItemRenderer<?>) Class.forName(rendererClassName)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to create item renderer: " + rendererClassName, e);
            }
        }

        private static net.minecraft.client.render.item.BuiltinModelItemRenderer instantiateBuiltinRenderer(String rendererClassName) {
            try {
                return (net.minecraft.client.render.item.BuiltinModelItemRenderer) Class.forName(rendererClassName)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to create built-in item renderer: " + rendererClassName, e);
            }
        }

        private static PlayerEntity getClientPlayerOrNull() {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            return mc != null ? mc.player : null;
        }
    }
}
