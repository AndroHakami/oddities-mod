package net.seep.odd.expeditions.atheneum.granny;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public final class GrannyPersistentState extends PersistentState {
    private static final String ID = "odd_granny_state";

    private BlockPos exitPos = BlockPos.ORIGIN;

    public static GrannyPersistentState createFromNbt(NbtCompound nbt) {
        GrannyPersistentState state = new GrannyPersistentState();
        state.exitPos = new BlockPos(
                nbt.getInt("ExitX"),
                nbt.getInt("ExitY"),
                nbt.getInt("ExitZ")
        );
        return state;
    }

    public static GrannyPersistentState get(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(GrannyPersistentState::createFromNbt, GrannyPersistentState::new, ID);
    }

    public BlockPos getExitPos() {
        return exitPos;
    }

    public void setExitPos(BlockPos exitPos) {
        this.exitPos = exitPos.toImmutable();
        this.markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt("ExitX", this.exitPos.getX());
        nbt.putInt("ExitY", this.exitPos.getY());
        nbt.putInt("ExitZ", this.exitPos.getZ());
        return nbt;
    }
}