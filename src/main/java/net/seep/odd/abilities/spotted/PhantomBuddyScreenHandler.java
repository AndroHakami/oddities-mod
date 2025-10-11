package net.seep.odd.abilities.spotted;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.seep.odd.entity.spotted.PhantomBuddyEntity;

public class PhantomBuddyScreenHandler extends ScreenHandler {
    public static final int ROWS = 4, COLS = 4, SIZE = ROWS * COLS;

    private final Inventory buddyInv;

    // client ctor
    public PhantomBuddyScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, new SimpleInventory(SIZE));
    }

    // server ctor
    public PhantomBuddyScreenHandler(int syncId, PlayerInventory playerInv, PhantomBuddyEntity buddy) {
        this(syncId, playerInv, buddy.getStorage());
    }

    private PhantomBuddyScreenHandler(int syncId, PlayerInventory playerInv, Inventory buddyInv) {
        super(SpottedScreens.PHANTOM_BUDDY, syncId);
        checkSize(buddyInv, SIZE);
        this.buddyInv = buddyInv;
        buddyInv.onOpen(playerInv.player);

        int startX = (176 - (COLS * 18)) / 2;
        int startY = 18;

        // 4x4 buddy grid
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                addSlot(new Slot(buddyInv, r * COLS + c, startX + c * 18, startY + r * 18));
            }
        }

        // player inv (3 rows)
        int invY = startY + ROWS * 18 + 24;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, invY + r * 18));
            }
        }
        // hotbar
        int hotbarY = invY + 58;
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c, 8 + c * 18, hotbarY));
    }

    @Override public boolean canUse(PlayerEntity player) { return buddyInv.canPlayerUse(player); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stack = slot.getStack();
            ret = stack.copy();

            int buddyEnd = SIZE - 1;
            int playerStart = SIZE;
            int playerEnd = this.slots.size() - 1;

            if (index <= buddyEnd) {
                if (!this.insertItem(stack, playerStart, playerEnd + 1, true)) return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(stack, 0, SIZE, false)) return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return ret;
    }

    // 1.20.1: override onClosed, not close
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.buddyInv.onClose(player);
    }
}
