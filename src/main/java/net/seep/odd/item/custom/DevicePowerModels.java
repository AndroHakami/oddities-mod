package net.seep.odd.item.custom;

import java.util.HashMap;
import java.util.Map;

public final class DevicePowerModels {
    private static final Map<String, Integer> POWER_TO_MODEL_DATA = new HashMap<>();

    static {
        register(1,  "accelerate");
        register(2,  "artificer");
        register(3,  "buddymorph");
        register(4,  "chef");
        register(5,  "climber");
        register(6,  "cosmic");
        register(7,  "cultist");
        register(8,  "druid");
        register(9,  "fairy");
        register(10, "falling_snow");
        register(11, "fire_sword");
        register(12, "forger");
        register(13, "gamble");
        register(14, "ghostlings");
        register(15, "glitch");
        register(16, "icewitch");
        register(17, "looker");
        register(18, "lunar");
        register(19, "misty_veil");
        register(20, "necromancer");
        register(21, "owl");
        register(22, "rat");
        register(23, "rider");
        register(24, "rise");
        register(25, "sniper");
        register(26, "splash");
        register(27, "supercharge");
        register(28, "umbra_soul");
        register(29, "vampire");
        register(30, "wizard");
        register(31, "zerosuit");
        register(32, "conquer");
        register(33, "blockade");
        register(34, "sun");
    }

    private DevicePowerModels() {}

    private static void register(int customModelData, String powerId) {
        POWER_TO_MODEL_DATA.put(normalize(powerId), customModelData);
    }

    public static boolean hasModelForPower(String powerId) {
        return getCustomModelDataForPower(powerId) != 0;
    }

    public static int getCustomModelDataForPower(String powerId) {
        if (powerId == null || powerId.isBlank()) {
            return 0;
        }
        return POWER_TO_MODEL_DATA.getOrDefault(normalize(powerId), 0);
    }

    private static String normalize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                out.append(c);
            }
        }
        return out.toString();
    }
}