// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardClientState.java
package net.seep.odd.abilities.wizard.client;

import net.seep.odd.abilities.wizard.WizardElement;

public final class WizardClientState {
    private WizardClientState() {}

    private static boolean hasWizard = false;
    private static float mana = 0f;
    private static float manaMax = 100f;

    // client-side copy (updated via S2C_ELEMENT_SYNC)
    private static WizardElement element = WizardElement.FIRE;

    public static void setMana(boolean has, float m, float max) {
        hasWizard = has;
        mana = m;
        manaMax = max;
        if (!hasWizard) {
            element = WizardElement.FIRE;
        }
    }

    public static boolean hasWizard() { return hasWizard; }
    public static float mana() { return mana; }
    public static float manaMax() { return manaMax; }

    public static void setElement(WizardElement e) {
        element = (e == null) ? WizardElement.FIRE : e;
    }


    /** never null, safe for visuals */
    public static WizardElement getElementSafe() {
        return (element == null) ? WizardElement.FIRE : element;
    }

}
