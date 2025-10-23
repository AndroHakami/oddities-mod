package net.seep.odd.block.grandanvil;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.grandanvil.recipe.ModGrandAnvilRecipes;

public class GrandAnvilBlockEntity extends BlockEntity
        implements NamedScreenHandlerFactory, ExtendedScreenHandlerFactory {

    // ----- property indices (unchanged) -----
    public static final int P_ACTIVE   = 0;
    public static final int P_DURATION = 1;
    public static final int P_PROGRESS = 2;
    public static final int P_SUCC     = 3;
    public static final int P_REQ      = 4;
    public static final int P_DIFF     = 5;
    public static final int P_SEED     = 6;

    // Server judge base window
    private static final int HIT_TOLERANCE_TICKS = 6;

    // QTE state
    private boolean qteActive = false;
    private int qteTicks = 0;
    private int qteDuration = 80;
    private int qteSuccesses = 0;
    private int qteRequired = 1;
    private int qteDifficulty = 1;
    private int qteSeed = 0;
    private String recipeId = "";

    // NEW: cache marks per run (server uses this; client can still derive from seed if it wants)
    private int[] cachedMarks = null;

    // Inventory
    private final SimpleInventory inv = new SimpleInventory(2) {
        @Override public void markDirty() {
            super.markDirty();
            GrandAnvilBlockEntity.this.markDirty();
            GrandAnvilBlockEntity.this.sync();
        }
    };
    public Inventory inventory() { return inv; }

    // Properties
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
        @Override public void set(int i, int v) {
            switch (i) {
                case P_ACTIVE   -> qteActive = (v != 0);
                case P_DURATION -> qteDuration = v;
                case P_PROGRESS -> qteTicks = v;
                case P_SUCC     -> qteSuccesses = v;
                case P_REQ      -> qteRequired = v;
                case P_DIFF     -> qteDifficulty = v;
                case P_SEED     -> qteSeed = v;
            }
        }
        @Override public int size() { return 7; }
    };
    public PropertyDelegate getProps() { return props; }

    public GrandAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.GRAND_ANVIL_BE, pos, state);
    }

    /* ---------------- server tick ---------------- */
    public static void tick(World world, BlockPos pos, BlockState state, GrandAnvilBlockEntity be) {
        if (!world.isClient) be.tickServer();
    }
    private void tickServer() {
        if (!qteActive) return;
        qteTicks++;
        if (qteTicks >= qteDuration) {
            finishQte(false);
        } else {
            sync();
        }
    }

    /* ---------------- start / hit ---------------- */
    public void startQte() {
        if (world == null || world.isClient) return;

        var gear = inv.getStack(0);
        var mat  = inv.getStack(1);
        var rm   = world.getRecipeManager();

        var opt = rm.listAllOfType(ModGrandAnvilRecipes.TYPE).stream()
                .filter(r -> r.matches(gear, mat))
                .findFirst();
        if (opt.isEmpty()) return;

        var rec = opt.get();
        this.recipeId      = rec.getId().toString();
        this.qteDifficulty = rec.difficulty();
        this.qteSeed       = world.getRandom().nextInt();

        this.qteActive     = true;
        this.qteTicks      = 0;
        this.qteDuration   = 80;
        this.qteSuccesses  = 0;
        this.qteRequired   = qteDifficulty;

        // NEW: cache marks once per run
        this.cachedMarks   = randomizedMarks(qteDifficulty, qteDuration, qteSeed);

        markDirty(); sync();
    }

    /** OLD entry kept for compatibility (no lag compensation info). */
    public void hit() {
        // If you still call this somewhere, we’ll judge at current tick with base tolerance.
        hitWithLagComp(null, -1);
    }

    /** Call this from your C2S packet, passing the hitting player. */
    /** Judge a hit from the network with simple RTT/2 compensation + tolerance widening. */
    public void hitFromNet(ServerPlayerEntity player) {
        if (!qteActive) return;

        // Approximate one-way latency in ticks (RTT/2). Cap to avoid huge shifts.
        int ms   = getLatencyMs(player);
        int half = clamp((ms + 25) / 50 / 2 * 2, 0, 6); // round-ish & cap ~300ms -> 6 ticks

        // What the client likely saw when they pressed the key
        int clientViewTick = clamp(qteTicks - half, 0, qteDuration);

        int[] marks = randomizedMarks(qteDifficulty, qteDuration, qteSeed);

        // Widen tolerance slightly as latency grows (but keep it sane)
        int tol = clamp(HIT_TOLERANCE_TICKS + Math.max(0, half - 1), HIT_TOLERANCE_TICKS, HIT_TOLERANCE_TICKS + 4);

        boolean success = false;
        for (int m : marks) {
            if (Math.abs(clientViewTick - m) <= tol) { success = true; break; }
        }

        if (!success) {
            breakMaterialAndStop();
            return;
        }

        qteSuccesses++;
        if (qteSuccesses >= qteRequired) finishQte(true);
        else sync();
    }


    /**
     * NEW: lag-compensated judge.
     * @param player player who hit (may be null; we’ll fall back gracefully)
     * @param clientRelativeTick optional, if you also send the client’s local QTE tick; pass -1 if not available
     */
    public void hitWithLagComp(ServerPlayerEntity player, int clientRelativeTick) {
        if (!qteActive) return;

        // Use cached marks (guaranteed non-null while active)
        int[] marks = (cachedMarks != null) ? cachedMarks
                : (cachedMarks = randomizedMarks(qteDifficulty, qteDuration, qteSeed));

        // --- estimate when the player actually pressed ---
        int estimatedTick;

        if (clientRelativeTick >= 0) {
            // Best case: client already computed "ticks since QTE start" when they pressed.
            estimatedTick = clientRelativeTick;
        } else if (player != null) {
            // Good case: subtract half the RTT (ping/2) from server arrival tick.
            int latencyMs = getLatencyMs(player);
            int halfRttTicks = Math.max(0, Math.round(latencyMs / 100f)); // (latency/2) / 50ms = ms/100
            estimatedTick = clamp(qteTicks - halfRttTicks, 0, qteDuration);
        } else {
            // Fallback: small constant grace
            estimatedTick = clamp(qteTicks - 2, 0, qteDuration);
        }

        // Dynamic tolerance: base ±3 plus a tiny lag fudge (max +4)
        int tol = HIT_TOLERANCE_TICKS;
        if (player != null) {
            int ms = getLatencyMs(player);
            tol += Math.min(4, (int)Math.ceil(ms / 150.0)); // every ~150ms adds 1 tick, capped
        }

        boolean success = false;
        for (int m : marks) {
            if (Math.abs(estimatedTick - m) <= tol) { success = true; break; }
        }

        if (!success) {
            breakMaterialAndStop();
            return;
        }

        qteSuccesses++;
        if (qteSuccesses >= qteRequired) {
            finishQte(true);
        } else {
            sync();
        }
    }

    /* ---------------- helpers ---------------- */
    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }


    /** Yarn 1.20.1 exposes latency on the network handler; fall back to a safe default if mappings differ. */
    private static int getLatencyMs(ServerPlayerEntity p) {
        // 1) Try handler#getLatency()
        try {
            var h = p.networkHandler;
            try {
                var m = h.getClass().getMethod("getLatency");
                Object v = m.invoke(h);
                if (v instanceof Integer i) return i;
            } catch (NoSuchMethodException ignored) {
                // 2) Try common handler fields: "latency", "pingMilliseconds"
                for (String name : new String[]{"latency", "pingMilliseconds"}) {
                    try {
                        var f = h.getClass().getDeclaredField(name);
                        f.setAccessible(true);
                        Object v = f.get(h);
                        if (v instanceof Integer i) return i;
                    } catch (Throwable ignored2) {}
                }
            }
        } catch (Throwable ignored) {}

        // 3) Fallback: some mappings put it on the player
        for (String name : new String[]{"latency", "pingMilliseconds"}) {
            try {
                var f = ServerPlayerEntity.class.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(p);
                if (v instanceof Integer i) return i;
            } catch (Throwable ignored) {}
        }

        // 4) Last resort default
        return 100; // ms
    }


    /** Consumes one material, plays break sound, and stops the QTE. */
    private void breakMaterialAndStop() {
        qteActive = false;
        cachedMarks = null;
        if (world != null && !world.isClient) {
            var mat = inv.getStack(1);
            if (!mat.isEmpty()) { mat.decrement(1); inv.setStack(1, mat); }
            world.playSound(null, this.pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        markDirty(); sync();
    }

    private void finishQte(boolean success) {
        qteActive = false;
        cachedMarks = null;
        if (world != null && !world.isClient) {
            if (success) {
                var mat = inv.getStack(1);
                if (!mat.isEmpty()) { mat.decrement(1); inv.setStack(1, mat); }

                var recOpt = world.getRecipeManager().listAllOfType(ModGrandAnvilRecipes.TYPE).stream()
                        .filter(r -> r.getId().toString().equals(this.recipeId))
                        .findFirst();

                var gear = inv.getStack(0);
                recOpt.ifPresent(r -> r.apply(gear, world));
                inv.setStack(0, gear);

                world.playSound(null, this.pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            } else {
                var mat = inv.getStack(1);
                if (!mat.isEmpty()) { mat.decrement(1); inv.setStack(1, mat); }
                world.playSound(null, this.pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
        }
        markDirty(); sync();
    }

    private static int[] randomizedMarks(int difficulty, int duration, int seed) {
        difficulty = Math.max(1, Math.min(7, difficulty));
        java.util.Random r = new java.util.Random(seed);
        int[] out = new int[difficulty];
        int minGap = 6;
        int i = 0, attempts = 0;
        while (i < difficulty && attempts++ < 300) {
            int m = 8 + r.nextInt(Math.max(1, duration - 16));
            boolean ok = true;
            for (int j = 0; j < i; j++) if (Math.abs(out[j] - m) < minGap) { ok = false; break; }
            if (ok) out[i++] = m;
        }
        java.util.Arrays.sort(out);
        return out;
    }

    private void sync() {
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    /* ---------------- NBT ---------------- */
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("qteActive", qteActive);
        nbt.putInt("qteTicks", qteTicks);
        nbt.putInt("qteDuration", qteDuration);
        nbt.putInt("qteSuccesses", qteSuccesses);
        nbt.putInt("qteRequired", qteRequired);
        nbt.putInt("qteDifficulty", qteDifficulty);
        nbt.putInt("qteSeed", qteSeed);
        nbt.putString("recipeId", recipeId);

        DefaultedList<ItemStack> list = DefaultedList.ofSize(inv.size(), ItemStack.EMPTY);
        for (int i = 0; i < inv.size(); i++) list.set(i, inv.getStack(i).copy());
        Inventories.writeNbt(nbt, list);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        qteActive    = nbt.getBoolean("qteActive");
        qteTicks     = nbt.getInt("qteTicks");
        qteDuration  = nbt.getInt("qteDuration");
        qteSuccesses = nbt.getInt("qteSuccesses");
        qteRequired  = nbt.getInt("qteRequired");
        qteDifficulty= nbt.getInt("qteDifficulty");
        qteSeed      = nbt.getInt("qteSeed");
        recipeId     = nbt.getString("recipeId");
        cachedMarks  = null; // force regen on next start

        DefaultedList<ItemStack> list = DefaultedList.ofSize(inv.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, list);
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, list.get(i));
    }

    /* ---------------- screen factory ---------------- */
    @Override public Text getDisplayName() {
        return Text.translatable("container.odd.grand_anvil");
    }

    @Override
    public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return new GrandAnvilScreenHandler(syncId, playerInv, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }
}
