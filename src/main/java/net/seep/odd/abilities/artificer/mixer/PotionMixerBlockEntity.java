package net.seep.odd.abilities.artificer.mixer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.sound.ModSounds;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Single-block Create-powered mixer.
 * - One tank per EssenceType (1000 mB cap each).
 * - Crafting consumes 3 distinct essences (orderless).
 * - Opens a simple ExtendedScreenHandler.
 */
public class PotionMixerBlockEntity extends KineticBlockEntity implements ExtendedScreenHandlerFactory {
    public static final int MIN_SPEED = 8;                // required Create rpm/tip speed threshold (tweak)
    private static final long CAPACITY_MB = 1000;         // per-essence capacity
    private static final long COST_PER_ESSENCE = 100;     // cost per essence when brewing (tweak)

    /** One tank per essence; only accepts that essence’s fluid. */
    private final EnumMap<EssenceType, SingleVariantStorage<FluidVariant>> tanks =
            new EnumMap<>(EssenceType.class);

    /** Optional output buffer if your recipe yields an ItemStack. */
    private final SimpleInventory output = new SimpleInventory(1);

    public PotionMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.POTION_MIXER_BE, pos, state); // <-- change name if your BE type differs
        for (EssenceType t : EssenceType.values()) {
            tanks.put(t, new SingleVariantStorage<>() {
                @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
                @Override protected long getCapacity(FluidVariant variant) { return CAPACITY_MB; }
                @Override protected boolean canInsert(FluidVariant variant) {
                    return EssenceType.fromFluid(variant.getFluid()) == t;
                }
                @Override protected void onFinalCommit() { markDirty(); }
            });
        }
    }

    /* -------------------- Fabric Transfer exposure -------------------- */
    /** Register with: FluidStorage.SIDED.registerForBlockEntity((be,dir)->be.getFluidStorage(), ModBlockEntities.POTION_MIXER); */
    public Storage<FluidVariant> getFluidStorage() {
        List<Storage<FluidVariant>> list = new ArrayList<>(tanks.values());
        return new CombinedStorage<>(list);
    }

    /* -------------------- HUD helpers -------------------- */
    public long getAmountDisplayMb(EssenceType t) {
        var s = tanks.get(t);
        return s == null ? 0 : s.getAmount();
    }
    public float getSpeedAbs() { return Math.abs(getSpeed()); }
    public boolean isPoweredAndReady() { return Math.abs(getSpeed()) >= MIN_SPEED; }

    /* -------------------- Craft: 3 distinct essences (orderless) -------------------- */
    public boolean craft(Set<EssenceType> chosen) {
        if (world == null || world.isClient) return false;
        if (!isPoweredAndReady()) return false;
        if (chosen == null || chosen.size() != 3) return false;

        // Ensure each selected tank has enough
        for (EssenceType t : chosen) {
            var s = tanks.get(t);
            if (s == null || s.getAmount() < COST_PER_ESSENCE) return false;
        }

        // Look up a matching recipe from your recipe manager (order-agnostic).
        PotionMixingRecipe recipe = findRecipe(chosen);
        if (recipe == null) return false;

        // Consume fluids atomically
        try (Transaction tx = Transaction.openOuter()) {
            for (EssenceType t : chosen) {
                tanks.get(t).extract(FluidVariant.of(t.getFluid()), COST_PER_ESSENCE, tx);
            }
            tx.commit();
        }

        // Produce result (item example). If your result is a fluid, adapt here.
        ItemStack result = recipe.assembleItem();
        if (!result.isEmpty()) {
            if (output.getStack(0).isEmpty()) output.setStack(0, result.copy());
            else ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, result);
        }

        world.playSound(null, pos, ModSounds.MIXER_BLEND_SUCCESS, SoundCategory.BLOCKS, 1f, 1f);
        markDirty();
        return true;
    }

    /** Find a recipe whose essence set equals the chosen set (orderless, no repeats). */
    private @Nullable PotionMixingRecipe findRecipe(Set<EssenceType> chosen) {
        if (world == null) return null;
        // Pull every recipe of our type from the RecipeManager and match by set.
        var all = world.getRecipeManager().listAllOfType(PotionMixingRecipe.Type.INSTANCE);
        for (PotionMixingRecipe r : all) {
            // Expect your recipe to expose an orderless set of required essences:
            Set<EssenceType> req = r.requiredEssences(); // adjust if your accessor is named differently
            if (req != null && req.size() == 3 && req.equals(chosen)) return r;
        }
        return null;
    }

    /* -------------------- Screen opening -------------------- */
    @Override public Text getDisplayName() { return Text.translatable("block.odd.potion_mixer"); }
    @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) { buf.writeBlockPos(pos); }
    @Override public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new PotionMixerScreenHandler(syncId, inv, pos);
    }

    public Inventory getOutput() { return output; }

    /* -------------------- NBT save/load for tanks -------------------- */
    @Override
    protected void write(NbtCompound nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        int i = 0;
        for (EssenceType t : EssenceType.values()) {
            var s = tanks.get(t);
            NbtCompound tank = new NbtCompound();
            if (s.getAmount() > 0) {
                tank.putString("fluid", Registries.FLUID.getId(s.getResource().getFluid()).toString());
                tank.putLong("amt", s.getAmount());
            } else {
                tank.putString("fluid", "");
                tank.putLong("amt", 0);
            }
            nbt.put("tank_" + (i++), tank);
        }
    }

    @Override
    protected void read(NbtCompound nbt, boolean clientPacket) {
        super.read(nbt, clientPacket);
        int i = 0;
        for (EssenceType t : EssenceType.values()) {
            String key = "tank_" + (i++);
            if (!nbt.contains(key)) continue;
            NbtCompound tag = nbt.getCompound(key);
            String fStr = tag.getString("fluid");
            long amt = tag.getLong("amt");

            // Clear then reinsert so we don't poke protected fields.
            var storage = tanks.get(t);
            if (storage == null) continue;

            try (Transaction tx = Transaction.openOuter()) {
                if (storage.getAmount() > 0) {
                    storage.extract(storage.getResource(), storage.getAmount(), tx);
                }
                if (!fStr.isEmpty() && amt > 0) {
                    Identifier id = Identifier.tryParse(fStr);
                    if (id != null) {
                        var fluid = Registries.FLUID.get(id);
                        storage.insert(FluidVariant.of(fluid), amt, tx);
                    }
                }
                tx.commit();
            }
        }
    }
}
