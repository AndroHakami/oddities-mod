package net.seep.odd.block.rps_machine;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.rps_machine.game.RpsEnemyType;
import net.seep.odd.block.rps_machine.game.RpsMove;
import net.seep.odd.block.rps_machine.game.RpsPhase;
import net.seep.odd.block.rps_machine.game.RpsRoundResult;
import net.seep.odd.block.rps_machine.screen.RpsMachineScreenHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RpsMachineBlockEntity extends BlockEntity implements GeoBlockEntity, ExtendedScreenHandlerFactory {
    public static final int PLAYER_MAX_HP = 20;
    public static final int PROPERTY_COUNT = 10;

    private static final RawAnimation IDLE_OFF = RawAnimation.begin().thenLoop("idle_off");
    private static final RawAnimation IDLE_ON  = RawAnimation.begin().thenLoop("idle_on");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private boolean active = false;

    private RpsPhase phase = RpsPhase.IDLE;
    private int playerHp = PLAYER_MAX_HP;
    private int enemyHp = 0;
    private int enemyMaxHp = 0;
    private int roundNumber = 1;

    private RpsMove lastPlayerMove = null;
    private RpsMove lastEnemyMove = null;
    private RpsRoundResult lastRoundResult = null;
    private RpsEnemyType enemyType = RpsEnemyType.TRAINING_BOT;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> phase.ordinal();
                case 1 -> playerHp;
                case 2 -> PLAYER_MAX_HP;
                case 3 -> enemyHp;
                case 4 -> enemyMaxHp;
                case 5 -> roundNumber;
                case 6 -> lastPlayerMove == null ? -1 : lastPlayerMove.ordinal();
                case 7 -> lastEnemyMove == null ? -1 : lastEnemyMove.ordinal();
                case 8 -> lastRoundResult == null ? -1 : lastRoundResult.ordinal();
                case 9 -> enemyType.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> phase = RpsPhase.byId(value);
                case 1 -> playerHp = value;
                case 3 -> enemyHp = value;
                case 4 -> enemyMaxHp = value;
                case 5 -> roundNumber = value;
                case 6 -> lastPlayerMove = RpsMove.byId(value);
                case 7 -> lastEnemyMove = RpsMove.byId(value);
                case 8 -> lastRoundResult = RpsRoundResult.byId(value);
                case 9 -> enemyType = RpsEnemyType.byId(value);
                default -> {}
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public RpsMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RPS_MACHINE_BE, pos, state);
        if (state.contains(RpsMachineBlock.ON)) {
            this.active = state.get(RpsMachineBlock.ON);
        }
    }

    public PropertyDelegate getPropertyDelegate() {
        return properties;
    }

    public void startNewRun() {
        this.enemyType = this.world != null ? RpsEnemyType.random(this.world.getRandom()) : RpsEnemyType.TRAINING_BOT;
        this.playerHp = PLAYER_MAX_HP;
        this.enemyMaxHp = enemyType.getMaxHp();
        this.enemyHp = this.enemyMaxHp;
        this.roundNumber = 1;
        this.lastPlayerMove = null;
        this.lastEnemyMove = null;
        this.lastRoundResult = null;
        this.phase = RpsPhase.PLAYER_CHOOSE;
        setActive(true);
        sync();
    }

    public void stopRun() {
        this.phase = RpsPhase.IDLE;
        this.playerHp = PLAYER_MAX_HP;
        this.enemyHp = 0;
        this.enemyMaxHp = 0;
        this.roundNumber = 1;
        this.lastPlayerMove = null;
        this.lastEnemyMove = null;
        this.lastRoundResult = null;
        setActive(false);
        sync();
    }

    public void playRound(PlayerEntity player, RpsMove playerMove) {
        if (this.world == null || this.world.isClient) return;
        if (!this.active) return;
        if (this.phase != RpsPhase.PLAYER_CHOOSE) return;
        if (playerMove == null) return;

        RpsMove enemyMove = enemyType.pickMove(this.world.getRandom());
        int versus = playerMove.versus(enemyMove);

        this.lastPlayerMove = playerMove;
        this.lastEnemyMove = enemyMove;

        if (versus > 0) {
            this.lastRoundResult = RpsRoundResult.WIN;
            this.enemyHp = Math.max(0, this.enemyHp - 4);
        } else if (versus < 0) {
            this.lastRoundResult = RpsRoundResult.LOSE;
            this.playerHp = Math.max(0, this.playerHp - enemyType.getDamageOnWin());
        } else {
            this.lastRoundResult = RpsRoundResult.DRAW;
        }

        if (this.enemyHp <= 0) {
            this.phase = RpsPhase.VICTORY;
        } else if (this.playerHp <= 0) {
            this.phase = RpsPhase.DEFEAT;
        } else {
            this.roundNumber++;
            this.phase = RpsPhase.PLAYER_CHOOSE;
        }

        sync();
    }

    public void setActive(boolean active) {
        this.active = active;

        if (this.world != null) {
            BlockState oldState = this.getCachedState();
            if (oldState.contains(RpsMachineBlock.ON)) {
                BlockState newState = oldState.with(RpsMachineBlock.ON, active);
                this.world.setBlockState(this.pos, newState, Block.NOTIFY_ALL);
                this.world.updateListeners(this.pos, oldState, newState, Block.NOTIFY_ALL);
            }
            this.markDirty();
        }
    }

    public boolean isActive() {
        if (this.world != null && this.getCachedState().contains(RpsMachineBlock.ON)) {
            return this.getCachedState().get(RpsMachineBlock.ON);
        }
        return this.active;
    }

    private void sync() {
        this.markDirty();
        if (this.world != null) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            state.setAndContinue(isActive() ? IDLE_ON : IDLE_OFF);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("Active", this.active);
        nbt.putInt("Phase", this.phase.ordinal());
        nbt.putInt("PlayerHp", this.playerHp);
        nbt.putInt("EnemyHp", this.enemyHp);
        nbt.putInt("EnemyMaxHp", this.enemyMaxHp);
        nbt.putInt("RoundNumber", this.roundNumber);
        nbt.putInt("EnemyType", this.enemyType.ordinal());
        nbt.putInt("LastPlayerMove", this.lastPlayerMove == null ? -1 : this.lastPlayerMove.ordinal());
        nbt.putInt("LastEnemyMove", this.lastEnemyMove == null ? -1 : this.lastEnemyMove.ordinal());
        nbt.putInt("LastRoundResult", this.lastRoundResult == null ? -1 : this.lastRoundResult.ordinal());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.active = nbt.getBoolean("Active");
        this.phase = RpsPhase.byId(nbt.getInt("Phase"));
        this.playerHp = nbt.getInt("PlayerHp");
        this.enemyHp = nbt.getInt("EnemyHp");
        this.enemyMaxHp = nbt.getInt("EnemyMaxHp");
        this.roundNumber = nbt.getInt("RoundNumber");
        this.enemyType = RpsEnemyType.byId(nbt.getInt("EnemyType"));
        this.lastPlayerMove = RpsMove.byId(nbt.getInt("LastPlayerMove"));
        this.lastEnemyMove = RpsMove.byId(nbt.getInt("LastEnemyMove"));
        this.lastRoundResult = RpsRoundResult.byId(nbt.getInt("LastRoundResult"));
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return this.createNbt();
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("RPS Machine");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RpsMachineScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }
}