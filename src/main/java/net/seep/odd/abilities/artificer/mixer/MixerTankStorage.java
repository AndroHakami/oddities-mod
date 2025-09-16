package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Single tank for one essence, measured in mB. */
public class MixerTankStorage extends SingleVariantStorage<FluidVariant> {
    private final long capacityMb;

    public MixerTankStorage(long capacityMb) { this.capacityMb = capacityMb; }

    @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
    @Override protected long getCapacity(FluidVariant variant) { return capacityMb; }

    /* ---- NBT (amount + fluid id) ---- */

    public void toNbt(NbtCompound tag) {
        tag.putLong("amt", getAmount());
        tag.putString("fluid", getResource().isBlank()
                ? "" : Registries.FLUID.getId(getResource().getFluid()).toString());
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
                try (Transaction tx = Transaction.openOuter()) {
                    insert(FluidVariant.of(fluid), amt, tx);
                    tx.commit();
                }
            }
        }
    }
}
