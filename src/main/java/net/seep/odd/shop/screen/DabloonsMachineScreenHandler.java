package net.seep.odd.shop.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.shop.ShopNetworking;

public final class DabloonsMachineScreenHandler extends ScreenHandler {

    private final BlockPos machinePos;

    // Client constructor (from ExtendedScreenHandlerType)
    public DabloonsMachineScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        this(syncId, inv, buf.readBlockPos());
    }

    // Server constructor
    public DabloonsMachineScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
        super(ModScreenHandlers.DABLOONS_MACHINE, syncId);
        this.machinePos = pos;

        // push catalog + current balance to this player when opened (server side)
        if (!inv.player.getWorld().isClient && inv.player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            ShopNetworking.sendCatalog(sp);
            ShopNetworking.sendBalance(sp);
        }
    }

    public BlockPos getMachinePos() {
        return machinePos;
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
