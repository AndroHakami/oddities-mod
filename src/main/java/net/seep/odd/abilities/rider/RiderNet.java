package net.seep.odd.abilities.rider;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.server.network.ServerPlayerEntity;

import net.seep.odd.entity.car.RiderCarEntity;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create;

public final class RiderNet {
    private RiderNet() {}

    public static final Identifier INPUT = new Identifier("odd", "rider/input");

    // NEW: radio control channel
    public static final Identifier RADIO = new Identifier("odd", "rider/radio");

    // NEW: small enum for radio commands
    public enum RadioCmd { TOGGLE, NEXT, PREV, VOLUME_SET }

    /* ===== server ===== */
    public static void registerServer() {
        // Existing: movement input
        ServerPlayNetworking.registerGlobalReceiver(INPUT, (server, player, handler, buf, rs) -> {
            float throttle = buf.readFloat();  // -1..1
            float steer    = buf.readFloat();  // -1..1
            boolean drift  = buf.readBoolean();
            boolean honk   = buf.readBoolean();
            boolean jump   = buf.readBoolean();

            server.execute(() -> {
                if (player.getVehicle() instanceof RiderCarEntity car && car.isDriver(player)) {
                    car.applyDriverInput(throttle, steer, drift, honk, jump);
                }
            });
        });

        // NEW: radio control receiver
        ServerPlayNetworking.registerGlobalReceiver(RADIO, (server, player, handler, buf, rs) -> {
            RadioCmd cmd = buf.readEnumConstant(RadioCmd.class);
            float value = (cmd == RadioCmd.VOLUME_SET) ? buf.readFloat() : 0f;

            server.execute(() -> {
                if (!(player.getVehicle() instanceof RiderCarEntity car)) return;
                switch (cmd) {
                    case TOGGLE    -> car.serverRadioToggle();
                    case NEXT      -> car.serverRadioNext();
                    case PREV      -> car.serverRadioPrev();
                    case VOLUME_SET-> car.serverRadioSetVolume(MathHelper.clamp(value, 0f, 1f));
                }
            });
        });
    }

    /* ===== client ===== */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static KeyBinding KB_HONK;

        public static void init() {
            // (movement/honk input kept as-is)
            KB_HONK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.odd.honk", GLFW.GLFW_KEY_R, "key.categories.odd"));

            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc.player == null) return;
                if (!(mc.player.getVehicle() instanceof RiderCarEntity car)) return;

                boolean forward = mc.options.forwardKey.isPressed();
                boolean back    = mc.options.backKey.isPressed();
                boolean left    = mc.options.leftKey.isPressed();
                boolean right   = mc.options.rightKey.isPressed();
                boolean drift   = mc.options.sprintKey.isPressed() || mc.options.sneakKey.isPressed(); // CTRL (either)
                boolean jump    = mc.options.jumpKey.isPressed();
                boolean honk    = KB_HONK.wasPressed();

                float throttle = (forward ? 1f : 0f) + (back ? -1f : 0f);
                throttle = MathHelper.clamp(throttle, -1f, 1f);
                float steer = (right ? 1f : 0f) + (left ? -1f : 0f);
                steer = MathHelper.clamp(steer, -1f, 1f);

                PacketByteBuf b = create();
                b.writeFloat(throttle);
                b.writeFloat(steer);
                b.writeBoolean(drift);
                b.writeBoolean(honk);
                b.writeBoolean(jump);
                ClientPlayNetworking.send(INPUT, b);
            });

            // HUD
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> drawHud(ctx));
        }

        private static void drawHud(DrawContext ctx) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || !(mc.player.getVehicle() instanceof RiderCarEntity car)) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            // SPEED (kept)
            double spd = car.getVelocity().length() * 20.0;
            String speedTxt = String.format("SPD %.1f", spd);
            int x = sw / 2 - 80, y = sh - 58;
            ctx.fill(x - 4, y - 4, x + 60, y + 10, 0x66000000);
            ctx.drawText(mc.textRenderer, speedTxt, x, y, 0xFFEAEAEA, true);

            // DRIFT BOOST (kept)
            boolean drifting = car.isDriftingClient() || car.getDriftTicksClient() > 0;
            if (drifting) {
                int ticks = car.getDriftTicksClient();
                int lvl = ticks >= 60 ? 3 : ticks >= 34 ? 2 : ticks >= 18 ? 1 : 0;
                String label = switch (lvl) { case 3 -> "DRIFT III"; case 2 -> "DRIFT II"; case 1 -> "DRIFT I"; default -> "DRIFT"; };
                int w = 68;
                int bx = sw / 2 + 20, by = sh - 58;
                ctx.fill(bx - 4, by - 4, bx + w, by + 10, 0x66000000);
                int color = lvl == 3 ? 0xFFFFAA00 : (lvl == 2 ? 0xFF66A9FF : (lvl == 1 ? 0xFFFF6666 : 0xFFEAEAEA));
                ctx.drawText(mc.textRenderer, label, bx, by, color, true);
            }

            // NEW: Radio HUD — always show while radio is ON
            if (car.isRadioOnClient()) {
                String name = car.getRadioTrackNameClient();              // title you set when registering
                int volPct  = Math.round(car.getRadioVolumeClient() * 100f);
                String now  = "Playing " + (name == null ? "<unknown>" : name) + "  (" + volPct + "%)";
                // center-top stripe
                int w = mc.textRenderer.getWidth(now) + 12;
                int rx = (sw - w) / 2;
                int ry = 8;
                ctx.fill(rx, ry, rx + w, ry + 14, 0x66000000);
                ctx.drawText(mc.textRenderer, now, rx + 6, ry + 3, 0xFFE7FFC2, true);
                //^ keep drawing every frame while the track loops
            }
        }
    }
}
