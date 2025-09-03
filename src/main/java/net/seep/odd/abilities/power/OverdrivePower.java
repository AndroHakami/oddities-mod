package net.seep.odd.abilities.power;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.overdrive.OverdriveSystem;

/**
 * Overdrive as a standard 'Power' so it plugs into your existing ability buttons.
 * Primary  = Toggle Energized (on/off)
 * Secondary= If meter full -> enter Overdrive; otherwise toggle Kinetic Relay aura.
 */
public final class OverdrivePower implements Power {
    @Override public String id() { return "overdrive"; }

    @Override public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    // No built-in cooldowns on the buttons (server logic drains/limits usage)
    @Override public long cooldownTicks()          { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/overdrive_toggle.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/overdrive_burst.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Energized Mode — unlocks parkour flow and special attacks. Drains the meter while active.";
            case "secondary" ->
                    "If your meter is full: enter OVERDRIVE.\nOtherwise: toggle Kinetic Relay to grant nearby allies speed & jump.";
            case "overview" ->
                    "Build kinetic energy while you move and fight. Toggle Energized for mobility, "
                            + "share power with allies via Relay, and unleash OVERDRIVE when the bar is full.";
            default -> "";
        };
    }

    @Override
    public String longDescription() {
        return """
            Stores kinetic energy from movement and combat, then spends it on enhanced mobility,
            ally buffs, and a short, explosive OVERDRIVE state when full.
            """;
    }

    /* ========== Ability buttons ========== */

    // Primary: toggle Energized mode
    @Override
    public void activate(ServerPlayerEntity player) {
        OverdriveSystem.toggleEnergized(player);
    }

    // Secondary: if full meter -> Overdrive; else toggle Relay ON/OFF
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (OverdriveSystem.hasFullMeter(player) && !OverdriveSystem.isInOverdrive(player)) {
            OverdriveSystem.tryOverdrive(player);
            return;
        }
        boolean turnOn = !OverdriveSystem.isRelayActive(player);
        OverdriveSystem.setRelay(player, turnOn);
    }
}
