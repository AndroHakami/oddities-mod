package net.seep.odd.device.store.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.math.BlockPos;
import net.seep.odd.device.store.DabloonStoreSnapshot;


public final class DabloonStoreClientState {
    private DabloonStoreClientState() {}

    public static final class BlockStateView {
        public final BlockPos pos;
        public final boolean owner;
        public final DabloonStoreSnapshot snapshot;

        public BlockStateView(BlockPos pos, boolean owner, DabloonStoreSnapshot snapshot) {
            this.pos = pos;
            this.owner = owner;
            this.snapshot = snapshot;
        }
    }

    private static final Map<Long, BlockStateView> BLOCKS = new HashMap<>();
    private static List<DabloonStoreSnapshot> discoverable = new ArrayList<>();
    private static List<DabloonStoreSnapshot> owned = new ArrayList<>();

    public static void putBlockState(BlockStateView view) {
        BLOCKS.put(view.pos.asLong(), view);
    }

    public static BlockStateView getBlockState(BlockPos pos) {
        return BLOCKS.get(pos.asLong());
    }

    public static void setAppData(List<DabloonStoreSnapshot> discoverableStores, List<DabloonStoreSnapshot> ownedStores) {
        discoverable = new ArrayList<>(discoverableStores);
        owned = new ArrayList<>(ownedStores);
    }

    public static List<DabloonStoreSnapshot> discoverableStores() {
        return new ArrayList<>(discoverable);
    }

    public static List<DabloonStoreSnapshot> ownedStores() {
        return new ArrayList<>(owned);
    }
}
