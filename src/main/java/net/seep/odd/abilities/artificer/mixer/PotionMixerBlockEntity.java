package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.fluid.ArtificerFluids;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;

import java.util.Arrays;

public class PotionMixerBlockEntity extends KineticBlockEntity {

    // 1000 mB capacity per essence
    private static final long CAP_MB = 1000;
    private static final long CAP    = (FluidConstants.BUCKET / 1000) * CAP_MB;


    public static final float MIN_SPEED = 16f; // require this or more absolute speed to run
    private static final int   WORK_T    = 40;  // craft cadence (2s)

    // 6 tanks ordered by EssenceType.ordinal()
    private final SingleVariantStorage<FluidVariant>[] tanks = new SingleVariantStorage[EssenceType.values().length];

    // Combined view for all tanks (pipes can insert matching fluids)
    private final Storage<FluidVariant> external;

    // simple output inventory (one slot)
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);

    private int ticker = 0;

    @SuppressWarnings("unchecked")
    public PotionMixerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        for (EssenceType t : EssenceType.values()) {
            final int idx = t.ordinal();
            tanks[idx] = new SingleVariantStorage<>() {
                @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
                @Override protected long getCapacity(FluidVariant variant) { return CAP; }

                @Override
                protected boolean canInsert(FluidVariant variant) {
                    var f = variant.getFluid();
                    return f == ArtificerFluids.still(t) || f == ArtificerFluids.FLOWING[t.ordinal()];
                }

                @Override
                protected boolean canExtract(FluidVariant variant) { return false; }

                // 🔔 whenever a pipe transaction commits, sync to client so HUD is accurate
                @Override
                protected void onFinalCommit() {
                    PotionMixerBlockEntity.this.markDirty();
                    PotionMixerBlockEntity.this.sendData(); // Create’s SmartBE sync
                }
            };
        }
        external = new CombinedStorage<>(Arrays.asList(tanks));
    }
    // ✅ add this overload so Fabric's factory can call it
    public PotionMixerBlockEntity(BlockPos pos, BlockState state) {
        this(ArtificerMixerRegistry.POTION_MIXER_BE, pos, state);
    }

    public Storage<FluidVariant> externalCombinedStorage() { return external; }

    @Override
    public void tick() {
        super.tick();
        if (world == null || world.isClient) return;

        if (Math.abs(getSpeed()) < MIN_SPEED) return;

        ticker++;
        if (ticker % WORK_T != 0) return;

        if (!(world instanceof ServerWorld sw)) return;

        RecipeManager rm = sw.getServer().getRecipeManager();
        var recipes = rm.listAllOfType(ArtificerMixerRegistry.POTION_MIXING_TYPE);
        if (recipes.isEmpty()) return;

        for (PotionMixingRecipe r : recipes) {
            if (canCraft(r) && doCraft(r)) {
                break;
            }
        }
    }

    private boolean canCraft(PotionMixingRecipe r) {
        ItemStack out = makeOutputStack(r);
        ItemStack slot = items.get(0);
        if (!slot.isEmpty() && (!ItemStack.canCombine(slot, out) || slot.getCount() >= slot.getMaxCount()))
            return false;

        long need = r.perEssenceMb();
        for (EssenceType t : r.essences()) {
            if (amountOf(t) < toFabric(need)) return false;
        }
        return true;
    }
    public long getAmountDisplayMb(EssenceType t) {
        long a = amountOf(t); // Fabric units
        return a / (FluidConstants.BUCKET / 1000);
    }

    private boolean doCraft(PotionMixingRecipe r) {
        try (Transaction tx = Transaction.openOuter()) {
            long need = toFabric(r.perEssenceMb());
            for (EssenceType t : r.essences()) {
                var tank = tanks[t.ordinal()];
                long extracted = tank.extract(FluidVariant.of(ArtificerFluids.still(t)), need, tx);
                if (extracted < need) { tx.abort(); return false; }
            }
            tx.commit();
        }

        ItemStack out = makeOutputStack(r);
        ItemStack slot = items.get(0);
        if (slot.isEmpty()) items.set(0, out);
        else slot.increment(out.getCount());

        // 🔊 mixing sound
        if (world != null) {
            world.playSound(null, pos,
                    SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.9f, 1.2f);
            // (Optional extra sparkle)
            // world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.5f, 1.6f);
        }

        markDirty();
        sendData();
        return true;
    }

    private static long toFabric(long milliBuckets) {
        return (FluidConstants.BUCKET / 1000) * milliBuckets;
    }

    private long amountOf(EssenceType t) {
        return tanks[t.ordinal()].getAmount();
    }

    private ItemStack makeOutputStack(PotionMixingRecipe r) {
        ItemStack st = new ItemStack(
                r.outputKind() == PotionMixingRecipe.Kind.THROWABLE
                        ? ArtificerMixerRegistry.BREW_THROWABLE
                        : ArtificerMixerRegistry.BREW_DRINKABLE,
                r.count()
        );
        var n = st.getOrCreateNbt();
        n.putString("odd_brew_id", r.brewId());
        n.putInt("odd_brew_color", r.color());
        return st;
    }

    /* ---- save / load ---- */
    @Override
    protected void read(NbtCompound nbt, boolean clientPacket) {
        super.read(nbt, clientPacket);

        // clear tanks then load via transactions (avoid touching SingleVariantStorage's protected fields)
        for (var t : tanks) {
            long amt = t.getAmount();
            if (amt > 0) {
                try (Transaction tx = Transaction.openOuter()) {
                    t.extract(t.getResource(), amt, tx);
                    tx.commit();
                }
            }
        }

        if (nbt.contains("fluids", 10)) {
            NbtCompound fluids = nbt.getCompound("fluids");
            for (EssenceType t : EssenceType.values()) {
                String key = "f_" + t.key;
                if (fluids.contains(key, 10)) {
                    NbtCompound f = fluids.getCompound(key);
                    var fluidId = new Identifier(f.getString("id"));
                    var fluid   = Registries.FLUID.get(fluidId);
                    var variant = FluidVariant.of(fluid);
                    long amt    = f.getLong("amt");
                    if (amt > 0 && !variant.isBlank()) {
                        try (Transaction tx = Transaction.openOuter()) {
                            tanks[t.ordinal()].insert(variant, amt, tx);
                            tx.commit();
                        }
                    }
                }
            }
        }

        if (nbt.contains("items", 10)) {
            Inventories.readNbt(nbt.getCompound("items"), items);
        }
    }

    @Override
    protected void write(NbtCompound nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);

        NbtCompound fluids = new NbtCompound();
        for (EssenceType t : EssenceType.values()) {
            var s = tanks[t.ordinal()];
            if (!s.isResourceBlank() && s.getAmount() > 0) {
                NbtCompound f = new NbtCompound();
                f.putString("id", Registries.FLUID.getId(s.getResource().getFluid()).toString());
                f.putLong("amt", s.getAmount());
                fluids.put("f_" + t.key, f);
            }
        }
        nbt.put("fluids", fluids);

        NbtCompound it = new NbtCompound();
        Inventories.writeNbt(it, items);
        nbt.put("items", it);
    }
}
