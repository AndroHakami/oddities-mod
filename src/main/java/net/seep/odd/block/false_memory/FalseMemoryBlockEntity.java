package net.seep.odd.block.false_memory;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.block.ModBlocks;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.bosswitch.BossWitchEntity;

import net.seep.odd.sound.ModSounds;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class FalseMemoryBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final int TRANSITION_TICKS = 10; // 0.5s @ 20 TPS

    private enum VisualState {
        IDLE,
        TRANSITION_TO_INSERTED,
        INSERTED,
        TRANSITION_TO_IDLE
    }

    private VisualState visualState = VisualState.IDLE;
    private int transitionTicksRemaining = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation TRANSITION_TO_INSERTED = RawAnimation.begin().thenPlay("transition_to_inserted");
    private static final RawAnimation INSERTED = RawAnimation.begin().thenLoop("inserted");
    private static final RawAnimation TRANSITION_TO_IDLE = RawAnimation.begin().thenPlay("transition_to_idle");

    public FalseMemoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FALSE_MEMORY_BE, pos, state);
    }

    public boolean hasMaskInserted() {
        return this.visualState != VisualState.IDLE;
    }

    public boolean isSummoning() {
        return this.visualState == VisualState.TRANSITION_TO_INSERTED
                || this.visualState == VisualState.INSERTED
                || this.visualState == VisualState.TRANSITION_TO_IDLE;
    }

    public void serverInsertMask(ServerPlayerEntity sp, ItemStack stack) {
        if (!(this.world instanceof ServerWorld sw)) return;
        if (this.visualState != VisualState.IDLE) return;
        if (isBossWitchAlive(sw)) return;

        BossWitchEntity witch = ModEntities.BOSS_WITCH.create(sw);
        if (witch == null) return;

        if (!sp.isCreative()) {
            stack.decrement(1);
        }

        double x = this.pos.getX() + 0.5D;
        double y = this.pos.getY() + 2.20D;
        double z = this.pos.getZ() + 0.5D;

        witch.refreshPositionAndAngles(x, y, z, sp.getYaw() + 180.0f, 0.0f);
        witch.beginFalseMemorySpawn(this.pos);
        sw.spawnEntity(witch);

        setVisualState(VisualState.TRANSITION_TO_INSERTED, TRANSITION_TICKS);

        sw.playSound(null, this.pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.75f);
        sw.playSound(null, this.pos, SoundEvents.ENTITY_ENDERMAN_AMBIENT, SoundCategory.BLOCKS, 0.8f, 0.65f);
    }

    public static void tickServer(ServerWorld sw, BlockPos pos, FalseMemoryBlockEntity be) {
        boolean bossAlive = isBossWitchAlive(sw);

        switch (be.visualState) {
            case IDLE -> {
                if (bossAlive) {
                    be.setVisualState(VisualState.INSERTED, 0);
                }
            }

            case TRANSITION_TO_INSERTED -> {
                if (be.transitionTicksRemaining > 0) {
                    be.transitionTicksRemaining--;
                }

                if (be.transitionTicksRemaining <= 0) {
                    if (bossAlive) {
                        be.setVisualState(VisualState.INSERTED, 0);
                    } else {
                        be.setVisualState(VisualState.TRANSITION_TO_IDLE, TRANSITION_TICKS);
                    }
                }
            }

            case INSERTED -> {
                if (!bossAlive) {
                    be.setVisualState(VisualState.TRANSITION_TO_IDLE, TRANSITION_TICKS);
                    sw.playSound(null, pos, ModSounds.MASK_INSERT, SoundCategory.BLOCKS, 1.0f, 1.0f);
                }
            }

            case TRANSITION_TO_IDLE -> {
                if (bossAlive) {
                    be.setVisualState(VisualState.INSERTED, 0);
                    return;
                }

                if (be.transitionTicksRemaining > 0) {
                    be.transitionTicksRemaining--;
                }

                if (be.transitionTicksRemaining <= 0) {
                    be.setVisualState(VisualState.IDLE, 0);
                }
            }
        }
    }

    public static boolean isBossWitchAlive(ServerWorld sw) {
        for (Entity entity : sw.iterateEntities()) {
            if (entity instanceof BossWitchEntity witch && witch.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private void setVisualState(VisualState newState, int transitionTicks) {
        if (this.visualState == newState && this.transitionTicksRemaining == transitionTicks) {
            return;
        }

        this.visualState = newState;
        this.transitionTicksRemaining = transitionTicks;
        markDirty();
        sync();
    }

    private void sync() {
        if (this.world instanceof ServerWorld sw) {
            sw.getChunkManager().markForUpdate(this.pos);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            switch (this.visualState) {
                case TRANSITION_TO_INSERTED -> state.setAndContinue(TRANSITION_TO_INSERTED);
                case INSERTED -> state.setAndContinue(INSERTED);
                case TRANSITION_TO_IDLE -> state.setAndContinue(TRANSITION_TO_IDLE);
                default -> state.setAndContinue(IDLE);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("VisualState", this.visualState.ordinal());
        nbt.putInt("TransitionTicksRemaining", this.transitionTicksRemaining);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        int stateIndex = nbt.getInt("VisualState");
        if (stateIndex < 0 || stateIndex >= VisualState.values().length) {
            stateIndex = 0;
        }

        this.visualState = VisualState.values()[stateIndex];
        this.transitionTicksRemaining = nbt.getInt("TransitionTicksRemaining");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}