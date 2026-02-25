package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.artificer.EssenceType;

public class MixerTankStorage extends SingleVariantStorage<FluidVariant> {
    private final long capacity; // Fabric Transfer units
    private final EssenceType essence;
    private final Runnable onCommit;

    public MixerTankStorage(long capacity, EssenceType essence, Runnable onCommit) {
        this.capacity = capacity;
        this.essence = essence;
        this.onCommit = onCommit;
    }

    public EssenceType getEssence() { return essence; }

    @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
    @Override protected long getCapacity(FluidVariant variant) { return capacity; }

    @Override
    protected boolean canInsert(FluidVariant variant) {
        if (variant == null || variant.isBlank()) return false;
        return fluidMatches(variant.getFluid(), essence.getFluid());
    }

    @Override
    protected void onFinalCommit() {
        if (onCommit != null) onCommit.run();
    }

    /** Still/flowing tolerant comparison (Create/Fabric sometimes uses either). */
    public static boolean fluidMatches(Fluid a, Fluid b) {
        if (a == b) return true;

        // If either side is flowable, compare their still/flowing pairs.
        if (a instanceof FlowableFluid fa) {
            if (fa.getStill() == b || fa.getFlowing() == b) return true;
        }
        if (b instanceof FlowableFluid fb) {
            if (fb.getStill() == a || fb.getFlowing() == a) return true;
        }
        return false;
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
                if (fluidMatches(fluid, essence.getFluid())) {
                    try (Transaction tx = Transaction.openOuter()) {
                        insert(FluidVariant.of(fluid), amt, tx);
                        tx.commit();
                    }
                }
            }
        }
    }
}
