package net.seep.odd.abilities.power;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.voids.VoidSystem;

public final class VoidPower implements Power {
    @Override public String id() { return "void"; }
    @Override public String displayName() { return "Void"; }
    @Override public String description() { return "Conjure a rift to your pocket Void island; press again inside to return."; }
    @Override public String longDescription() {
        return """
            Open a rift that teleports anyone entering it to your tiny basalt-delta island.
            While inside the Void, press the button again to return to the exact spot and dimension you left.""";
    }

    @Override public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/void_portrait.png");
    }
    @Override public String slotTitle(String slot) { return "primary".equals(slot) ? "Rift" : Power.super.slotTitle(slot); }
    @Override public String slotDescription(String slot) { return "primary".equals(slot) ? "Conjure portal / Return from Void" : ""; }

    @Override public void activate(ServerPlayerEntity player) { VoidSystem.onPrimary(player); }

    @Override public long cooldownTicks() { return 100; }
    @Override public long secondaryCooldownTicks() { return 0; }
}
