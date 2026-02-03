package net.seep.odd.abilities.vampire.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.abilities.power.VampirePower;

@Environment(EnvType.CLIENT)
public final class VampireClientFlag {
    private VampireClientFlag() {}

    private static volatile boolean HAS = false;

    private static volatile boolean SHOW_HUD_BAR = true;
    private static volatile float BLOOD = 100f;
    private static volatile float MAX = 100f;

    private static volatile boolean FRENZY = false;

    public static boolean hasVampire() { return HAS; }
    public static boolean showHudBar() { return SHOW_HUD_BAR; }
    public static boolean frenzy() { return FRENZY; }

    public static float blood() { return BLOOD; }
    public static float max() { return MAX <= 0.001f ? 100f : MAX; }

    public static float pct() {
        float m = max();
        return Math.max(0f, Math.min(1f, BLOOD / m));
    }

    public static void init() {
        VampireFrenzyFx.init();

        ClientPlayNetworking.registerGlobalReceiver(VampirePower.S2C_VAMPIRE_FLAG, (client, handler, buf, resp) -> {
            boolean v = buf.readBoolean();
            client.execute(() -> HAS = v);
        });

        ClientPlayNetworking.registerGlobalReceiver(VampirePower.S2C_BLOOD_SYNC, (client, handler, buf, resp) -> {
            boolean has = buf.readBoolean();
            boolean showHud = buf.readBoolean();
            float blood = buf.readFloat();
            float max = buf.readFloat();

            client.execute(() -> {
                HAS = has;
                SHOW_HUD_BAR = showHud;
                BLOOD = blood;
                MAX = max;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(VampirePower.S2C_FRENZY_STATE, (client, handler, buf, resp) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> {
                FRENZY = on;
                VampireFrenzyFx.setActive(on);
            });
        });
    }
}
