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

public class PotionMixerBlockEntity extends KineticBlockEntity implements ExtendedScreenHandlerFactory {
    public static final int MIN_SPEED = 8;
    private static final long CAPACITY_MB = 1000;

    /** Each essence has its own tank, and each tank only accepts its essence fluid. */
    private final EnumMap<EssenceType, MixerTankStorage> tanks = new EnumMap<>(EssenceType.class);

    public PotionMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ArtificerMixerRegistry.POTION_MIXER_BE, pos, state);
        for (EssenceType e : EssenceType.values()) {
            tanks.put(e, new MixerTankStorage(CAPACITY_MB, e));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getSpeed() != 0) setCachedState(getCachedState());
    }

    /* ====== UI ====== */
    @Override public Text getDisplayName() { return Text.translatable("block.odd.potion_mixer"); }
    @Nullable @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new PotionMixerScreenHandler(syncId, inv, getPos());
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
        for (EssenceType e : EssenceType.values()) {
            if (nbt.contains("tank_" + e.name())) {
                tanks.get(e).fromNbt(nbt.getCompound("tank_" + e.name()));
            }
        }
    }

    /* ====== Fabric Transfer exposure (Create pipes use this) ====== */
    public Storage<FluidVariant> externalCombinedStorage() {
        return new MixerCombinedStorage(tanks);
    }

    /** Combined view: routes insert/extract to the correct essence tank based on the fluid. */
    public record MixerCombinedStorage(EnumMap<EssenceType, MixerTankStorage> map) implements Storage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext tx) {
            EssenceType e = EssenceType.fromFluid(resource.getFluid());
            if (e == null) return 0;
            MixerTankStorage tank = map.get(e);
            return tank == null ? 0 : tank.insert(resource, maxAmount, tx);
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext tx) {
            EssenceType e = EssenceType.fromFluid(resource.getFluid());
            if (e == null) return 0;
            MixerTankStorage tank = map.get(e);
            return tank == null ? 0 : tank.extract(resource, maxAmount, tx);
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            java.util.ArrayList<StorageView<FluidVariant>> list = new java.util.ArrayList<>();
            for (MixerTankStorage s : map.values())
                for (StorageView<FluidVariant> v : s) list.add(v);
            return list.iterator();
        }
    }

    /* ====== Brewing ====== */
    public void tryBrew(Set<EssenceType> picked) {
        if (world == null || world.isClient) return;
        if (getSpeedAbs() < MIN_SPEED) return;
        if (picked.size() != 3) return;

        final long per = 1000;
        for (EssenceType e : picked) {
            if (tanks.get(e).getAmount() < per) return; // not enough of that essence
        }

        // Consume and spawn result
        try (Transaction tx = Transaction.openOuter()) {
            for (EssenceType e : picked) {
                var t = tanks.get(e);
                t.extract(t.getResource(), per, tx);
            }
            tx.commit();
        }
        ItemStack out = new ItemStack(ArtificerMixerRegistry.BREW_DRINKABLE, 1);
        var ie = new net.minecraft.entity.ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, out);
        world.spawnEntity(ie);
    }

    /* ====== HUD helpers ====== */
    public long getAmountDisplayMb(EssenceType t) { var tank = tanks.get(t); return tank != null ? tank.getAmount() : 0L; }
    public int getSpeedAbs() { return Math.round(Math.abs(getSpeed())); }
    public boolean isPoweredAndReady() { return getSpeedAbs() >= MIN_SPEED; }
}
