
package net.seep.odd.quest.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.seep.odd.quest.QuestNetworking;
import org.lwjgl.glfw.GLFW;

public final class QuestKeybinds {
    public static KeyBinding ABANDON_QUEST;

    private QuestKeybinds() {
    }

    public static void init() {
        ABANDON_QUEST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.abandon_quest",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.odd.quests"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ABANDON_QUEST.wasPressed()) {
                if (QuestClientState.INSTANCE.hasActiveQuest()) {
                    ClientPlayNetworking.send(QuestNetworking.C2S_ABANDON, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
                }
            }
        });
    }
}
