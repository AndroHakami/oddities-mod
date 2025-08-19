package net.seep.odd.block.grandanvil;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class GrandAnvilScreenHandler extends ScreenHandler {
    private final Inventory anvilInv;
    private final PropertyDelegate properties;
    private final BlockPos pos;

    // ----- server ctor -----
    public GrandAnvilScreenHandler(int syncId, PlayerInventory playerInv, GrandAnvilBlockEntity be) {
        super(ModScreens.GRAND_ANVIL, syncId);
        this.anvilInv = be.inventory();
        this.properties = be.getProps();
        this.pos = be.getPos();

        // 0 = gear, 1 = material
        this.addSlot(new Slot(anvilInv, 0, 44, 20));
        this.addSlot(new Slot(anvilInv, 1, 116, 20));

        addPlayerSlots(playerInv);

        this.addProperties(this.properties);
    }

    // ----- client ctor -----
    public GrandAnvilScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        super(ModScreens.GRAND_ANVIL, syncId);
        this.anvilInv = new SimpleInventory(2);
        this.properties = new ArrayPropertyDelegate(7);
        this.pos = pos;

        this.addSlot(new Slot(anvilInv, 0, 39, 35));
        this.addSlot(new Slot(anvilInv, 1, 118, 35));

        addPlayerSlots(playerInv);

        this.addProperties(this.properties);
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

    public BlockPos getPos() { return pos; }

    // exposed props for UI
    public boolean active()   { return properties.get(GrandAnvilBlockEntity.P_ACTIVE) != 0; }
    public int duration()     { return properties.get(GrandAnvilBlockEntity.P_DURATION); }
    public int progress()     { return properties.get(GrandAnvilBlockEntity.P_PROGRESS); }
    public int successes()    { return properties.get(GrandAnvilBlockEntity.P_SUCC); }
    public int required()     { return properties.get(GrandAnvilBlockEntity.P_REQ); }
    public int difficulty()   { return properties.get(GrandAnvilBlockEntity.P_DIFF); }
    public int seed()         { return properties.get(GrandAnvilBlockEntity.P_SEED); }

    @Override
    public boolean canUse(PlayerEntity player) {
        // basic sanity: still at this anvil & close by
        if (player.getWorld().getBlockEntity(pos) instanceof GrandAnvilBlockEntity) {
            double dx = pos.getX() + 0.5 - player.getX();
            double dy = pos.getY() + 0.5 - player.getY();
            double dz = pos.getZ() + 0.5 - player.getZ();
            return dx*dx + dy*dy + dz*dz <= 64.0;
        }
        return false;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        // basic shift-click behavior
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack src = slot.getStack();
            newStack = src.copy();

            int invStart = 2;           // after 2 custom slots
            int invEnd = invStart + 27; // main inv
            int hotStart = invEnd;
            int hotEnd = hotStart + 9;

            if (index < invStart) {
                // move from custom slots -> player inv
                if (!this.insertItem(src, invStart, hotEnd, true)) return ItemStack.EMPTY;
            } else {
                // from player inv -> try gear(0) then material(1)
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
