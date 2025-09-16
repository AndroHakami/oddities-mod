package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.EssenceType;

/** A single essence tank that only accepts its own essence fluid. Amounts are in mB. */
public class MixerTankStorage extends SingleVariantStorage<FluidVariant> {
    private final long capacityMb;
    private final EssenceType essence;
    private final Runnable onCommit; // <-- notify BE when transactions finish

    public MixerTankStorage(long capacityMb, EssenceType essence, Runnable onCommit) {
        this.capacityMb = capacityMb;
        this.essence = essence;
        this.onCommit = onCommit;
    }

    public EssenceType getEssence() { return essence; }

    @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
    @Override protected long getCapacity(FluidVariant variant) { return capacityMb; }

    @Override protected boolean canInsert(FluidVariant variant) {
        return !variant.isBlank() && variant.getFluid() == essence.getFluid();
    }

    @Override protected void onFinalCommit() {
        if (onCommit != null) onCommit.run();   // <-- sync BE to clients
    }

    /* ---- NBT ---- */
    public void toNbt(NbtCompound tag) {
        tag.putLong("amt", getAmount());
        tag.putString("fluid", getResource().isBlank()
                ? "" : Registries.FLUID.getId(getResource().getFluid()).toString());
        tag.putString("essence", essence.name());
    }

    public void fromNbt(NbtCompound tag) {
        long amt = tag.getLong("amt");
        String idStr = tag.getString("fluid");

        try (Transaction tx = Transaction.openOuter()) {
            if (getAmount() > 0) extract(getResource(), getAmount(), tx);
            tx.commit();
        }

        if (amt > 0 && idStr != null && !idStr.isEmpty()) {
            Identifier id = Identifier.tryParse(idStr);
            if (id != null) {
                var fluid = Registries.FLUID.get(id);
                if (fluid == essence.getFluid()) {
                    try (Transaction tx = Transaction.openOuter()) {
                        insert(FluidVariant.of(fluid), amt, tx);
                        tx.commit();
                    }
                }
            }
        }
    }
}
