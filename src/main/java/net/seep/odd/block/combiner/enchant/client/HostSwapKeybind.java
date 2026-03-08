// src/main/java/net/seep/odd/block/combiner/enchant/client/HostSwapKeybind.java
package net.seep.odd.block.combiner.enchant.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import net.seep.odd.block.combiner.enchant.HostSwapNet;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class HostSwapKeybind {
    private HostSwapKeybind(){}

    private static boolean inited = false;
    private static KeyBinding key;

    public static void init() {
        if (inited) return;
        inited = true;

        key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.host_swap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.odd.combiner"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.wasPressed()) {
                HostSwapNet.c2sSwap();
            }
        });
    }
}