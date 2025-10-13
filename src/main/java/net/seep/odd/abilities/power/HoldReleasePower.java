package net.seep.odd.abilities.power;

import net.minecraft.server.network.ServerPlayerEntity;

/** Optional: implement to receive hold/tick/release callbacks per slot. */
public interface HoldReleasePower {
    default void onHoldStart(ServerPlayerEntity player, String slot) {}
    default void onHoldTick(ServerPlayerEntity player, String slot, int heldTicks) {}
    default void onHoldRelease(ServerPlayerEntity player, String slot, int heldTicks, boolean canceled) {}
}
