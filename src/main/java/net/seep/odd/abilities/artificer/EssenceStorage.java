package net.seep.odd.abilities.artificer;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class EssenceStorage {
    private EssenceStorage() {}

    public static final String NBT_ROOT = "odd_artificer";
    public static final String NBT_CAP  = "cap";

    // Animation state (read by GeckoLib controller)
    public static final String NBT_ANIM_NAME = "vacuum_anim";      // e.g., "select_gaia"
    public static final String NBT_ANIM_SEQ  = "vacuum_anim_seq";  // increments to signal “new” anim

    public static int getCapacity(ItemStack stack) {
        NbtCompound r = root(stack);
        if (!r.contains(NBT_CAP)) r.putInt(NBT_CAP, 1000);
        return r.getInt(NBT_CAP);
    }

    public static int get(ItemStack stack, EssenceType t) { return root(stack).getInt(t.key); }
    public static int set(ItemStack stack, EssenceType t, int v) { root(stack).putInt(t.key, Math.max(0, v)); return v; }

    public static int add(ItemStack stack, EssenceType t, int add) {
        int capLeft = Math.max(0, getCapacity(stack) - total(stack));
        int put = Math.min(capLeft, Math.max(0, add));
        if (put <= 0) return 0;
        set(stack, t, get(stack, t) + put);
        return put;
    }

    public static int total(ItemStack stack) {
        int s = 0; for (var e : EssenceType.values()) s += get(stack, e); return s;
    }

    /** Write the anim name and bump a sequence so the client can one-shot it. */
    public static void setSelectedAnim(ItemStack stack, EssenceType t) {
        NbtCompound r = root(stack);
        r.putString(NBT_ANIM_NAME, "select_" + t.key);          // e.g. "select_gaia"
        r.putInt(NBT_ANIM_SEQ, r.getInt(NBT_ANIM_SEQ) + 1);     // bump seq
    }

    private static NbtCompound root(ItemStack stack) {
        NbtCompound n = stack.getOrCreateNbt();
        if (!n.contains(NBT_ROOT)) n.put(NBT_ROOT, new NbtCompound());
        return n.getCompound(NBT_ROOT);
    }
}
