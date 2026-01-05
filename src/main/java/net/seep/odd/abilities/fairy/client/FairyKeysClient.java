package net.seep.odd.abilities.fairy.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.seep.odd.abilities.power.FairyPower;
import org.lwjgl.glfw.GLFW;

public final class FairyKeysClient {
    private FairyKeysClient() {}

    // Bindings
    private static KeyBinding TOGGLE_CAST, OPEN_MENU;
    private static KeyBinding UP, DOWN, LEFT, RIGHT;

    // Input buffer
    private static final byte N = 0, S = 1, W = 2, E = 3;
    private static final byte[] buf = new byte[3];
    private static int idx = 0;
    private static boolean castForm = false;

    private static boolean initialized = false;

    /** Call this ONCE from your OdditiesClient.onInitializeClient() */
    public static void registerClient() {
        if (initialized) return;
        initialized = true;

        TOGGLE_CAST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.cast_form", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.odd"));
        OPEN_MENU   = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.manage_flowers", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.odd"));

        UP    = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.up",    InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP,    "key.categories.odd"));
        DOWN  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.down",  InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN,  "key.categories.odd"));
        LEFT  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.left",  InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT,  "key.categories.odd"));
        RIGHT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.right", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "key.categories.odd"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (TOGGLE_CAST.wasPressed()) {
                castForm = !castForm;
                idx = 0;
                ClientPlayNetworking.send(
                        FairyPower.C2S_TOGGLE_CAST,
                        (PacketByteBuf) PacketByteBufs.create().writeBoolean(castForm)
                );
                if (castForm && client.inGameHud != null) {
                    client.inGameHud.setOverlayMessage(Text.literal("Cast Form ⟶ 3-arrow combo"), false);
                }
            }
            if (OPEN_MENU.wasPressed()) {
                ClientPlayNetworking.send(FairyPower.C2S_OPEN_MENU, PacketByteBufs.create());
            }

            if (!castForm) return;
            if (UP.wasPressed())    push(N);
            if (DOWN.wasPressed())  push(S);
            if (LEFT.wasPressed())  push(W);
            if (RIGHT.wasPressed()) push(E);
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (!castForm || idx == 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();

            Text t = Text.literal("⟪ ");
            for (int i = 0; i < 3; i++) {
                t = t.copy().append(i < idx ? arrow(buf[i]) : Text.literal("·"));
                if (i < 2) t = t.copy().append(" ");
            }
            t = t.copy().append(" ⟫");

            int x = (mc.getWindow().getScaledWidth() / 2) - 40;
            int y = mc.getWindow().getScaledHeight() - 30;

            // ✅ 1.20+: draw via DrawContext
            ctx.drawTextWithShadow(mc.textRenderer, t, x, y, 0xE4E0FF);
            // If you prefer OrderedText: ctx.drawTextWithShadow(mc.textRenderer, t.asOrderedText(), x, y, 0xE4E0FF);
        });
    }

    // Optional: expose current state for other UI bits if you need them
    public static boolean isCastForm() { return castForm; }
    public static void setCastForm(boolean enabled) { castForm = enabled; idx = 0; }

    /* ----------------- internals ----------------- */

    private static Text arrow(byte b) {
        return switch (b) {
            case 0 -> Text.literal("↑");
            case 1 -> Text.literal("↓");
            case 2 -> Text.literal("←");
            default -> Text.literal("→");
        };
    }

    private static void push(byte b) {
        buf[idx++] = b;
        if (idx >= 3) {
            var out = PacketByteBufs.create();
            out.writeByte(buf[0]);
            out.writeByte(buf[1]);
            out.writeByte(buf[2]);
            ClientPlayNetworking.send(FairyPower.C2S_CAST_COMMIT, out);
            idx = 0;
        }
    }
}
