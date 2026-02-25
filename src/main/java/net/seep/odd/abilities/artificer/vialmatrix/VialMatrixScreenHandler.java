// FILE: src/main/java/net/seep/odd/abilities/artificer/vialmatrix/VialMatrixScreenHandler.java
package net.seep.odd.abilities.artificer.vialmatrix;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

public final class VialMatrixScreenHandler extends ScreenHandler {

    private final VialMatrixInventory matrix;

    public VialMatrixScreenHandler(int syncId, PlayerInventory playerInv, VialMatrixInventory matrix) {
        super(ScreenHandlerType.GENERIC_3X3, syncId);
        this.matrix = matrix;

        // 3x3 (dispenser layout)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = col + row * 3;
                this.addSlot(new FilteredSlot(matrix, idx, 62 + col * 18, 17 + row * 18));
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return matrix.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack out = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        out = stack.copy();

        // matrix slots are 0..8
        if (index < 9) {
            if (!this.insertItem(stack, 9, 45, true)) return ItemStack.EMPTY;
        } else {
            if (!VialMatrixInventory.isAllowed(stack)) return ItemStack.EMPTY;
            if (!this.insertItem(stack, 0, 9, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();

        return out;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        matrix.markDirty();
    }

    private static final class FilteredSlot extends Slot {
        FilteredSlot(VialMatrixInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return VialMatrixInventory.isAllowed(stack);
        }

        // ✅ allow stacking where the item supports it (brews), while potions/buckets stay 1 naturally
        @Override
        public int getMaxItemCount() {
            return 64;
        }
    }
}