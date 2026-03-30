// FILE: src/main/java/net/seep/odd/abilities/power/ArtificerPower.java
package net.seep.odd.abilities.power;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.artificer.vialmatrix.VialMatrixInventory;
import net.seep.odd.abilities.artificer.vialmatrix.VialMatrixScreenHandler;

import net.minecraft.screen.SimpleNamedScreenHandlerFactory;

/**
 * Artificer power
 *
 * - Primary: Vial Matrix — opens a 3x3 storage that persists on death.
 *            Only accepts Potions + Essence Buckets.
 * - Secondary: removed
 */
public final class ArtificerPower implements Power {

    @Override public String id() { return "artificer"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot); // ✅ secondary removed
    }

    @Override public long cooldownTicks()          { return 10; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/vial_matrix.png");
            default        -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }
    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "VIAL MATRIX";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "opens a 3×3 storage that persists on death.";
            case "overview" ->
                    "Artificer maintains a death-persistent potion/essence cache (Vial Matrix).";
            default -> "Artificer channels world essences through crafted instruments.";
        };
    }

    @Override
    public String longDescription() {
        return """
               The world is yours to reshape, use the vacuum to extact essences and build the potion mixer 
               to brew miracles.
               """;
    }

    /* ===================== PRIMARY ===================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new VialMatrixScreenHandler(syncId, playerInv, VialMatrixInventory.forPlayer(player)),
                Text.literal("Vial Matrix")
        ));

        // keep your SoundEvents style consistent with your project
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
    }

    /* ===================== SECONDARY (REMOVED) ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // no-op (slot removed)
    }

    public static void serverTickBridge(ServerPlayerEntity p) { /* no-op */ }
}