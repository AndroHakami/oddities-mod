package net.seep.odd.abilities.power;

import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;

public interface Power {
    String id();
    default void onAssigned(ServerPlayerEntity player) {}
    default void activate(ServerPlayerEntity player) {}
    default long cooldownTicks() { return 0L; }

    default void activateSecondary(ServerPlayerEntity player) {}
    default long secondaryCooldownTicks() { return 0L; }

    // UI bits...
    default String displayName() { String s = id().replace('_',' '); return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    default String description() { return ""; }                 // short blurb (optional)
    default String longDescription() { return description(); }   // <<< long, multi-paragraph text

    default Identifier iconTexture() { return new Identifier("odd","textures/gui/abilities/ability_default.png"); }
    default Identifier iconTexture(String slot) { return iconTexture(); }
    default String slotTitle(String slot) { /* same as before */ return switch (slot) {
        case "primary" -> "Primary Ability";
        case "secondary" -> "Secondary Ability";
        case "third" -> "Third Ability";
        case "fourth" -> "Fourth Ability";
        default -> "Ability";
    }; }
    default String slotDescription(String slot) { return ""; }
    default String slotLongDescription(String slot) {
        return slotDescription(slot); // <- String → String, OK
    }
    default Identifier portraitTexture() {
        // Fallback portrait if a power doesn't override it
        return new Identifier("odd", "textures/gui/overview/player_icon.png");
    }
    default boolean hasSlot(String slot) {
        // By default only primary exists; powers can override to expose more
        return "primary".equals(slot);
    }
}


