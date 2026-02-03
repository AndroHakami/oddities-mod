package net.seep.odd.abilities.druid.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class DruidClientState {
    private DruidClientState() {}

    private static String formKey = "human";

    public static void setFormKey(String key) {
        formKey = (key == null || key.isBlank()) ? "human" : key;
    }

    public static boolean isShifted() {
        return !"human".equals(formKey);
    }

    public static String formKey() {
        return formKey;
    }
}
