// src/main/java/net/seep/odd/block/combiner/CombinerBlockEntity.java
package net.seep.odd.block.combiner;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.combiner.net.CombinerNet;
import net.seep.odd.block.combiner.recipe.ModCombinerRecipes;
import net.seep.odd.block.combiner.recipe.CombinerRecipe;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.jetbrains.annotations.Nullable;

public class CombinerBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ExtendedScreenHandlerFactory, GeoBlockEntity {

    // props (same indices)
    public static final int P_ACTIVE   = 0;
    public static final int P_DURATION = 1;
    public static final int P_PROGRESS = 2;
    public static final int P_SUCC     = 3;
    public static final int P_REQ      = 4;
    public static final int P_DIFF     = 5;
    public static final int P_SEED     = 6;

    // wider window so the QTE is a bit more forgiving for higher-ping players
    private static final int HIT_TOLERANCE_TICKS = 12;

    // lighter anti-spam so quick follow-up presses do not get eaten as often
    private static final int HIT_COOLDOWN_TICKS = 1;

    // slightly longer total QTE so the client-local version has a fairer buffer
    private static final int QTE_DURATION_TICKS = 96;

    // inventory: 0 gear, 1 trim template
    private final SimpleInventory inv = new SimpleInventory(2) {
        @Override public void markDirty() {
            super.markDirty();
            CombinerBlockEntity.this.markDirtyAndSync();
        }
    };
    public SimpleInventory inventory() { return inv; }

    // qte state
    private boolean qteActive = false;
    private int qteTicks = 0;
    private int qteDuration = QTE_DURATION_TICKS;
    private int qteSuccesses = 0;
    private int qteRequired = 1;
    private int qteDifficulty = 1;
    private int qteSeed = 0;

    @Nullable private String recipeId = null;

    // ✅ cached marks + per-mark used state (prevents spamming the same mark)
    private int[] cachedMarks = null;
    private boolean[] markUsed = null;

    // ✅ server-side hit throttle
    private long lastHitWorldTick = -9999;

    // menu open -> animation
    private int openCount = 0;
    private boolean menuOpen = false;

    // emerge like cooker
    private int emergeTicks = 20;
    private static final int EMERGE_DURATION = 20;

    // geckolib
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE          = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation CRAFT_IN_HOLD = RawAnimation.begin().thenPlayAndHold("crafting");
    private static final RawAnimation CRAFT_OUT     = RawAnimation.begin().thenPlay("crafting_back");
    private static final RawAnimation HIT_SUCCESS   = RawAnimation.begin().thenPlay("hit_success");

    // client transition memory
    private boolean clientPrevOpen = false;
    private boolean clientClosing = false;

    // property delegate
    private final PropertyDelegate props = new ArrayPropertyDelegate(7) {
        @Override public int get(int i) {
            return switch (i) {
                case P_ACTIVE   -> qteActive ? 1 : 0;
                case P_DURATION -> qteDuration;
                case P_PROGRESS -> qteTicks;
                case P_SUCC     -> qteSuccesses;
                case P_REQ      -> qteRequired;
                case P_DIFF     -> qteDifficulty;
                case P_SEED     -> qteSeed;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) { /* client ignores */ }
        @Override public int size() { return 7; }
    };
    public PropertyDelegate getProps() { return props; }

    public CombinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.COMBINER_BE, pos, state);
    }

    /* ================= emerge ================= */

    public void serverStartEmerge() {
        this.emergeTicks = 0;
        markDirtyAndSync();
    }
    public int getEmergeTicks() { return emergeTicks; }

    /* ================= menu hooks ================= */

    public void onMenuOpened(PlayerEntity p) {
        if (world == null || world.isClient) return;
        openCount++;
        if (!menuOpen) { menuOpen = true; markDirtyAndSync(); }
    }

    public void onMenuClosed(PlayerEntity p) {
        if (world == null || world.isClient) return;
        openCount = Math.max(0, openCount - 1);
        if (openCount == 0 && menuOpen) { menuOpen = false; markDirtyAndSync(); }
    }

    /* ================= ticking ================= */

    public static void tickServer(World w, BlockPos pos, BlockState state, CombinerBlockEntity be) {
        if (!(w instanceof ServerWorld sw)) return;

        if (be.emergeTicks < EMERGE_DURATION) {
            be.emergeTicks++;
            be.markDirtyAndSync();
        }

        if (!be.qteActive) return;

        be.qteTicks++;
        if (be.qteTicks >= be.qteDuration) {
            be.finishQte(false);
        } else {
            be.markDirtyAndSync();
        }
    }

    /* ================= QTE start ================= */

    public void startQte() {
        if (world == null || world.isClient) return;

        ItemStack gear = inv.getStack(0);
        ItemStack trim = inv.getStack(1);

        var opt = world.getRecipeManager().listAllOfType(ModCombinerRecipes.TYPE).stream()
                .filter(r -> r.matches(gear, trim))
                .findFirst();
        if (opt.isEmpty()) return;

        CombinerRecipe rec = opt.get();

        this.recipeId = rec.getId().toString();
        this.qteDifficulty = rec.difficulty();
        this.qteSeed = world.getRandom().nextInt();

        this.qteActive = true;
        this.qteTicks = 0;
        this.qteDuration = QTE_DURATION_TICKS;
        this.qteSuccesses = 0;
        this.qteRequired = qteDifficulty;

        // ✅ cache marks and used flags
        this.cachedMarks = randomizedMarks(qteDifficulty, qteDuration, qteSeed);
        this.markUsed = new boolean[this.cachedMarks.length];

        this.lastHitWorldTick = -9999;

        markDirtyAndSync();
    }

    /* ================= HIT (client-lag-proof) ================= */

    /**
     * ✅ clientTick is judged against marks directly, so ping doesn't make the QTE feel laggy.
     */
// inside CombinerBlockEntity.java

    public void hitFromNet(ServerPlayerEntity player, int clientTick) {
        if (!qteActive || world == null || world.isClient) return;
        ServerWorld sw = (ServerWorld) world;

        long now = sw.getTime();
        if (now < lastHitWorldTick + HIT_COOLDOWN_TICKS) return; // ignore spam
        lastHitWorldTick = now;

        // invalid tick = immediate fail
        if (clientTick < 0 || clientTick > qteDuration) {
            failNow(); // ✅ consume tablet + stop event immediately
            return;
        }

        if (cachedMarks == null) cachedMarks = randomizedMarks(qteDifficulty, qteDuration, qteSeed);
        if (markUsed == null || markUsed.length != cachedMarks.length) markUsed = new boolean[cachedMarks.length];

        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;

        for (int i = 0; i < cachedMarks.length; i++) {
            if (markUsed[i]) continue;
            int d = Math.abs(clientTick - cachedMarks[i]);
            if (d <= HIT_TOLERANCE_TICKS && d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }

        // MISS = immediate fail
        if (bestIdx == -1) {
            failNow(); // ✅ consume tablet + stop event immediately
            return;
        }

        // mark consumed so you can't farm the same mark
        markUsed[bestIdx] = true;

        // play success anim for watchers
        CombinerNet.s2cAnim(sw, pos, "hit_success");

        qteSuccesses++;
        if (qteSuccesses >= qteRequired) finishQte(true);
        else markDirtyAndSync();
    }

    /** ✅ Fail instantly: consume 1 tablet and STOP the event immediately. */
    private void failNow() {
        qteActive = false;

        // hard reset QTE state so it cannot keep ticking
        qteTicks = 0;
        qteSuccesses = 0;
        recipeId = null;

        cachedMarks = null;
        markUsed = null;
        lastHitWorldTick = -9999;

        // ✅ destroy ONE tablet immediately
        if (inv != null) {
            ItemStack trim = inv.getStack(1);
            if (!trim.isEmpty()) {
                trim.decrement(1);
                if (trim.isEmpty()) inv.setStack(1, ItemStack.EMPTY);
                else inv.setStack(1, trim);
            }
        }

        // sfx
        if (world != null) {
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }

        markDirtyAndSync();
    }

    private void finishQte(boolean success) {
        qteActive = false;
        cachedMarks = null;
        markUsed = null;

        if (world == null || world.isClient) { markDirtyAndSync(); return; }

        ItemStack gear = inv.getStack(0);
        ItemStack trim = inv.getStack(1);

        // copy the trim type BEFORE decrement so mapping always works
        ItemStack trimType = trim.isEmpty() ? ItemStack.EMPTY : trim.copyWithCount(1);

        // always consume 1 trim at end
        if (!trim.isEmpty()) {
            trim.decrement(1);
            inv.setStack(1, trim);
        }

        if (success && recipeId != null && !trimType.isEmpty()) {
            var recOpt = world.getRecipeManager().listAllOfType(ModCombinerRecipes.TYPE).stream()
                    .filter(r -> r.getId().toString().equals(recipeId))
                    .findFirst();

            recOpt.ifPresent(r -> r.apply(gear, trimType, world));
            inv.setStack(0, gear);

            world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        } else {
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }

        markDirtyAndSync();
    }

    /* ================= drops ================= */

    public void dropAllContents(ServerWorld sw) {
        drop(sw, inv.getStack(0));
        drop(sw, inv.getStack(1));
        inv.setStack(0, ItemStack.EMPTY);
        inv.setStack(1, ItemStack.EMPTY);
        markDirtyAndSync();
    }

    private void drop(ServerWorld sw, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemEntity e = new ItemEntity(sw, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, stack.copy());
        e.setToDefaultPickupDelay();
        sw.spawnEntity(e);
    }

    /* ================= helpers ================= */

    private static int[] randomizedMarks(int difficulty, int duration, int seed) {
        difficulty = Math.max(1, Math.min(7, difficulty));
        java.util.Random r = new java.util.Random(seed);

        int[] out = new int[difficulty];
        int edgePadding = Math.min(16, Math.max(8, duration / 5));
        int minGap = 8;
        int safeSpan = Math.max(1, duration - (edgePadding * 2));
        int i = 0, attempts = 0;

        while (i < difficulty && attempts++ < 500) {
            int m = edgePadding + r.nextInt(safeSpan);
            boolean ok = true;
            for (int j = 0; j < i; j++) {
                if (Math.abs(out[j] - m) < minGap) { ok = false; break; }
            }
            if (ok) out[i++] = m;
        }

        // fallback if RNG spacing couldn't fill (rare)
        while (i < difficulty) {
            out[i] = edgePadding + ((i + 1) * safeSpan / (difficulty + 1));
            i++;
        }

        java.util.Arrays.sort(out);
        return out;
    }

    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isClient) {
            ((ServerWorld) world).getChunkManager().markForUpdate(pos);
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /* ================= GUI factory ================= */

    @Override public Text getDisplayName() { return Text.literal("Combiner"); }

    @Override
    public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return new CombinerScreenHandler(syncId, playerInv, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    /* ================= NBT ================= */

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        nbt.putBoolean("qteActive", qteActive);
        nbt.putInt("qteTicks", qteTicks);
        nbt.putInt("qteDuration", qteDuration);
        nbt.putInt("qteSuccesses", qteSuccesses);
        nbt.putInt("qteRequired", qteRequired);
        nbt.putInt("qteDifficulty", qteDifficulty);
        nbt.putInt("qteSeed", qteSeed);

        nbt.putBoolean("menuOpen", menuOpen);
        nbt.putInt("emergeTicks", emergeTicks);

        DefaultedList<ItemStack> list = DefaultedList.ofSize(inv.size(), ItemStack.EMPTY);
        for (int i = 0; i < inv.size(); i++) list.set(i, inv.getStack(i).copy());
        Inventories.writeNbt(nbt, list);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        qteActive = nbt.getBoolean("qteActive");
        qteTicks = nbt.getInt("qteTicks");
        qteDuration = nbt.getInt("qteDuration");
        qteSuccesses = nbt.getInt("qteSuccesses");
        qteRequired = nbt.getInt("qteRequired");
        qteDifficulty = nbt.getInt("qteDifficulty");
        qteSeed = nbt.getInt("qteSeed");

        menuOpen = nbt.getBoolean("menuOpen");
        emergeTicks = nbt.getInt("emergeTicks");

        cachedMarks = null;
        markUsed = null;

        DefaultedList<ItemStack> list = DefaultedList.ofSize(inv.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, list);
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, list.get(i));
    }

    @Override public NbtCompound toInitialChunkDataNbt() { NbtCompound n = new NbtCompound(); writeNbt(n); return n; }
    @Override public net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    /* ================= GeckoLib ================= */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrs) {
        AnimationController<CombinerBlockEntity> ctrl =
                new AnimationController<>(this, "main", 0, state -> {

                    boolean open = this.menuOpen;

                    if (state.getAnimatable().world != null && state.getAnimatable().world.isClient) {
                        if (open && !clientPrevOpen) {
                            clientClosing = false;
                            clientPrevOpen = true;
                            return state.setAndContinue(CRAFT_IN_HOLD);
                        }

                        if (!open && clientPrevOpen) {
                            clientClosing = true;
                            clientPrevOpen = false;
                            return state.setAndContinue(CRAFT_OUT);
                        }

                        if (clientClosing) {
                            if (state.getController().hasAnimationFinished()) {
                                clientClosing = false;
                                return state.setAndContinue(IDLE);
                            }
                            return PlayState.CONTINUE;
                        }

                        if (open) return state.setAndContinue(CRAFT_IN_HOLD);
                        return state.setAndContinue(IDLE);
                    }

                    if (open) return state.setAndContinue(CRAFT_IN_HOLD);
                    return state.setAndContinue(IDLE);
                });

        ctrl.triggerableAnim("hit_success", HIT_SUCCESS);
        ctrs.add(ctrl);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}