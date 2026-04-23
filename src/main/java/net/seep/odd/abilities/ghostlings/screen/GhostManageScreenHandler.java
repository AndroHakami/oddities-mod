package net.seep.odd.abilities.ghostlings.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.registry.GhostRegistries;

public class GhostManageScreenHandler extends ScreenHandler {
    public final int ghostEntityId;
    public final GhostlingEntity.Job ghostJob;

    public GhostManageScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        this(syncId, inv, buf.readVarInt(), buf.readEnumConstant(GhostlingEntity.Job.class));
    }

    public GhostManageScreenHandler(int syncId, PlayerInventory inv, int ghostEntityId, GhostlingEntity.Job ghostJob) {
        super(GhostRegistries.GHOST_MANAGE_HANDLER, syncId);
        this.ghostEntityId = ghostEntityId;
        this.ghostJob = ghostJob;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
