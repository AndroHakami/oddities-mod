package net.seep.odd.device.store.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public final class DabloonStoreScreenHandler extends ScreenHandler {
    private final BlockPos pos;

    public DabloonStoreScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        this(syncId, inventory, buf.readBlockPos());
    }

    public DabloonStoreScreenHandler(int syncId, PlayerInventory inventory, BlockPos pos) {
        super(StoreScreenHandlers.DABLOON_STORE, syncId);
        this.pos = pos.toImmutable();
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return null;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
