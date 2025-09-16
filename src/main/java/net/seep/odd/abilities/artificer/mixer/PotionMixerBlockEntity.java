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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeManager;
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

    private final EnumMap<EssenceType, MixerTankStorage> tanks = new EnumMap<>(EssenceType.class);

    public PotionMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ArtificerMixerRegistry.POTION_MIXER_BE, pos, state);
        for (EssenceType e : EssenceType.values()) {
            // Pass a commit callback so any insert/extract will sync the client HUD/GUI
            tanks.put(e, new MixerTankStorage(CAPACITY_MB, e, this::syncTanks));
        }
    }

    /** Mark dirty and push a BE update to clients so the UI/HUD refreshes immediately. */
    private void syncTanks() {
        if (world == null) return;
        markDirty();
        if (!world.isClient) {
            try { this.sendData(); } catch (Throwable ignored) {} // Create BE helper, guarded
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getSpeed() != 0) setCachedState(getCachedState());
    }

    @Override public Text getDisplayName() { return Text.translatable("block.odd.potion_mixer"); }
    @Nullable @Override public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) { return new PotionMixerScreenHandler(syncId, inv, getPos()); }
    @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) { buf.writeBlockPos(getPos()); }

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
        // If this was a client update packet, the UI should already be notified via updateListeners/sendData
    }

    public Storage<FluidVariant> externalCombinedStorage() { return new MixerCombinedStorage(tanks); }

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

    /* ====== Brewing (recipe-driven + debug) ====== */
    private static final boolean ODD_MIXER_DEBUG = true;
    private void dbg(String msg) { if (ODD_MIXER_DEBUG) System.out.println("[Odd/Mixer@" + pos + "] " + msg); }

    public void tryBrew(Set<EssenceType> picked) {
        if (world == null || world.isClient) return;

        if (getSpeedAbs() < MIN_SPEED) { dbg("Blocked: speed " + getSpeedAbs() + " < MIN_SPEED " + MIN_SPEED); return; }
        if (picked.size() != 3) { dbg("Blocked: picked.size()=" + picked.size() + " (need 3)"); return; }

        // canonical key for the three essences (sorted by key)
        EssenceType[] arr = picked.toArray(new EssenceType[0]);
        java.util.Arrays.sort(arr, java.util.Comparator.comparing((EssenceType e) -> e.key));
        String inKey = arr[0].key + "+" + arr[1].key + "+" + arr[2].key;
        dbg("Picked inKey=" + inKey);

        // On your Yarn, listAllOfType returns List<PotionMixingRecipe>
        RecipeManager rm = world.getRecipeManager();
        java.util.List<PotionMixingRecipe> recipes = rm.listAllOfType(ArtificerMixerRegistry.POTION_MIXING_TYPE);
        dbg("Recipe count for type 'odd:potion_mixing' = " + recipes.size());

        PotionMixingRecipe match = null;
        for (PotionMixingRecipe r : recipes) {
            if (r.inKey().equals(inKey)) { match = r; break; }
        }
        if (match == null) { dbg("No recipe matched inKey=" + inKey + " — check JSON path/type/essence keys."); return; }

        long per = match.per();
        dbg("Match: id=" + match.id() + " kind=" + match.kind() + " count=" + match.count() + " per=" + per);

        for (EssenceType e : picked) {
            long have = tanks.get(e).getAmount();
            if (have < per) { dbg("Not enough fluid: " + e.key + " have=" + have + " need>=" + per); return; }
        }

        try (Transaction tx = Transaction.openOuter()) {
            for (EssenceType e : picked) {
                var t = tanks.get(e);
                t.extract(t.getResource(), per, tx);
            }
            tx.commit();
        }

        // sync UI immediately after levels change
        syncTanks();

        ItemStack out = new ItemStack(
                match.kind() == PotionMixingRecipe.Kind.THROW
                        ? ArtificerMixerRegistry.BREW_THROWABLE
                        : ArtificerMixerRegistry.BREW_DRINKABLE,
                match.count()
        );
        var tag = out.getOrCreateNbt();
        tag.putString("odd_brew_id", match.brewId());
        tag.putInt("odd_brew_color", match.color());

        var ie = new net.minecraft.entity.ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, out);
        world.spawnEntity(ie);
        dbg("Brewed item spawned.");
    }

    /* HUD helpers */
    public long getAmountDisplayMb(EssenceType t) { var tank = tanks.get(t); return tank != null ? tank.getAmount() : 0L; }
    public int getSpeedAbs() { return Math.round(Math.abs(getSpeed())); }
    public boolean isPoweredAndReady() { return getSpeedAbs() >= MIN_SPEED; }
}
