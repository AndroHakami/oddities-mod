package net.seep.odd.abilities.artificer;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class EssenceStorage {
    private EssenceStorage() {}

    // Root compound for non-amount data (capacity, animation state)
    public static final String NBT_ROOT = "odd_artificer";
    public static final String NBT_CAP  = "cap";

    // Animation state (read by GeckoLib controller)
    public static final String NBT_ANIM_NAME = "vacuum_anim";      // e.g. "select_gaia"
    public static final String NBT_ANIM_SEQ  = "vacuum_anim_seq";  // increments to signal “new” anim

    // ---- helpers ----
    private static String amtKey(EssenceType t) { return "odd_ess_" + t.key; }

    private static NbtCompound root(ItemStack stack) {
        NbtCompound n = stack.getOrCreateNbt();
        if (!n.contains(NBT_ROOT)) n.put(NBT_ROOT, new NbtCompound());
        // one-time migration from old nested format if it ever existed
        migrateNestedToTopLevel(n);
        return n.getCompound(NBT_ROOT);
    }

    /** If an old nested format existed (odd_vacuum.amts.{key}:int), lift it to top-level odd_ess_<key>. */
    private static void migrateNestedToTopLevel(NbtCompound nbt) {
        if (!nbt.contains("odd_vacuum")) return;
        NbtCompound vac = nbt.getCompound("odd_vacuum");
        if (!vac.contains("amts")) return;
        NbtCompound amts = vac.getCompound("amts");
        for (EssenceType t : EssenceType.values()) {
            String k = t.key;
            if (amts.contains(k)) {
                int v = amts.getInt(k);
                if (v > 0) nbt.putInt(amtKey(t), Math.max(nbt.getInt(amtKey(t)), v));
            }
        }
        // do not remove legacy; harmless to leave
    }

    // ---- capacity ----
    public static int getCapacity(ItemStack stack) {
        NbtCompound r = root(stack);
        if (!r.contains(NBT_CAP)) r.putInt(NBT_CAP, 1000); // your previous default
        return r.getInt(NBT_CAP);
    }
    public static void setCapacity(ItemStack stack, int cap) {
        root(stack).putInt(NBT_CAP, Math.max(0, cap));
    }

    // ---- amounts (TOP-LEVEL ints: odd_ess_<key>) ----
    public static int get(ItemStack stack, EssenceType t) {
        if (stack == null || stack.isEmpty()) return 0;
        NbtCompound n = stack.getNbt();
        if (n == null) return 0;
        return n.getInt(amtKey(t));
    }

    public static int set(ItemStack stack, EssenceType t, int v) {
        NbtCompound n = stack.getOrCreateNbt();
        n.putInt(amtKey(t), Math.max(0, v));
        return v;
    }

    public static int add(ItemStack stack, EssenceType t, int add) {
        if (add <= 0) return 0;
        int capLeft = Math.max(0, getCapacity(stack) - total(stack));
        int put = Math.min(capLeft, add);
        if (put <= 0) return 0;
        set(stack, t, get(stack, t) + put);
        return put;
    }

    public static int extract(ItemStack stack, EssenceType t, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return 0;
        NbtCompound n = stack.getOrCreateNbt();
        String k = amtKey(t);
        int cur = n.getInt(k);
        int take = Math.min(cur, amount);
        if (take > 0) n.putInt(k, cur - take);
        return take;
    }

    public static int total(ItemStack stack) {
        int sum = 0;
        for (EssenceType e : EssenceType.values()) sum += get(stack, e);
        return sum;
    }

    /** Write the anim name and bump a sequence so the client can one-shot it. */
    public static void setSelectedAnim(ItemStack stack, EssenceType t) {
        NbtCompound r = root(stack);
        r.putString(NBT_ANIM_NAME, "select_" + t.key);      // e.g. "select_gaia"
        r.putInt(NBT_ANIM_SEQ, r.getInt(NBT_ANIM_SEQ) + 1); // bump seq
    }
}
