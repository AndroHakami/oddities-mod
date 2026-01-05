package net.seep.odd.abilities.fairy;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.falseflower.FalseFlowerTracker;

/**
 * Server-side entry to open the Manage Flowers screen.
 * Sends a fresh snapshot first, then a tiny "open" packet.
 */
public final class FlowerMenu {
    private FlowerMenu() {}

    /** S2C: tells client to open ManageFlowersScreen. */
    public static final Identifier S2C_OPEN = new Identifier(Oddities.MOD_ID, "fairy/flower_menu_open");

    public static void openFor(ServerPlayerEntity player) {
        // 1) push the latest flower list to client (FalseFlowerTracker must implement this)
        FalseFlowerTracker.sendSnapshot(player);

        // 2) instruct client to open the UI
        ServerPlayNetworking.send(player, S2C_OPEN, PacketByteBufs.create());
    }
}
