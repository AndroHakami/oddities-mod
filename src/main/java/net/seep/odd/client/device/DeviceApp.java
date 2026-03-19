package net.seep.odd.client.device;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public enum DeviceApp {
    NOTES        ("notes",         "Notes",         "textures/gui/device/apps/notes.png",   38,  34),
    INFO         ("info",          "Info",          "textures/gui/device/apps/info.png",    138, 34),
    DABLOON_BANK ("dabloon_bank",  "Dabloon Bank",  "textures/gui/device/apps/dabloon.png", 38,  120),
    GUILD        ("guild",         "Guild",         "textures/gui/device/apps/team.png",    138, 120),
    SOCIAL       ("social",        "Social",        "textures/gui/device/apps/social.png",  38,  206),
    STORE        ("store",         "Store",         "textures/gui/device/apps/shop.png",    138, 206);

    private final String id;
    private final String title;
    private final Identifier texture;
    private final int x;
    private final int y;

    DeviceApp(String id, String title, String texturePath, int x, int y) {
        this.id = id;
        this.title = title;
        this.texture = new Identifier(Oddities.MOD_ID, texturePath);
        this.x = x;
        this.y = y;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Identifier texture() {
        return texture;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }
}