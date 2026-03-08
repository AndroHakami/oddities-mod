// FILE: src/main/java/net/seep/odd/abilities/rider/RiderNet.java
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
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.entity.car.RiderCarEntity;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create;

public final class RiderNet {
    private RiderNet() {}

    public static final Identifier INPUT = new Identifier("odd", "rider/input");
    public static final Identifier RADIO = new Identifier("odd", "rider/radio");

    public enum RadioCmd { TOGGLE, NEXT, PREV, VOLUME_SET }

    /* ===== server ===== */
    public static void registerServer() {
        // movement input
        ServerPlayNetworking.registerGlobalReceiver(INPUT, (server, player, handler, buf, rs) -> {
            float throttle = buf.readFloat();  // -1..1
            float steer    = buf.readFloat();  // -1..1
            boolean drift  = buf.readBoolean();
            boolean honk   = buf.readBoolean();
            boolean jump   = buf.readBoolean();

            server.execute(() -> {
                // ✅ POWERLESS: ignore controls and kick them out of the car
                if (player.hasStatusEffect(ModStatusEffects.POWERLESS)) {
                    if (player.hasVehicle()) player.stopRiding();
                    return;
                }

                if (player.getVehicle() instanceof RiderCarEntity car && car.isDriver(player)) {
                    car.applyDriverInput(throttle, steer, drift, honk, jump);
                }
            });
        });

        // radio control receiver
        ServerPlayNetworking.registerGlobalReceiver(RADIO, (server, player, handler, buf, rs) -> {
            RadioCmd cmd = buf.readEnumConstant(RadioCmd.class);
            float value = (cmd == RadioCmd.VOLUME_SET) ? buf.readFloat() : 0f;

            server.execute(() -> {
                // ✅ POWERLESS: block radio controls too
                if (player.hasStatusEffect(ModStatusEffects.POWERLESS)) return;

                if (!(player.getVehicle() instanceof RiderCarEntity car)) return;
                switch (cmd) {
                    case TOGGLE     -> car.serverRadioToggle();
                    case NEXT       -> car.serverRadioNext();
                    case PREV       -> car.serverRadioPrev();
                    case VOLUME_SET -> car.serverRadioSetVolume(MathHelper.clamp(value, 0f, 1f));
                }
            });
        });
    }

    /* ===== client ===== */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static KeyBinding KB_HONK;

        // ✅ one looping engine sound per car (by UUID)
        private static final Map<UUID, RiderEngineLoop> ENGINE = new HashMap<>();

        public static void init() {
            KB_HONK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.odd.honk", GLFW.GLFW_KEY_R, "key.categories.odd"));

            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc.player == null) return;

                // send controls only when in car
                if (mc.player.getVehicle() instanceof RiderCarEntity car) {
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
                }

                // ✅ fix “sound drops behind the car”: moving loop sound per nearby car
                tickEngineLoops(mc);
            });

            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> drawHud(ctx));
        }

        private static void tickEngineLoops(MinecraftClient mc) {
            if (mc.world == null || mc.player == null) {
                // stop everything cleanly
                for (RiderEngineLoop loop : ENGINE.values()) loop.forceStop();
                ENGINE.clear();
                return;
            }

            double r = 64.0;
            Box box = mc.player.getBoundingBox().expand(r);

            HashMap<UUID, RiderCarEntity> seen = new HashMap<>();
            for (RiderCarEntity car : mc.world.getEntitiesByClass(RiderCarEntity.class, box, e -> e != null && e.isAlive())) {
                seen.put(car.getUuid(), car);

                double speed = car.getVelocity().horizontalLength();
                boolean active = speed > 0.02 || car.hasPassengers();

                RiderEngineLoop loop = ENGINE.get(car.getUuid());
                if (active) {
                    if (loop == null) {
                        loop = new RiderEngineLoop(car);
                        ENGINE.put(car.getUuid(), loop);
                        mc.getSoundManager().play(loop);
                    } else {
                        loop.setCar(car);
                    }
                } else {
                    if (loop != null) {
                        loop.forceStop();
                        ENGINE.remove(car.getUuid());
                    }
                }
            }

            Iterator<Map.Entry<UUID, RiderEngineLoop>> it = ENGINE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, RiderEngineLoop> en = it.next();
                UUID id = en.getKey();
                RiderEngineLoop loop = en.getValue();

                RiderCarEntity car = seen.get(id);
                if (car == null || !car.isAlive()) {
                    loop.forceStop();
                    it.remove();
                }
            }
        }

        /** Moving loop sound that follows the car every tick (same principle as your radio). */
        private static final class RiderEngineLoop extends MovingSoundInstance {
            private RiderCarEntity car;
            private boolean stopped = false;

            private RiderEngineLoop(RiderCarEntity car) {
                super(ModSounds.CAR_ACC != null ? ModSounds.CAR_ACC : SoundEvents.ENTITY_MINECART_RIDING,
                        SoundCategory.NEUTRAL, car.getRandom());
                this.car = car;

                this.repeat = true;
                this.repeatDelay = 0;

                this.x = car.getX();
                this.y = car.getY();
                this.z = car.getZ();
                this.volume = 0.18f;
                this.pitch = 0.95f;
            }

            public void setCar(RiderCarEntity car) { this.car = car; }

            public void forceStop() {
                this.stopped = true;
                this.setDone();
            }

            @Override
            public void tick() {
                if (stopped || car == null || !car.isAlive()) {
                    this.setDone();
                    return;
                }

                this.x = car.getX();
                this.y = car.getY();
                this.z = car.getZ();

                double spd = car.getVelocity().horizontalLength(); // blocks/tick
                float v = (float) MathHelper.clamp(0.14 + spd * 2.3, 0.14, 0.85);
                float p = (float) MathHelper.clamp(0.85 + spd * 1.7, 0.85, 1.35);

                var me = MinecraftClient.getInstance().player;
                if (me != null && me.getVehicle() == car) v = Math.min(1.0f, v + 0.10f);

                this.volume = v;
                this.pitch = p;
            }
        }

        private static void drawHud(DrawContext ctx) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || !(mc.player.getVehicle() instanceof RiderCarEntity car)) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            double spd = car.getVelocity().length() * 20.0;
            String speedTxt = String.format("SPD %.1f", spd);
            int x = sw / 2 - 80, y = sh - 58;
            ctx.fill(x - 4, y - 4, x + 60, y + 10, 0x66000000);
            ctx.drawText(mc.textRenderer, speedTxt, x, y, 0xFFEAEAEA, true);

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

            if (car.isRadioOnClient()) {
                String name = car.getRadioTrackNameClient();
                int volPct  = Math.round(car.getRadioVolumeClient() * 100f);
                String now  = "Playing " + (name == null ? "<unknown>" : name) + "  (" + volPct + "%)";
                int w = mc.textRenderer.getWidth(now) + 12;
                int rx = (sw - w) / 2;
                int ry = 8;
                ctx.fill(rx, ry, rx + w, ry + 14, 0x66000000);
                ctx.drawText(mc.textRenderer, now, rx + 6, ry + 3, 0xFFE7FFC2, true);
            }
        }
    }
}