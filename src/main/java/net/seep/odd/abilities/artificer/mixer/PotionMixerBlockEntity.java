package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Set;

/** Create-powered mixer BE with per-essence tanks and Fabric Transfer exposure. */
public class PotionMixerBlockEntity extends KineticBlockEntity implements ExtendedScreenHandlerFactory {
    public static final int MIN_SPEED = 8;
    private static final long CAPACITY_MB = 1000;

    private final EnumMap<EssenceType, MixerTankStorage> tanks = new EnumMap<>(EssenceType.class);

    public PotionMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ArtificerMixerRegistry.POTION_MIXER_BE, pos, state);
        for (EssenceType t : EssenceType.values()) {
            tanks.put(t, new MixerTankStorage(CAPACITY_MB));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getSpeed() != 0) setCachedState(getCachedState()); // keep BE live for Create
    }

    /* ====== UI ====== */
    @Override public Text getDisplayName() { return Text.translatable("block.odd.potion_mixer"); }

    @Nullable @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new PotionMixerScreenHandler(syncId, playerInventory, getPos());
    }

    @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) { buf.writeBlockPos(getPos()); }

    /* ====== Save/Load ====== */
    @Override
    protected void write(NbtCompound nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        for (var e : tanks.entrySet()) {
            NbtCompound t = new NbtCompound();
            e.getValue().toNbt(t);
            nbt.put("tank_" + e.getKey().name(), t);
        }
    }

    @Override
    protected void read(NbtCompound nbt, boolean clientPacket) {
        super.read(nbt, clientPacket);
        for (EssenceType t : EssenceType.values()) {
            if (nbt.contains("tank_" + t.name())) {
                tanks.get(t).fromNbt(nbt.getCompound("tank_" + t.name()));
            }
        }
    }

    /* ====== External IO (for pipes) ====== */
    public Storage<FluidVariant> externalCombinedStorage() { return new MixerCombinedStorage(tanks); }

    /* ====== Crafting ======
       Note: your current PotionMixingRecipe.java in the repo is "machine-only" and does not
       expose a helper like essenceAsStack(..). To keep this file compiling and functional,
       we do a simple brew output here (you can wire real recipe selection later). */
    public void tryBrew(Set<EssenceType> picked) {
        if (world == null || world.isClient) return;
        if (getSpeed() < MIN_SPEED) return;
        if (picked.size() != 3) return;

        long per = 250; // 250 mB from each essence
        for (EssenceType t : picked) {
            if (tanks.get(t).getAmount() < per) return;
        }

        ItemStack out = new ItemStack(ArtificerMixerRegistry.BREW_DRINKABLE, 1);
        // Optional: tag for future effect/color selection
        // out.getOrCreateNbt().putString("odd_brew_key", canonicalKey(picked));

        try (Transaction tx = Transaction.openOuter()) {
            for (EssenceType t : picked) {
                var tank = tanks.get(t);
                tank.extract(tank.getResource(), per, tx);
            }
            tx.commit();
        }

        var ie = new net.minecraft.entity.ItemEntity(
                world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, out);
        world.spawnEntity(ie);
    }

    /* ====== Fabric Storage bridge (combined view over all tanks) ====== */
    public record MixerCombinedStorage(EnumMap<EssenceType, MixerTankStorage> map) implements Storage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            long inserted = 0;
            for (MixerTankStorage s : map.values()) {
                if (inserted >= maxAmount) break;
                inserted += s.insert(resource, maxAmount - inserted, transaction);
            }
            return inserted;
        }
        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            long extracted = 0;
            for (MixerTankStorage s : map.values()) {
                if (extracted >= maxAmount) break;
                extracted += s.extract(resource, maxAmount - extracted, transaction);
            }
            return extracted;
        }
        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            java.util.ArrayList<StorageView<FluidVariant>> list = new java.util.ArrayList<>();
            for (MixerTankStorage s : map.values())
                for (StorageView<FluidVariant> v : s) list.add(v);
            return list.iterator();
        }
    }

    /* ====== HUD helpers used by PotionMixerHud ====== */
    public long getAmountDisplayMb(EssenceType t) { var tank = this.tanks.get(t); return tank != null ? tank.getAmount() : 0L; }
    public int getSpeedAbs() { return Math.round(Math.abs(getSpeed())); }
    public boolean isPoweredAndReady() { return getSpeedAbs() >= MIN_SPEED; }
}
