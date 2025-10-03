package net.seep.odd.abilities.ghostlings.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.seep.odd.abilities.ghostlings.registry.GhostRegistries;

public class GhostDashboardScreenHandler extends ScreenHandler {
    public GhostDashboardScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(GhostRegistries.GHOST_DASH_HANDLER, syncId);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return null;
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }
}
