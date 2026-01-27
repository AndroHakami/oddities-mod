package net.seep.odd.abilities.fairy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.seep.odd.abilities.power.FairyPower;
import org.lwjgl.glfw.GLFW;

public final class FairyKeysClient {
    private FairyKeysClient() {}

    private static KeyBinding TOGGLE_CAST, OPEN_MENU;
    private static KeyBinding ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT;

    // 0=UP,1=DOWN,2=LEFT,3=RIGHT
    private static final byte UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    private static final byte[] combo = new byte[3];
    private static int idx = 0;
    private static boolean castForm = false;

    /* ------------------- input queue (prevents missed taps) ------------------- */

    private static final int QUEUE_MAX = 4;
    private static final int[] queue = new int[QUEUE_MAX];
    private static int qHead = 0, qTail = 0, qSize = 0;

    private static void qClear() { qHead = qTail = qSize = 0; }
    private static boolean qOffer(int dir) {
        if (qSize >= QUEUE_MAX) return false;
        queue[qTail] = dir;
        qTail = (qTail + 1) % QUEUE_MAX;
        qSize++;
        return true;
    }
    private static int qPoll() {
        if (qSize <= 0) return -1;
        int v = queue[qHead];
        qHead = (qHead + 1) % QUEUE_MAX;
        qSize--;
        return v;
    }

    /* ------------------- timing knobs ------------------- */

    private static int nextAcceptTick = 0;

    private static final int ARM_DELAY = 7;
    private static final int INPUT_GAP = 5;

    /* ------------------- HUD fade ------------------- */

    private static boolean hudVisible = false;
    private static int hudAge = 0;
    private static int hudFadeOut = 0;

    private static final int FADE_IN = 8;
    private static final int LINGER = 12;
    private static final int FADE_OUT = 12;

    private static final byte[] lastCombo = new byte[3];
    private static int lastLen = 0;

    /* ------------------- edge detect ------------------- */
    // ✅ arrows only
    private static boolean prevUp, prevDown, prevLeft, prevRight;

    /* ------------------- camera forcing ------------------- */

    private static Perspective prevPerspective = null;
    private static boolean forcedThird = false;

    private static boolean initialized = false;

    /** Call once from your OdditiesClient.onInitializeClient() */
    public static void registerClient() {
        if (initialized) return;
        initialized = true;

        // ✅ CPM bridge tick cleanup (safe even if animations are one-shot)
        FairyCpmBridge.init();

        TOGGLE_CAST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.cast_form", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.odd"));
        OPEN_MENU   = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.manage_flowers", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.odd"));

        ARROW_UP    = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "key.categories.odd"));
        ARROW_DOWN  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "key.categories.odd"));
        ARROW_LEFT  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.left", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, "key.categories.odd"));
        ARROW_RIGHT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odd.fairy.right", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "key.categories.odd"));

        ClientPlayNetworking.registerGlobalReceiver(FairyPower.S2C_CAST_STATE, (client, handler, buf, resp) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> setCastLocal(on, false));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (hudVisible) hudAge++;
            if (hudFadeOut > 0) {
                hudFadeOut--;
                if (hudFadeOut == 0 && !castForm) {
                    hudVisible = false;
                    lastLen = 0;
                }
            }

            if (TOGGLE_CAST.wasPressed()) setCast(!castForm);
            if (OPEN_MENU.wasPressed()) ClientPlayNetworking.send(FairyPower.C2S_OPEN_MENU, PacketByteBufs.create());

            if (!castForm) {
                updatePrev();
                qClear();
                return;
            }

            if (client.currentScreen != null) {
                updatePrev();
                return;
            }

            // slow motion feel client-side
            if (client.player.input != null) {
                client.player.input.movementForward *= 0.20f;
                client.player.input.movementSideways *= 0.20f;
                client.player.input.jumping = false;
            }

            int now = client.player.age;

            // ✅ detect new arrow press, enqueue, AND play CPM immediately
            int dir = detectNewDir();
            if (dir != -1) {
                FairyCpmBridge.playDir((byte) dir);
                qOffer(dir);
            }

            if (qSize > 0 && now >= nextAcceptTick) {
                accept((byte) qPoll(), client);
                nextAcceptTick = now + INPUT_GAP;
            }

            updatePrev();
        });

        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (!hudVisible) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            float alphaIn = Math.min(1f, hudAge / (float) FADE_IN);
            float alphaOut = (hudFadeOut > 0 && !castForm)
                    ? Math.min(1f, hudFadeOut / (float) FADE_OUT)
                    : 1f;
            float alpha = alphaIn * alphaOut;
            if (alpha <= 0.01f) return;

            int icon = 18, gap = 8;
            int totalW = icon * 3 + gap * 2;
            int x0 = (sw - totalW) / 2;
            int y0 = (sh / 2) - 18;

            int bgA = (int) (alpha * 110);
            int borderA = (int) (alpha * 160);
            int bg = (bgA << 24) | 0x0E0E14;
            int border = (borderA << 24) | 0xE4E0FF;

            for (int i = 0; i < 3; i++) {
                int x = x0 + i * (icon + gap);
                ctx.fill(x - 3, y0 - 3, x + icon + 3, y0 + icon + 3, border);
                ctx.fill(x - 2, y0 - 2, x + icon + 2, y0 + icon + 2, bg);
            }

            int shownLen = castForm ? idx : lastLen;

            RenderSystem.enableBlend();
            int ta = (int) (alpha * 255);
            int col = (ta << 24) | 0xE4E0FF;

            for (int i = 0; i < 3; i++) {
                int x = x0 + i * (icon + gap);
                byte d = (i < shownLen) ? (castForm ? combo[i] : lastCombo[i]) : -1;

                String s = switch (d) {
                    case UP -> "↑";
                    case DOWN -> "↓";
                    case LEFT -> "←";
                    case RIGHT -> "→";
                    default -> "·";
                };

                int tx = x + (icon / 2) - (mc.textRenderer.getWidth(s) / 2);
                int ty = y0 + (icon / 2) - (mc.textRenderer.fontHeight / 2);
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), tx, ty, col);
            }

            ctx.drawCenteredTextWithShadow(mc.textRenderer,
                    Text.literal(castForm ? "Cast Form" : "Casting…"),
                    sw / 2, y0 - 14, col);

            RenderSystem.disableBlend();
        });
    }

    public static boolean isCastForm() { return castForm; }

    /* ------------------- cast toggle ------------------- */

    private static void setCast(boolean on) {
        setCastLocal(on, false);

        var b = PacketByteBufs.create();
        b.writeBoolean(on);
        ClientPlayNetworking.send(FairyPower.C2S_TOGGLE_CAST, b);
    }

    private static void setCastLocal(boolean on, boolean keepHud) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (on) forceThirdPerson(mc);
        else restorePerspective(mc);

        castForm = on;
        idx = 0;
        qClear();

        if (on) {
            hudVisible = true;
            hudAge = 0;
            hudFadeOut = 0;
            lastLen = 0;

            if (mc.player != null) nextAcceptTick = mc.player.age + ARM_DELAY;
        } else {
            if (!keepHud) {
                hudVisible = false;
                hudAge = 0;
                hudFadeOut = 0;
                lastLen = 0;
            }
        }

        prevUp = prevDown = prevLeft = prevRight = false;
    }

    private static void forceThirdPerson(MinecraftClient mc) {
        if (mc == null || mc.options == null) return;
        if (!forcedThird) {
            prevPerspective = mc.options.getPerspective();
            forcedThird = true;
        }
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    private static void restorePerspective(MinecraftClient mc) {
        if (!forcedThird) return;
        forcedThird = false;
        if (mc != null && mc.options != null && prevPerspective != null) {
            mc.options.setPerspective(prevPerspective);
        }
        prevPerspective = null;
    }

    /* ------------------- input detect (ARROWS ONLY) ------------------- */

    private static int detectNewDir() {
        boolean up = ARROW_UP.isPressed();
        boolean down = ARROW_DOWN.isPressed();
        boolean left = ARROW_LEFT.isPressed();
        boolean right = ARROW_RIGHT.isPressed();

        if (up && !prevUp) return UP;
        if (down && !prevDown) return DOWN;
        if (left && !prevLeft) return LEFT;
        if (right && !prevRight) return RIGHT;

        return -1;
    }

    private static void updatePrev() {
        prevUp = ARROW_UP.isPressed();
        prevDown = ARROW_DOWN.isPressed();
        prevLeft = ARROW_LEFT.isPressed();
        prevRight = ARROW_RIGHT.isPressed();
    }

    /* ------------------- accept + commit ------------------- */

    private static void accept(byte dir, MinecraftClient client) {
        combo[idx++] = dir;

        if (idx >= 3) {
            lastCombo[0] = combo[0];
            lastCombo[1] = combo[1];
            lastCombo[2] = combo[2];
            lastLen = 3;

            var out = PacketByteBufs.create();
            out.writeByte(combo[0]);
            out.writeByte(combo[1]);
            out.writeByte(combo[2]);
            ClientPlayNetworking.send(FairyPower.C2S_CAST_COMMIT, out);

            setCastLocal(false, true);

            var b2 = PacketByteBufs.create();
            b2.writeBoolean(false);
            ClientPlayNetworking.send(FairyPower.C2S_TOGGLE_CAST, b2);

            hudVisible = true;
            hudAge = FADE_IN;
            hudFadeOut = LINGER + FADE_OUT;

            idx = 0;
            qClear();

            if (client.player != null) nextAcceptTick = client.player.age + 999999;
        }
    }
}
