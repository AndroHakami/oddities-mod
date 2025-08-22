package net.seep.odd.abilities.power;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public interface Power {
    String id();

    /* ===== lifecycle ===== */
    default void onAssigned(ServerPlayerEntity player) {}

    /* ===== primary ===== */
    default void activate(ServerPlayerEntity player) {}
    default long cooldownTicks() { return 0L; }

    /* ===== secondary ===== */
    default void activateSecondary(ServerPlayerEntity player) {}
    default long secondaryCooldownTicks() { return 0L; }

    /* ===== third (new) ===== */
    /** Third-slot activation (e.g., radial wheel). */
    default void activateThird(ServerPlayerEntity player) {}
    /** Third-slot cooldown in ticks (0 = none). */
    default long thirdCooldownTicks() { return 0L; }

    /* ===== fourth (optional) ===== */
    default void activateFourth(ServerPlayerEntity player) {}
    default long fourthCooldownTicks() { return 0L; }

    /* ===== UI bits ===== */
    default String displayName() {
        String s = id().replace('_',' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    default String description() { return ""; }
    default String longDescription() { return description(); }

    default Identifier iconTexture() { return new Identifier("odd","textures/gui/abilities/ability_default.png"); }
    default Identifier iconTexture(String slot) { return iconTexture(); }

    default String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "Primary Ability";
            case "secondary" -> "Secondary Ability";
            case "third" -> "Third Ability";
            case "fourth" -> "Fourth Ability";
            default -> "Ability";
        };
    }
    default String slotDescription(String slot) { return ""; }
    default String slotLongDescription(String slot) { return slotDescription(slot); }

    default Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/player_icon.png");
    }

    /** Powers expose the slots they actually support. */
    default boolean hasSlot(String slot) {
        // By default only primary exists; powers can override to expose more
        return "primary".equals(slot);
    }
}
