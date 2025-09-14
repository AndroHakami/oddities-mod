package net.seep.odd.abilities.power;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.artificer.EssenceStorage;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;

/**
 * Artificer power (minimal, aligned with the current Vacuum-based system).
 *
 * - Primary: If the player is holding the Artificer Vacuum, shows a quick
 *   summary of stored essences + a hint to hold RMB to siphon.
 * - Secondary: Purge the vacuum tank (clears all stored essences in the held Vacuum).
 *
 * NOTE: Extraction logic, GeckoLib animations, and HUD are handled by the Vacuum item
 * (ArtificerVacuumItem) and client HUD hook; this power deliberately avoids hard
 * dependencies on old vial/potion systems you removed.
 */
public final class ArtificerPower implements Power {

    @Override public String id() { return "artificer"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    // No CD for primary info; small CD on purge to avoid spam.
    @Override public long cooldownTicks()          { return 0; }
    @Override public long secondaryCooldownTicks() { return 5L * 20L; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/artificer_primary.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/artificer_secondary.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Status + hint: if you’re holding the Artificer Vacuum, shows stored essence amounts. "
                            + "Hold right-click with the Vacuum to siphon.";
            case "secondary" ->
                    "Maintenance: purge the Vacuum’s internal tank (clears all stored essences).";
            case "overview" ->
                    "Harnesses Light, Gaia, Hot, Cold, Death, and Life essences with a custom vacuum tool.";
            default -> "Artificer channels world essences through crafted instruments.";
        };
    }

    @Override
    public String longDescription() {
        return """
               The Artificer extracts raw Essences with a custom vacuum and stores them in the tool.
               Use those Essences later for crafting and effects. Hold right-click with the Vacuum to siphon.
               """;
    }

    /* ===================== PRIMARY ===================== */

    @Override
    public void activate(ServerPlayerEntity player) {
        ItemStack vac = getHeldVacuum(player);
        if (vac.isEmpty()) {
            player.sendMessage(Text.literal("Hold the Artificer Vacuum to extract essences."), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
            return;
        }

        int cap   = EssenceStorage.getCapacity(vac);
        int total = EssenceStorage.total(vac);

        StringBuilder sb = new StringBuilder();
        sb.append("Tank ").append(total).append("/").append(cap);

        // List any non-zero essences
        boolean any = false;
        for (EssenceType t : EssenceType.values()) {
            int v = EssenceStorage.get(vac, t);
            if (v > 0) {
                any = true;
                sb.append("  • ").append(t.key).append(": ").append(v);
            }
        }
        if (!any) sb.append("  • (empty)");

        player.sendMessage(Text.literal(sb.toString()), true);
        player.playSound(SoundEvents.BLOCK_BEACON_AMBIENT, 0.5f, 1.35f);
    }

    /* ===================== SECONDARY ===================== */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ItemStack vac = getHeldVacuum(player);
        if (vac.isEmpty()) {
            player.sendMessage(Text.literal("Hold the Artificer Vacuum to manage storage."), true);
            return;
        }

        for (EssenceType t : EssenceType.values()) EssenceStorage.set(vac, t, 0);
        player.sendMessage(Text.literal("Artificer Vacuum: storage purged."), true);
        player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.3f);
    }

    /* ===================== Helpers ===================== */

    private static ItemStack getHeldVacuum(ServerPlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        if (main.getItem() instanceof ArtificerVacuumItem) return main;
        ItemStack off = p.getOffHandStack();
        if (off.getItem() instanceof ArtificerVacuumItem) return off;
        return ItemStack.EMPTY;
    }

    // No per-tick logic needed; extraction lives on the item. Present for parity if your engine calls it.
    public static void serverTickBridge(ServerPlayerEntity p) { /* no-op */ }
}
