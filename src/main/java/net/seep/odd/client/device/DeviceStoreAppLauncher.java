package net.seep.odd.client.device;

import net.minecraft.client.MinecraftClient;
import net.seep.odd.device.store.client.DeviceStoreAppScreen;


public final class DeviceStoreAppLauncher {
    private DeviceStoreAppLauncher() {}

    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new DeviceStoreAppScreen());
    }
}
