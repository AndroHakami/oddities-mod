// src/main/java/net/seep/odd/block/combiner/CombinerScreenHandler.java
package net.seep.odd.block.combiner;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.block.grandanvil.ModScreens;

public class CombinerScreenHandler extends ScreenHandler {
    private final Inventory inv;
    private final PropertyDelegate props;
    private final BlockPos pos;

    // server-only
    private final CombinerBlockEntity be;

    /* =========================
       SERVER CTOR
       ========================= */
    public CombinerScreenHandler(int syncId, PlayerInventory playerInv, CombinerBlockEntity be) {
        super(ModScreens.COMBINER, syncId);
        this.inv = be.inventory();
        this.props = be.getProps();
        this.pos = be.getPos();
        this.be = be;

        // mark menu open for animations
        be.onMenuOpened(playerInv.player);

        // ✅ Match OLD Grand Anvil slot positions
        // 0 = gear, 1 = trim template
        this.addSlot(new Slot(inv, 0, 39, 35));
        this.addSlot(new Slot(inv, 1, 118, 35));

        addPlayerSlots(playerInv);
        this.addProperties(this.props);
    }

    /* =========================
       CLIENT CTOR
       ========================= */
    public CombinerScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        super(ModScreens.COMBINER, syncId);
        this.inv = new SimpleInventory(2);
        this.props = new ArrayPropertyDelegate(7);
        this.pos = pos;
        this.be = null;

        // ✅ Match OLD Grand Anvil slot positions
        this.addSlot(new Slot(inv, 0, 39, 35));
        this.addSlot(new Slot(inv, 1, 118, 35));

        addPlayerSlots(playerInv);
        this.addProperties(this.props);
    }

    private void addPlayerSlots(PlayerInventory inv) {
        // player inv
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        // hotbar
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 142));
        }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (be != null) be.onMenuClosed(player);
    }

    public BlockPos getPos() { return pos; }

    // exposed props for UI
    public boolean active()   { return props.get(CombinerBlockEntity.P_ACTIVE) != 0; }
    public int duration()     { return props.get(CombinerBlockEntity.P_DURATION); }
    public int progress()     { return props.get(CombinerBlockEntity.P_PROGRESS); }
    public int successes()    { return props.get(CombinerBlockEntity.P_SUCC); }
    public int required()     { return props.get(CombinerBlockEntity.P_REQ); }
    public int difficulty()   { return props.get(CombinerBlockEntity.P_DIFF); }
    public int seed()         { return props.get(CombinerBlockEntity.P_SEED); }

    @Override
    public boolean canUse(PlayerEntity player) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - player.getY();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return dx*dx + dy*dy + dz*dz <= 64.0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack src = slot.getStack();
            newStack = src.copy();

            int customSlots = 2;
            int invStart = customSlots;
            int invEnd   = invStart + 27;
            int hotStart = invEnd;
            int hotEnd   = hotStart + 9;

            if (index < customSlots) {
                if (!this.insertItem(src, invStart, hotEnd, true)) return ItemStack.EMPTY;
            } else {
                if (!this.insertItem(src, 0, 1, false) && !this.insertItem(src, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (src.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();

            if (src.getCount() == newStack.getCount()) return ItemStack.EMPTY;
            slot.onTakeItem(player, src);
        }
        return newStack;
    }
}