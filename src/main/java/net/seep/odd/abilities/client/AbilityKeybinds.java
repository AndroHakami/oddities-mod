package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.seep.odd.abilities.net.AbilityKeyPacket;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.HoldReleasePower;
import org.lwjgl.glfw.GLFW;

public final class AbilityKeybinds {
    private static boolean initialized = false;

    private static KeyBinding primary;
    private static KeyBinding secondary;
    private static KeyBinding third;
    private static KeyBinding fourth;
    private static KeyBinding overview;

    // client-side hold state for the secondary key
    private static boolean secHolding = false;
    private static int secHeldTicks = 0;

    private AbilityKeybinds() {}

    /** Register keybinds + client tick handler (idempotent). */
    public static void register() {
        if (initialized) return;
        initialized = true;

        primary   = registerKey("primary_ability",   GLFW.GLFW_KEY_Z);
        secondary = registerKey("secondary_ability", GLFW.GLFW_KEY_X);
        third     = registerKey("third_ability",     GLFW.GLFW_KEY_C);
        fourth    = registerKey("fourth_ability",    GLFW.GLFW_KEY_V);
        overview  = registerKey("ability_overview",  GLFW.GLFW_KEY_B);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Overview UI open (local only)
            if (overview.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new net.seep.odd.abilities.client.AbilityOverviewScreen());
                }
                return; // don't send a packet for overview
            }

            // PRIMARY / THIRD / FOURTH stay as simple presses
            if (primary.wasPressed()) sendPress("primary");
            if (third.wasPressed())   sendPress("third");
            if (fourth.wasPressed())  sendPress("fourth");

            // SECONDARY: if current power supports hold, run hold lifecycle; else fall back to press
            boolean useHoldForSecondary = currentPowerSupportsSecondaryHold();

            if (useHoldForSecondary) {
                boolean pressed = secondary.isPressed();
                if (pressed && !secHolding) {
                    secHolding = true;
                    secHeldTicks = 0;
                    ClientPlayNetworking.send(AbilityKeyPacket.HOLD_START,
                            AbilityKeyPacket.makeHoldStartBuf("secondary"));
                    // local HUD fade
                    AbilityHudOverlay.ClientHeldState.set("secondary", true);
                }

                if (pressed && secHolding) {
                    secHeldTicks++;
                    ClientPlayNetworking.send(AbilityKeyPacket.HOLD_TICK,
                            AbilityKeyPacket.makeHoldTickBuf("secondary", secHeldTicks));
                }

                if (!pressed && secHolding) {
                    ClientPlayNetworking.send(AbilityKeyPacket.HOLD_RELEASE,
                            AbilityKeyPacket.makeHoldReleaseBuf("secondary", secHeldTicks, false));
                    AbilityHudOverlay.ClientHeldState.set("secondary", false);
                    secHolding = false;
                    secHeldTicks = 0;
                }
            } else {
                // Fallback: old behavior
                if (secondary.wasPressed()) sendPress("secondary");
            }
        });
    }

    private static boolean currentPowerSupportsSecondaryHold() {
        // Ask client-side registry what power is active and whether it implements HoldReleasePower
        String powerId = ClientPowerHolder.get();
        var p = Powers.get(powerId);
        return p instanceof HoldReleasePower && p.hasSlot("secondary");
    }

    private static KeyBinding registerKey(String name, int defaultKey) {
        // Translation keys: key.oddities.<name>, category.oddities.abilities
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.oddities." + name,
                InputUtil.Type.KEYSYM,
                defaultKey,
                "category.oddities.abilities"
        ));
    }

    private static void sendPress(String slot) {
        ClientPlayNetworking.send(AbilityKeyPacket.ID, AbilityKeyPacket.makeBuf(slot));
    }

    /** Human-readable key name for HUD labels (e.g., "Z", "Mouse5"). */
    public static String boundKeyName(String slot) {
        KeyBinding kb = switch (slot) {
            case "primary"   -> primary;
            case "secondary" -> secondary;
            case "third"     -> third;
            case "fourth"    -> fourth;
            case "overview"  -> overview;
            default -> null;
        };
        return kb == null ? "" : kb.getBoundKeyLocalizedText().getString();
    }

    // Optional getters if you need to expose the bindings elsewhere
    public static KeyBinding primary()   { return primary; }
    public static KeyBinding secondary() { return secondary; }
    public static KeyBinding third()     { return third; }
    public static KeyBinding fourth()    { return fourth; }
    public static KeyBinding overview()  { return overview; }
}
