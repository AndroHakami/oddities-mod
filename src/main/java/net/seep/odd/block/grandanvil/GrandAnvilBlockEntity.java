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
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.grandanvil.recipe.ModGrandAnvilRecipes;

public class GrandAnvilBlockEntity extends BlockEntity
        implements NamedScreenHandlerFactory, ExtendedScreenHandlerFactory {

    // Properties exposed to the ScreenHandler (order matters)
    public static final int P_ACTIVE   = 0; // 1/0
    public static final int P_DURATION = 1; // total ticks
    public static final int P_PROGRESS = 2; // current tick
    public static final int P_SUCC     = 3; // landed hits
    public static final int P_REQ      = 4; // required hits
    public static final int P_DIFF     = 5; // difficulty 1..7
    public static final int P_SEED     = 6; // RNG seed for windows

    // BE state
    private boolean qteActive = false;
    private int qteTicks = 0;
    private int qteDuration = 80;
    private int qteSuccesses = 0;
    private int qteRequired = 1;
    private int qteDifficulty = 1;
    private int qteSeed = 0;
    private String recipeId = ""; // the chosen recipe id for this run

    // 2-slot inventory: 0=gear, 1=material
    private final SimpleInventory inv = new SimpleInventory(2) {
        @Override public void markDirty() {
            super.markDirty();
            GrandAnvilBlockEntity.this.markDirty();
            GrandAnvilBlockEntity.this.sync();
        }
    };
    public Inventory inventory() { return inv; }

    // Properties for client sync
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

    // ===== server tick =====
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

    // ===== QTE control (called by GrandAnvilNet) =====
    public void startQte() {
        if (world == null || world.isClient) return;
        var gear = inv.getStack(0);
        var mat  = inv.getStack(1);
        var rm = world.getRecipeManager();

        var opt = rm.listAllOfType(ModGrandAnvilRecipes.TYPE).stream()
                .filter(r -> r.matches(gear, mat))
                .findFirst();
        if (opt.isEmpty()) return;

        var rec = opt.get();
        this.recipeId = rec.getId().toString();
        this.qteDifficulty = rec.difficulty();
        this.qteSeed = world.getRandom().nextInt();

        qteActive = true;
        qteTicks = 0;
        qteDuration = 80;             // tweak as needed
        qteSuccesses = 0;
        qteRequired = qteDifficulty;  // #hits required equals difficulty
        markDirty(); sync();
    }

    public void hit() {
        if (!qteActive) return;
        // Hit succeeds if we're within ±3 ticks of any of the randomized marks.
        // (Server will regenerate the same marks using seed+difficulty.)
        int[] marks = randomizedMarks(qteDifficulty, qteDuration, qteSeed);
        for (int m : marks) {
            if (Math.abs(qteTicks - m) <= 3) { qteSuccesses++; break; }
        }
        if (qteSuccesses >= qteRequired) finishQte(true);
        else sync();
    }

    private void finishQte(boolean success) {
        qteActive = false;
        if (world != null && !world.isClient) {
            if (success) {
                // consume one material
                var cat = inv.getStack(1);
                if (!cat.isEmpty()) { cat.decrement(1); inv.setStack(1, cat); }

                // apply recipe result by id
                var recOpt = world.getRecipeManager().listAllOfType(ModGrandAnvilRecipes.TYPE).stream()
                        .filter(r -> r.getId().toString().equals(this.recipeId))
                        .findFirst();

                var gear = inv.getStack(0);
                recOpt.ifPresent(r -> r.apply(gear, world));
                inv.setStack(0, gear);
            }
        }
        markDirty(); sync();
    }

    private static int[] randomizedMarks(int difficulty, int duration, int seed) {
        difficulty = Math.max(1, Math.min(7, difficulty));
        java.util.Random r = new java.util.Random(seed);
        int[] out = new int[difficulty];
        int minGap = 6; // avoid clustered marks
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

    private boolean canStart() { return !inv.getStack(0).isEmpty() && !inv.getStack(1).isEmpty(); }

    private void sync() {
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    // ===== NBT =====
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

        DefaultedList<ItemStack> list = DefaultedList.ofSize(inv.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, list);
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, list.get(i));
    }

    // ===== screen factory =====
    @Override public Text getDisplayName() {
        return Text.translatable("container.odd.grand_anvil");
    }

    @Override
    public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return new GrandAnvilScreenHandler(syncId, playerInv, this); // server ctor
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }
}
