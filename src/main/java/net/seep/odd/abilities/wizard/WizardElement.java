// FILE: src/main/java/net/seep/odd/abilities/wizard/WizardElement.java
package net.seep.odd.abilities.wizard;

public enum WizardElement {
    FIRE(0, "Fire"),
    WATER(1, "Water"),
    AIR(2, "Air"),
    EARTH(3, "Earth");

    public final int id;
    public final String displayName;

    WizardElement(int id, String name) {
        this.id = id;
        this.displayName = name;
    }

    public static WizardElement fromId(int id) {
        return switch (id) {
            case 1 -> WATER;
            case 2 -> AIR;
            case 3 -> EARTH;
            default -> FIRE;
        };
    }
}
