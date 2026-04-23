package net.seep.odd.abilities.climber.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import net.seep.odd.abilities.climber.net.ClimberClimbNetworking;
import net.seep.odd.abilities.power.ClimberPower;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class ClimberClient {
    private ClimberClient() {}

    private static final KeyBinding TOGGLE_WALL_CLIMB = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.odd.climber_toggle_wall_climb",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.odd.powers"
    ));

    private static boolean climbToggleEnabled = true;
    private static Boolean lastSentToggle = null;
    private static int toggleHeartbeat = 0;

    /** Call once from your client initializer. */
    public static void init() {
        // Receive the server-authoritative 'can wall climb' state for all players.
        ClimberClimbNetworking.registerClient();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            climbToggleEnabled = true;
            lastSentToggle = null;
            toggleHeartbeat = 0;
        });

        ClientTickEvents.END_CLIENT_TICK.register(ClimberClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.options == null) return;
        if (client.getNetworkHandler() == null) return;

        while (TOGGLE_WALL_CLIMB.wasPressed()) {
            climbToggleEnabled = !climbToggleEnabled;
            client.player.sendMessage(Text.literal(climbToggleEnabled ? "Wall Climb: ON" : "Wall Climb: OFF"), true);
            sendToggleState();
        }

        toggleHeartbeat++;
        if (lastSentToggle == null || lastSentToggle.booleanValue() != climbToggleEnabled || toggleHeartbeat >= 20) {
            sendToggleState();
        }

        byte flags = 0;

        if (client.options.forwardKey.isPressed()) flags |= ClimberPower.IN_FORWARD;
        if (client.options.backKey.isPressed())    flags |= ClimberPower.IN_BACK;
        if (client.options.leftKey.isPressed())    flags |= ClimberPower.IN_LEFT;
        if (client.options.rightKey.isPressed())   flags |= ClimberPower.IN_RIGHT;
        if (client.options.jumpKey.isPressed())    flags |= ClimberPower.IN_JUMP;
        if (client.options.sneakKey.isPressed())   flags |= ClimberPower.IN_SNEAK;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(flags);
        ClientPlayNetworking.send(ClimberPower.CLIMBER_CTRL_C2S, buf);
    }

    private static void sendToggleState() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(climbToggleEnabled);
        ClientPlayNetworking.send(ClimberPower.CLIMBER_TOGGLE_C2S, buf);
        lastSentToggle = climbToggleEnabled;
        toggleHeartbeat = 0;
    }
}
