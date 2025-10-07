package net.seep.odd.abilities.ghostlings.screen.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

public class GhostCargoScreenHandler extends ScreenHandler {
    public static ScreenHandlerType<GhostCargoScreenHandler> TYPE;

    private final Inventory cargo;

    // server ctor
    public GhostCargoScreenHandler(int syncId, PlayerInventory playerInv, Inventory cargo) {
        super(TYPE, syncId);
        this.cargo = cargo;
        if (cargo instanceof SimpleInventory si) si.onOpen(playerInv.player);

        // 9x9 cargo grid (81)
        int startX = 8, startY = 18, slot = 0;
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++, slot++) {
                this.addSlot(new Slot(cargo, slot, startX + col * 18, startY + row * 18));
            }
        }

        // Player inventory (9x3)
        int invY = startY + 9 * 18 + 12;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        // Hotbar
        int hotY = invY + 58;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, hotY));
        }
    }

    // client ctor (for ScreenRegistry#registerSimple if you want it)
    public GhostCargoScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, new SimpleInventory(81));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    // shift-click move
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stack = slot.getStack();
            newStack = stack.copy();

            int cargoEnd = 81;
            if (index < cargoEnd) {
                // from cargo -> player
                if (!this.insertItem(stack, cargoEnd, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                // from player -> cargo
                if (!this.insertItem(stack, 0, cargoEnd, false)) return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}
