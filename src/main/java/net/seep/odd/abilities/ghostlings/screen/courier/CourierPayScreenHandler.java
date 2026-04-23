// FILE: net/seep/odd/abilities/ghostlings/screen/courier/CourierPayScreenHandler.java
package net.seep.odd.abilities.ghostlings.screen.courier;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.ghostlings.registry.GhostRegistries;

public class CourierPayScreenHandler extends ScreenHandler {
    private final SimpleInventory inv = new SimpleInventory(1);
    public final int ghostId;
    public final BlockPos target;
    public final int tearsNeeded;

    public CourierPayScreenHandler(int syncId, PlayerInventory playerInv, PacketByteBuf buf) {
        this(syncId, playerInv, buf.readVarInt(), buf.readBlockPos(), buf.readVarInt());
    }

    public CourierPayScreenHandler(int syncId, PlayerInventory playerInv, int ghostId, BlockPos target, int tearsNeeded) {
        super(GhostRegistries.COURIER_PAY_HANDLER, syncId);
        this.ghostId = ghostId;
        this.target = target;
        this.tearsNeeded = tearsNeeded;

        this.addSlot(new Slot(inv, 0, 80, 35) {
            @Override public boolean canInsert(ItemStack stack) { return stack != null && stack.isOf(Items.GHAST_TEAR); }
            @Override public int getMaxItemCount() { return tearsNeeded; }
        });

        // player inventory
        int m; int l;
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInv, l + m * 9 + 9, 8 + l * 18, 84 + m * 18));
            }
        }
        for (l = 0; l < 9; ++l) {
            this.addSlot(new Slot(playerInv, l, 8 + l * 18, 142));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        // NEVER return null. Vanilla assumes ItemStack is non-null.
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;

        Slot slot = this.slots.get(index);
        if (slot == null) return ItemStack.EMPTY;

        ItemStack inSlot = slot.getStack();
        if (inSlot == null || inSlot.isEmpty()) return ItemStack.EMPTY;

        ItemStack copy = inSlot.copy();

        final int CONTAINER_SLOTS = 1; // only our ghast-tear payment slot
        final int PLAYER_START = CONTAINER_SLOTS;
        final int PLAYER_END = this.slots.size();

        if (index < CONTAINER_SLOTS) {
            // Container -> Player
            if (!this.insertItem(inSlot, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> Container (only ghast tears allowed)
            if (!inSlot.isOf(Items.GHAST_TEAR)) return ItemStack.EMPTY;
            if (!this.insertItem(inSlot, 0, CONTAINER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (inSlot.isEmpty()) slot.setStack(ItemStack.EMPTY);
        slot.markDirty();

        return copy;
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }

    public int countTears() {
        ItemStack s = inv.getStack(0);
        return s.isOf(Items.GHAST_TEAR) ? s.getCount() : 0;
    }

    public void consumeTears(int n) {
        ItemStack s = inv.getStack(0);
        if (s.isOf(Items.GHAST_TEAR)) {
            s.decrement(n);
            inv.markDirty();
        }
    }

    // -------- factory (server-side) to open with extra data ----------
    public static class Factory implements ExtendedScreenHandlerFactory {
        private final int ghostId; private final BlockPos target; private final int tearsNeeded;
        public Factory(int ghostId, BlockPos target, int tearsNeeded) {
            this.ghostId = ghostId; this.target = target; this.tearsNeeded = tearsNeeded;
        }
        @Override public Text getDisplayName() { return Text.of("Courier Payment"); }
        @Override public void writeScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player, PacketByteBuf buf) {
            buf.writeVarInt(ghostId);
            buf.writeBlockPos(target);
            buf.writeVarInt(tearsNeeded);
        }
        @Override public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
            return new CourierPayScreenHandler(syncId, inv, ghostId, target, tearsNeeded);
        }
    }
}
