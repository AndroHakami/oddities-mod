package net.seep.odd.abilities.ghostlings.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.seep.odd.abilities.ghostlings.registry.GhostScreens;

public class GhostManageScreenHandler extends ScreenHandler {
    public final int ghostEntityId;

    public GhostManageScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        this(syncId, inv, buf.readVarInt());
    }

    public GhostManageScreenHandler(int syncId, PlayerInventory inv, int ghostEntityId) {
        super(GhostScreens.GHOST_MANAGE_HANDLER, syncId);
        this.ghostEntityId = ghostEntityId;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return null;
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }
}
