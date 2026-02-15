// FILE: src/main/java/net/seep/odd/abilities/wizard/WizardCombo.java
package net.seep.odd.abilities.wizard;

public enum WizardCombo {
    STEAM_CLOUD(0, "Steam Cloud"),
    FIRE_TORNADO(1, "Fire Tornado"),
    LIFE_RESTORATION(2, "Life Restoration"),
    SONIC_SCREECH(3, "Sonic Screech"),
    SWAPPERINO(4, "Swapperino"),
    METEOR_STRIKE(5, "Meteor Strike");

    public final int id;
    public final String displayName;

    WizardCombo(int id, String name) {
        this.id = id;
        this.displayName = name;
    }

    public static WizardCombo fromId(int id) {
        for (WizardCombo c : values()) if (c.id == id) return c;
        return STEAM_CLOUD;
    }

    /** Only Sonic Screech has a charge-up right now */
    public boolean requiresCharge() {
        return this == SONIC_SCREECH;
    }
}
