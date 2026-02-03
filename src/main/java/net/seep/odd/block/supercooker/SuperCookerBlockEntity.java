package net.seep.odd.block.supercooker;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.chef.Chef;
import net.seep.odd.abilities.chef.net.ChefNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.supercooker.recipe.SuperCookerRecipe;
import net.seep.odd.items.ModItems;
import net.seep.odd.recipe.ModRecipes;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;

public class SuperCookerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final DefaultedList<ItemStack> fuelInv   = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private final DefaultedList<ItemStack> fridgeInv = DefaultedList.ofSize(20, ItemStack.EMPTY);
    private final DefaultedList<ItemStack> cookInv   = DefaultedList.ofSize(5, ItemStack.EMPTY);

    private int burnTime = 0;
    private int burnTimeTotal = 0;

    private boolean cooking = false;
    private boolean finished = false;
    private int cookTime = 0;

    private int cookTimeTotal = 200;
    private int minStirsRequired = 3;

    private int stirs = 0;
    private long lastStirTime = 0;
    private boolean ruined = false;

    private ItemStack result = ItemStack.EMPTY;
    private ItemStack plannedOutput = ItemStack.EMPTY;

    private int emergeTicks = 20;
    private static final int EMERGE_DURATION = 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE     = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation EMERGE   = RawAnimation.begin().thenPlayOnce("emerge").thenLoop("idle");
    private static final RawAnimation COOKLOOP = RawAnimation.begin().thenLoop("cook_loop");
    private static final RawAnimation STIR     = RawAnimation.begin().thenPlayOnce("stirring");

    public SuperCookerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SUPER_COOKER_BE, pos, state);
    }

    public void serverStartEmerge() {
        this.emergeTicks = 0;
        markDirtyAndSync();
    }

    public int getEmergeTicks() { return emergeTicks; }
    public boolean isFinished() { return finished; }

    /** Texture “enabled” when there’s food/active cooking. */
    public boolean isEnabledTexture() {
        return cooking || finished || hasAnyCookIngredients();
    }

    public boolean canStir() {
        return hasAnyCookIngredients() || cooking;
    }

    public boolean tryInsertIngredient(net.minecraft.entity.player.PlayerEntity player, Hand hand) {
        if (cooking || finished) return false;

        ItemStack held = player.getStackInHand(hand);
        if (!Chef.isIngredient(held)) return false;

        for (int i = 0; i < cookInv.size(); i++) {
            if (cookInv.get(i).isEmpty()) {
                ItemStack one = held.copy();
                one.setCount(1);
                cookInv.set(i, one);
                if (!player.getAbilities().creativeMode) held.decrement(1);
                markDirtyAndSync();
                return true;
            }
        }
        return false;
    }

    public void serverStir() {
        if (!(world instanceof ServerWorld sw)) return;

        this.lastStirTime = sw.getTime();
        this.stirs++;

        // Trigger client-side stir anim
        ChefNet.s2cCookerAnim(sw, pos, "stir");

        // First stir starts the cook attempt
        if (!cooking && !finished) {
            startCookingOrMush(sw);
        }

        markDirtyAndSync();
    }

    private void startCookingOrMush(ServerWorld sw) {
        Optional<SuperCookerRecipe> match = findMatchingRecipe(sw);

        if (match.isEmpty()) {
            finishIntoMush(sw);
            return;
        }

        SuperCookerRecipe r = match.get();
        this.cookTime = 0;
        this.cookTimeTotal = r.getCookTime();
        this.minStirsRequired = r.getMinStirs();
        this.ruined = false;

        this.plannedOutput = r.getOutputCopy();
        this.cooking = true;
        this.finished = false;
        this.result = ItemStack.EMPTY;

        // Consume ingredients now (no swapping mid-cook)
        clearCookIngredients();

        // Make sure we have fuel
        tryConsumeFuelIfNeeded();
    }

    private Optional<SuperCookerRecipe> findMatchingRecipe(ServerWorld sw) {
        SimpleInventory inv = new SimpleInventory(cookInv.size());
        for (int i = 0; i < cookInv.size(); i++) inv.setStack(i, cookInv.get(i));
        return sw.getRecipeManager()
                .getFirstMatch(ModRecipes.SUPER_COOKER_TYPE, inv, sw)
                .map(x -> (SuperCookerRecipe) x.getValue());
    }

    private boolean hasAnyCookIngredients() {
        for (ItemStack s : cookInv) if (!s.isEmpty()) return true;
        return false;
    }

    private void clearCookIngredients() {
        for (int i = 0; i < cookInv.size(); i++) cookInv.set(i, ItemStack.EMPTY);
    }

    private void tryConsumeFuelIfNeeded() {
        if (burnTime > 0) return;

        ItemStack fuel = fuelInv.get(0);
        Integer t = fuel.isEmpty() ? null : FuelRegistry.INSTANCE.get(fuel.getItem());
        if (t == null || t <= 0) return;

        burnTime = t;
        burnTimeTotal = t;

        fuel.decrement(1);
        if (fuel.isEmpty()) fuelInv.set(0, ItemStack.EMPTY);
    }

    private void finishIntoMush(ServerWorld sw) {
        this.cooking = false;
        this.finished = true;
        this.result = new ItemStack(ModItems.MUSH);
        this.plannedOutput = ItemStack.EMPTY;
        clearCookIngredients();
        spawnDoneSmoke(sw);
    }

    private void finishInto(ServerWorld sw, ItemStack out) {
        this.cooking = false;
        this.finished = true;
        this.result = out.copy();
        this.plannedOutput = ItemStack.EMPTY;
        spawnDoneSmoke(sw);
    }

    private void spawnDoneSmoke(ServerWorld sw) {
        // geo top is 18/16 = 1.125 blocks :contentReference[oaicite:3]{index=3}
        double x = pos.getX() + 0.5;
        double y = pos.getY() + (18.0 / 16.0) + 0.05;
        double z = pos.getZ() + 0.5;
        sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 8, 0.15, 0.08, 0.15, 0.001);
    }

    public boolean tryGiveResult(net.minecraft.entity.player.PlayerEntity player) {
        if (!finished || result.isEmpty()) return false;

        ItemStack give = result.copy();
        result = ItemStack.EMPTY;
        finished = false;
        cooking = false;
        cookTime = 0;
        stirs = 0;
        ruined = false;

        if (!player.getInventory().insertStack(give)) {
            player.dropItem(give, false);
        }
        markDirtyAndSync();
        return true;
    }

    public static void tickServer(ServerWorld sw, BlockPos pos, SuperCookerBlockEntity be) {
        if (be.emergeTicks < EMERGE_DURATION) {
            be.emergeTicks++;
            be.markDirtyAndSync();
        }

        if (be.burnTime > 0) be.burnTime--;

        // keep burning while cooking
        if (be.cooking && be.burnTime == 0) be.tryConsumeFuelIfNeeded();

        if (be.cooking) {
            // If no burn, you ruin if it stays off too long
            if (be.burnTime <= 0) {
                if (sw.getTime() - be.lastStirTime > 100) be.ruined = true;
                be.markDirtyAndSync();
                return;
            }

            // If you don't stir for too long, ruined
            if (sw.getTime() - be.lastStirTime > 80) be.ruined = true;

            be.cookTime++;

            // flame near furnace base (0..8 => around y=4px) :contentReference[oaicite:4]{index=4}
            if (sw.random.nextFloat() < 0.15f && be.isEnabledTexture()) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + (4.0 / 16.0);
                double z = pos.getZ() + 0.5;
                sw.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0.05, 0.03, 0.05, 0.0);
                sw.spawnParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 1, 0.05, 0.03, 0.05, 0.0);
            }

            if (be.cookTime >= be.cookTimeTotal) {
                if (be.ruined || be.stirs < be.minStirsRequired || be.plannedOutput.isEmpty()) {
                    be.finishInto(sw, new ItemStack(ModItems.MUSH));
                } else {
                    be.finishInto(sw, be.plannedOutput);
                }
            }

            be.markDirtyAndSync();
        }
    }

    /** Combine for ItemScatterer on break. */
    public Inventory asDropInventory() {
        SimpleInventory inv = new SimpleInventory(1 + 20 + 5 + 1);
        int i = 0;
        inv.setStack(i++, fuelInv.get(0));
        for (ItemStack s : fridgeInv) inv.setStack(i++, s);
        for (ItemStack s : cookInv) inv.setStack(i++, s);
        inv.setStack(i, result);
        return inv;
    }

    public Inventory fuelInventoryView()   { return new ListInventory(fuelInv,  this, (slot, st) -> FuelRegistry.INSTANCE.get(st.getItem()) != null); }
    public Inventory fridgeInventoryView() { return new ListInventory(fridgeInv,this, (slot, st) -> Chef.isIngredient(st)); }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        NbtCompound fuel = new NbtCompound();
        Inventories.writeNbt(fuel, fuelInv);
        nbt.put("FuelInv", fuel);

        NbtCompound fridge = new NbtCompound();
        Inventories.writeNbt(fridge, fridgeInv);
        nbt.put("FridgeInv", fridge);

        NbtCompound cook = new NbtCompound();
        Inventories.writeNbt(cook, cookInv);
        nbt.put("CookInv", cook);

        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("BurnTimeTotal", burnTimeTotal);

        nbt.putBoolean("Cooking", cooking);
        nbt.putBoolean("Finished", finished);
        nbt.putInt("CookTime", cookTime);
        nbt.putInt("CookTimeTotal", cookTimeTotal);
        nbt.putInt("MinStirs", minStirsRequired);
        nbt.putInt("Stirs", stirs);
        nbt.putLong("LastStir", lastStirTime);
        nbt.putBoolean("Ruined", ruined);

        nbt.putInt("EmergeTicks", emergeTicks);

        if (!result.isEmpty()) {
            NbtCompound r = new NbtCompound();
            result.writeNbt(r);
            nbt.put("Result", r);
        }
        if (!plannedOutput.isEmpty()) {
            NbtCompound r = new NbtCompound();
            plannedOutput.writeNbt(r);
            nbt.put("Planned", r);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        Inventories.readNbt(nbt.getCompound("FuelInv"), fuelInv);
        Inventories.readNbt(nbt.getCompound("FridgeInv"), fridgeInv);
        Inventories.readNbt(nbt.getCompound("CookInv"), cookInv);

        burnTime = nbt.getInt("BurnTime");
        burnTimeTotal = nbt.getInt("BurnTimeTotal");

        cooking = nbt.getBoolean("Cooking");
        finished = nbt.getBoolean("Finished");
        cookTime = nbt.getInt("CookTime");
        cookTimeTotal = nbt.getInt("CookTimeTotal");
        minStirsRequired = nbt.getInt("MinStirs");
        stirs = nbt.getInt("Stirs");
        lastStirTime = nbt.getLong("LastStir");
        ruined = nbt.getBoolean("Ruined");

        emergeTicks = nbt.getInt("EmergeTicks");

        result = ItemStack.EMPTY;
        if (nbt.contains("Result")) result = ItemStack.fromNbt(nbt.getCompound("Result"));

        plannedOutput = ItemStack.EMPTY;
        if (nbt.contains("Planned")) plannedOutput = ItemStack.fromNbt(nbt.getCompound("Planned"));
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        return nbt;
    }

    @Override
    public net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", state -> {
            if (emergeTicks < EMERGE_DURATION) return state.setAndContinue(EMERGE);
            if (cooking) return state.setAndContinue(COOKLOOP);
            return state.setAndContinue(IDLE);
        }).triggerableAnim("stir", STIR));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Inventory wrapper with validation + sync. */
    private static final class ListInventory implements Inventory {
        interface Validator { boolean isValid(int slot, ItemStack stack); }

        private final DefaultedList<ItemStack> list;
        private final SuperCookerBlockEntity owner;
        private final Validator validator;

        ListInventory(DefaultedList<ItemStack> list, SuperCookerBlockEntity owner, Validator validator) {
            this.list = list;
            this.owner = owner;
            this.validator = validator;
        }

        @Override public int size() { return list.size(); }

        @Override public boolean isEmpty() {
            for (ItemStack s : list) if (!s.isEmpty()) return false;
            return true;
        }

        @Override public ItemStack getStack(int slot) { return list.get(slot); }

        @Override public ItemStack removeStack(int slot, int amount) {
            ItemStack out = Inventories.splitStack(list, slot, amount);
            if (!out.isEmpty()) markDirty();
            return out;
        }

        @Override public ItemStack removeStack(int slot) {
            ItemStack out = Inventories.removeStack(list, slot);
            if (!out.isEmpty()) markDirty();
            return out;
        }

        @Override public void setStack(int slot, ItemStack stack) {
            if (!isValid(slot, stack)) return;
            list.set(slot, stack);
            if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
            markDirty();
        }

        @Override public void markDirty() { owner.markDirtyAndSync(); }

        @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return true; }

        @Override public void clear() {
            list.clear();
            markDirty();
        }

        @Override public boolean isValid(int slot, ItemStack stack) {
            return validator == null || validator.isValid(slot, stack);
        }
    }
}
