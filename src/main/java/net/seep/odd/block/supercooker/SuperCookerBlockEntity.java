// src/main/java/net/seep/odd/block/supercooker/SuperCookerBlockEntity.java
package net.seep.odd.block.supercooker;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.seep.odd.abilities.chef.ChefFridgeData;
import net.seep.odd.abilities.chef.ChefFuelData;
import net.seep.odd.abilities.chef.net.ChefNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.supercooker.recipe.SuperCookerRecipe;
import net.seep.odd.item.ModItems;
import net.seep.odd.recipe.ModRecipes;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class SuperCookerBlockEntity extends BlockEntity implements GeoBlockEntity {

    /* =========================
       Fuel (persistent like fridge)
       ========================= */

    @Nullable private UUID owner = null;    // whoever first used this cooker
    private boolean fuelPresent = false;     // synced to client for visuals

    private int burnTime = 0;
    private int burnTimeTotal = 0;

    /* =========================
       Cooktop
       ========================= */

    private final DefaultedList<ItemStack> cookInv = DefaultedList.ofSize(5, ItemStack.EMPTY);
    private final DefaultedList<ItemStack> cookingDisplay = DefaultedList.ofSize(5, ItemStack.EMPTY);

    private boolean cooking = false;
    private boolean finished = false;
    private int cookTime = 0;

    private int cookTimeTotal = 200;
    private int minGoodStirsRequired = 3;

    private ItemStack result = ItemStack.EMPTY;
    private ItemStack plannedOutput = ItemStack.EMPTY;

    private int emergeTicks = 20;
    private static final int EMERGE_DURATION = 20;

    private long nextStirAt = 0;
    private int stirWindow = 8;
    private int goodStirs = 0;
    private int misses = 0;
    private static final int MAX_MISSES_FOR_MUSH = 3;

    private int stirVisualTicks = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE     = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation EMERGE   = RawAnimation.begin().thenPlay("emerge").thenLoop("idle");
    private static final RawAnimation COOKLOOP = RawAnimation.begin().thenLoop("cook_loop");
    private static final RawAnimation STIR     = RawAnimation.begin().thenPlay("stirring"); // trigger once

    public SuperCookerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SUPER_COOKER_BE, pos, state);
    }

    /* ----------------- emerge trigger ----------------- */
    public void serverStartEmerge() {
        this.emergeTicks = 0;
        markDirtyAndSync();
    }

    /* ----------------- getters for renderer/HUD ----------------- */
    public boolean isCooking() { return cooking; }
    public boolean isFinished() { return finished; }
    public int getEmergeTicks() { return emergeTicks; }

    public long getNextStirAt() { return nextStirAt; }
    public int getStirWindow() { return stirWindow; }
    public int getGoodStirs() { return goodStirs; }
    public int getMinGoodStirsRequired() { return minGoodStirsRequired; }
    public int getMisses() { return misses; }
    public int getStirVisualTicks() { return stirVisualTicks; }

    /** Texture swap ONLY when there’s fuel present/burning. */
    public boolean isFueledVisual() {
        return burnTime > 0 || fuelPresent;
    }

    /** Used by renderer: show ingredients while cooking; otherwise show placed. */
    public DefaultedList<ItemStack> getRenderIngredients() {
        return cooking ? cookingDisplay : cookInv;
    }

    /** Used by renderer: show result when finished. */
    public ItemStack getResultForRender() {
        return finished ? result : ItemStack.EMPTY;
    }

    /* =========================
       Owner + persistent fuel helpers
       ========================= */

    private UUID ensureOwner(PlayerEntity p) {
        if (owner == null) {
            owner = p.getUuid();
            // refresh visual cache immediately on server
            if (world instanceof ServerWorld sw) refreshFuelPresent(sw);
            markDirtyAndSync();
        }
        return owner;
    }

    private void refreshFuelPresent(ServerWorld sw) {
        if (owner == null) { fuelPresent = false; return; }
        ChefFuelData data = ChefFuelData.get(sw);
        ItemStack fuel = data.getList(owner).get(0);
        fuelPresent = fuel != null && !fuel.isEmpty();
    }

    private boolean hasFuelAvailable(ServerWorld sw, PlayerEntity context) {
        if (burnTime > 0) return true;

        UUID key = (owner != null) ? owner : context.getUuid();
        ChefFuelData data = ChefFuelData.get(sw);
        ItemStack fuel = data.getList(key).get(0);

        if (fuel.isEmpty()) return false;
        Integer t = FuelRegistry.INSTANCE.get(fuel.getItem());
        return t != null && t > 0;
    }

    private void tryConsumeFuelIfNeeded(ServerWorld sw) {
        if (burnTime > 0) return;
        if (owner == null) return;

        ChefFuelData data = ChefFuelData.get(sw);
        var list = data.getList(owner);
        ItemStack fuel = list.get(0);

        Integer t = fuel.isEmpty() ? null : FuelRegistry.INSTANCE.get(fuel.getItem());
        if (t == null || t <= 0) return;

        burnTime = t;
        burnTimeTotal = t;

        fuel.decrement(1);
        if (fuel.isEmpty()) list.set(0, ItemStack.EMPTY);

        data.markDirty();
        refreshFuelPresent(sw);
        markDirtyAndSync();
    }

    /* ----------------- UI inventories ----------------- */

    /** Vanilla 9x1 UI; only slot 0 is real. Fuel is stored per-player persistently (like fridge). */
    public Inventory fuelUiInventory(PlayerEntity player) {
        if (!(world instanceof ServerWorld sw)) return new SimpleInventory(9);

        UUID key = ensureOwner(player);
        ChefFuelData data = ChefFuelData.get(sw);
        var list = data.getList(key);

        // keep visual cache fresh when UI opens
        refreshFuelPresent(sw);

        return new UiInventory(9) {
            @Override public ItemStack getStack(int slot) {
                return slot == 0 ? list.get(0) : ItemStack.EMPTY;
            }

            @Override public void setStack(int slot, ItemStack stack) {
                if (slot != 0 || !isValid(slot, stack)) {
                    spill(stack);
                    data.markDirty();
                    refreshFuelPresent(sw);
                    markDirtyAndSync();
                    return;
                }

                list.set(0, stack == null ? ItemStack.EMPTY : stack);
                if (stack != null && stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());

                data.markDirty();
                refreshFuelPresent(sw);
                markDirtyAndSync();
            }

            @Override public ItemStack removeStack(int slot, int amount) {
                if (slot != 0) return ItemStack.EMPTY;
                ItemStack out = Inventories.splitStack(list, 0, amount);
                if (!out.isEmpty()) {
                    data.markDirty();
                    refreshFuelPresent(sw);
                    markDirtyAndSync();
                }
                return out;
            }

            @Override public ItemStack removeStack(int slot) {
                if (slot != 0) return ItemStack.EMPTY;
                ItemStack out = Inventories.removeStack(list, 0);
                if (!out.isEmpty()) {
                    data.markDirty();
                    refreshFuelPresent(sw);
                    markDirtyAndSync();
                }
                return out;
            }

            @Override public boolean isValid(int slot, ItemStack stack) {
                if (slot != 0) return false;
                if (stack == null || stack.isEmpty()) return true;
                Integer t = FuelRegistry.INSTANCE.get(stack.getItem());
                return t != null && t > 0;
            }
        };
    }

    /**
     * Vanilla 9x3 UI (27 slots) but only first 20 are used.
     * Enderchest-like: shared per-player via ChefFridgeData.
     */
    public Inventory fridgeUiInventory(PlayerEntity player) {
        if (!(world instanceof ServerWorld sw)) return new SimpleInventory(27);

        ChefFridgeData data = ChefFridgeData.get(sw);
        DefaultedList<ItemStack> list = data.getList(player.getUuid());

        return new UiInventory(27) {
            @Override public ItemStack getStack(int slot) {
                return (slot >= 0 && slot < ChefFridgeData.SIZE) ? list.get(slot) : ItemStack.EMPTY;
            }

            @Override public void setStack(int slot, ItemStack stack) {
                if (slot < 0 || slot >= ChefFridgeData.SIZE) {
                    spill(stack);
                    data.markDirty();
                    return;
                }

                if (stack != null && !stack.isEmpty() && !Chef.isIngredient(stack)) {
                    spill(stack);
                    data.markDirty();
                    return;
                }

                list.set(slot, stack == null ? ItemStack.EMPTY : stack);
                if (stack != null && stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
                data.markDirty();
            }

            @Override public ItemStack removeStack(int slot, int amount) {
                if (slot < 0 || slot >= ChefFridgeData.SIZE) return ItemStack.EMPTY;
                ItemStack out = Inventories.splitStack(list, slot, amount);
                if (!out.isEmpty()) data.markDirty();
                return out;
            }

            @Override public ItemStack removeStack(int slot) {
                if (slot < 0 || slot >= ChefFridgeData.SIZE) return ItemStack.EMPTY;
                ItemStack out = Inventories.removeStack(list, slot);
                if (!out.isEmpty()) data.markDirty();
                return out;
            }

            @Override public void markDirty() { data.markDirty(); }
            @Override public boolean isValid(int slot, ItemStack stack) { return true; }
        };
    }

    private void spill(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (!(world instanceof ServerWorld sw)) return;

        ItemStack drop = stack.copy();
        ItemEntity e = new ItemEntity(sw, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, drop);
        e.setToDefaultPickupDelay();
        sw.spawnEntity(e);
    }

    private abstract static class UiInventory implements Inventory {
        private final int size;
        UiInventory(int size) { this.size = size; }
        @Override public int size() { return size; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < size; i++) if (!getStack(i).isEmpty()) return false;
            return true;
        }
        @Override public void clear() {
            for (int i = 0; i < size; i++) setStack(i, ItemStack.EMPTY);
        }
        @Override public int getMaxCountPerStack() { return 64; }
        @Override public void markDirty() {}
        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
    }

    /* ----------------- cooktop interactions ----------------- */

    public boolean tryInsertIngredient(PlayerEntity player, Hand hand) {
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

    /** Shift-right-click: pull one ingredient back (only when not cooking/finished). */
    public boolean tryTakeOneIngredient(PlayerEntity player) {
        if (cooking || finished) return false;

        for (int i = cookInv.size() - 1; i >= 0; i--) {
            ItemStack s = cookInv.get(i);
            if (s.isEmpty()) continue;

            cookInv.set(i, ItemStack.EMPTY);
            markDirtyAndSync();

            ItemStack give = s.copy();
            if (!player.getInventory().insertStack(give)) player.dropItem(give, false);
            return true;
        }
        return false;
    }

    /** Despawn/break helper: drops ingredients + in-progress + result. Fuel is NOT dropped anymore. */
    public void dropAllCookerContents(ServerWorld sw) {
        for (int i = 0; i < cookInv.size(); i++) {
            dropStack(sw, cookInv.get(i));
            cookInv.set(i, ItemStack.EMPTY);
        }

        for (int i = 0; i < cookingDisplay.size(); i++) {
            dropStack(sw, cookingDisplay.get(i));
            cookingDisplay.set(i, ItemStack.EMPTY);
        }

        dropStack(sw, result);
        result = ItemStack.EMPTY;

        markDirtyAndSync();
    }

    private void dropStack(ServerWorld sw, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemEntity e = new ItemEntity(sw, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, stack.copy());
        e.setToDefaultPickupDelay();
        sw.spawnEntity(e);
    }

    /** Right-click stir: requires ingredients + fuel to START; minigame scoring. */
    public void serverStir(PlayerEntity player) {
        if (!(world instanceof ServerWorld sw)) return;

        ensureOwner(player);
        refreshFuelPresent(sw);

        if (!cooking && !finished) {
            boolean hasAny = false;
            for (int i = 0; i < cookInv.size(); i++) {
                if (!cookInv.get(i).isEmpty()) { hasAny = true; break; }
            }
            if (!hasAny) {
                player.sendMessage(net.minecraft.text.Text.literal("Add in food first!"), true);
                return;
            }

            if (!hasFuelAvailable(sw, player)) {
                player.sendMessage(net.minecraft.text.Text.literal("Fuel the cooker first!"), true);
                return;
            }
        }

        stirVisualTicks = 12;
        ChefNet.s2cCookerAnim(sw, pos, "stir");

        if (!cooking && !finished) {
            startCookingOrMush(sw);
            markDirtyAndSync();
            return;
        }

        if (!cooking) return;

        long now = sw.getTime();
        boolean inWindow = Math.abs(now - nextStirAt) <= stirWindow;

        if (inWindow) goodStirs++;
        else misses++;

        scheduleNextStir(sw);
        markDirtyAndSync();
    }

    private void startCookingOrMush(ServerWorld sw) {
        // ✅ always consume/ignite fuel when you begin cooking attempt
        tryConsumeFuelIfNeeded(sw);

        Optional<SuperCookerRecipe> match = findMatchingRecipe(sw);
        if (match.isEmpty()) {
            // ✅ mush should consume ingredients too
            finishIntoMush(sw);
            return;
        }

        SuperCookerRecipe r = match.get();

        cookTime = 0;
        cookTimeTotal = r.getCookTime();
        minGoodStirsRequired = r.getMinStirs();

        plannedOutput = r.getOutputCopy();
        cooking = true;
        finished = false;
        result = ItemStack.EMPTY;

        // snapshot ingredients for visuals DURING cooking
        for (int i = 0; i < cookInv.size(); i++) cookingDisplay.set(i, cookInv.get(i).copy());

        // consume ingredients
        for (int i = 0; i < cookInv.size(); i++) cookInv.set(i, ItemStack.EMPTY);

        goodStirs = 0;
        misses = 0;
        stirWindow = 8;

        scheduleFirstStir(sw);
    }

    private void scheduleFirstStir(ServerWorld sw) {
        long now = sw.getTime();
        nextStirAt = now + 40;
    }

    private void scheduleNextStir(ServerWorld sw) {
        long now = sw.getTime();
        int interval = 30 + sw.random.nextInt(31);
        nextStirAt = now + interval;
    }

    private Optional<SuperCookerRecipe> findMatchingRecipe(ServerWorld sw) {
        SimpleInventory inv = new SimpleInventory(cookInv.size());
        for (int i = 0; i < cookInv.size(); i++) inv.setStack(i, cookInv.get(i));
        return sw.getRecipeManager().getFirstMatch(ModRecipes.SUPER_COOKER_TYPE, inv, sw);
    }

    private void finishIntoMush(ServerWorld sw) {
        cooking = false;
        finished = true;
        result = new ItemStack(ModItems.MUSH);
        plannedOutput = ItemStack.EMPTY;

        // ✅ CONSUME ingredients even on failure
        for (int i = 0; i < cookInv.size(); i++) cookInv.set(i, ItemStack.EMPTY);
        for (int i = 0; i < cookingDisplay.size(); i++) cookingDisplay.set(i, ItemStack.EMPTY);

        spawnDoneSparkles(sw, false);
        markDirtyAndSync();
    }

    private void finishInto(ServerWorld sw, ItemStack out) {
        cooking = false;
        finished = true;
        result = out.copy();
        plannedOutput = ItemStack.EMPTY;

        for (int i = 0; i < cookingDisplay.size(); i++) cookingDisplay.set(i, ItemStack.EMPTY);

        spawnDoneSparkles(sw, true);
        markDirtyAndSync();
    }

    private void spawnDoneSparkles(ServerWorld sw, boolean success) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + (18.0 / 16.0) + 0.08;
        double z = pos.getZ() + 0.5;

        if (success) {
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 16, 0.22, 0.08, 0.22, 0.01);
            sw.spawnParticles(ParticleTypes.END_ROD,       x, y, z, 10, 0.18, 0.06, 0.18, 0.01);
        } else {
            sw.spawnParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.18, 0.06, 0.18, 0.002);
        }
    }

    public boolean tryGiveResult(PlayerEntity player) {
        if (!finished || result.isEmpty()) return false;

        ItemStack give = result.copy();

        result = ItemStack.EMPTY;
        finished = false;
        cooking = false;
        cookTime = 0;

        goodStirs = 0;
        misses = 0;
        nextStirAt = 0;
        stirVisualTicks = 0;

        if (!player.getInventory().insertStack(give)) player.dropItem(give, false);

        markDirtyAndSync();
        return true;
    }

    /* ----------------- ticking ----------------- */

    public static void tickServer(ServerWorld sw, BlockPos pos, SuperCookerBlockEntity be) {
        if (be.emergeTicks < EMERGE_DURATION) {
            be.emergeTicks++;
            be.markDirtyAndSync();
        }

        if (be.stirVisualTicks > 0) be.stirVisualTicks--;

        if (be.burnTime > 0) be.burnTime--;

        if (be.cooking) {
            if (be.burnTime == 0) be.tryConsumeFuelIfNeeded(sw);

            if (be.burnTime > 0 && sw.random.nextFloat() < 0.18f) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + (18.0 / 16.0) + 0.03;
                double z = pos.getZ() + 0.5;
                sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0.08, 0.02, 0.08, 0.0005);
            }

            long now = sw.getTime();
            if (now > be.nextStirAt + be.stirWindow) {
                be.misses++;
                be.scheduleNextStir(sw);
            }

            if (be.misses >= MAX_MISSES_FOR_MUSH) {
                be.finishIntoMush(sw);
                return;
            }

            be.cookTime++;

            if (be.cookTime >= be.cookTimeTotal) {
                if (be.goodStirs < be.minGoodStirsRequired || be.plannedOutput.isEmpty()) {
                    be.finishIntoMush(sw);
                    return;
                }

                ItemStack out = be.plannedOutput.copy();
                if (be.misses == 0) {
                    int doubled = out.getCount() * 2;
                    out.setCount(Math.min(doubled, out.getMaxCount()));
                }

                be.finishInto(sw, out);
            }

            be.markDirtyAndSync();
        } else if (be.finished) {
            if (sw.random.nextFloat() < 0.06f) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + (18.0 / 16.0) + 0.08;
                double z = pos.getZ() + 0.5;
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.15, 0.05, 0.15, 0.01);
            }
        }
    }

    /* ----------------- drops ----------------- */

    /** Used on block-break. Fuel is persistent, so it is NOT dropped. */
    public Inventory asDropInventory() {
        // cookInv(5) + cookingDisplay(5) + result(1) = 11
        SimpleInventory inv = new SimpleInventory(11);
        int i = 0;

        for (ItemStack s : cookInv) inv.setStack(i++, s);
        for (ItemStack s : cookingDisplay) inv.setStack(i++, s);
        inv.setStack(i, result);

        return inv;
    }

    private static void clear(DefaultedList<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) list.set(i, ItemStack.EMPTY);
    }

    /* ----------------- sync/nbt ----------------- */

    private void markDirtyAndSync() {
        markDirty();

        if (world != null && !world.isClient) {
            ((ServerWorld) world).getChunkManager().markForUpdate(pos);
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        // owner + fuel presence cache (fuel itself is in ChefFuelData)
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putBoolean("FuelPresent", fuelPresent);

        NbtCompound cook = new NbtCompound();
        Inventories.writeNbt(cook, cookInv);
        nbt.put("CookInv", cook);

        NbtCompound disp = new NbtCompound();
        Inventories.writeNbt(disp, cookingDisplay);
        nbt.put("CookDisp", disp);

        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("BurnTimeTotal", burnTimeTotal);

        nbt.putBoolean("Cooking", cooking);
        nbt.putBoolean("Finished", finished);
        nbt.putInt("CookTime", cookTime);
        nbt.putInt("CookTimeTotal", cookTimeTotal);
        nbt.putInt("MinGoodStirs", minGoodStirsRequired);

        nbt.putLong("NextStirAt", nextStirAt);
        nbt.putInt("StirWindow", stirWindow);
        nbt.putInt("GoodStirs", goodStirs);
        nbt.putInt("Misses", misses);
        nbt.putInt("StirVis", stirVisualTicks);

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

        owner = nbt.contains("Owner") ? nbt.getUuid("Owner") : null;
        fuelPresent = nbt.getBoolean("FuelPresent");

        clear(cookInv);
        Inventories.readNbt(nbt.getCompound("CookInv"), cookInv);

        clear(cookingDisplay);
        Inventories.readNbt(nbt.getCompound("CookDisp"), cookingDisplay);

        burnTime = nbt.getInt("BurnTime");
        burnTimeTotal = nbt.getInt("BurnTimeTotal");

        cooking = nbt.getBoolean("Cooking");
        finished = nbt.getBoolean("Finished");
        cookTime = nbt.getInt("CookTime");
        cookTimeTotal = nbt.getInt("CookTimeTotal");
        minGoodStirsRequired = nbt.getInt("MinGoodStirs");

        nextStirAt = nbt.getLong("NextStirAt");
        stirWindow = nbt.getInt("StirWindow");
        goodStirs = nbt.getInt("GoodStirs");
        misses = nbt.getInt("Misses");
        stirVisualTicks = nbt.getInt("StirVis");

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

    /* ----------------- geckolib ----------------- */

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
}