// src/main/java/net/seep/odd/block/falseflower/FalseFlowerBlock.java
package net.seep.odd.block.falseflower;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.ShapeContext;

public class FalseFlowerBlock extends BlockWithEntity {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    public static final IntProperty POWER = IntProperty.of("power", 1, 3);
    public static final EnumProperty<Skin> SKIN = EnumProperty.of("skin", Skin.class);

    private static final VoxelShape OUTLINE = Block.createCuboidShape(5, 0, 5, 11, 11, 11);

    public enum Skin implements StringIdentifiable {
        NONE,
        LEVITATE, HEAVY, MINE, STONE, BUBBLE, REGEN, GROWTH, BLACKHOLE, RECHARGE,

        STORM, BULLETS, WATER, TINY, LIFTOFF, BANISH, EXTINGUISH, RETURN, SWITCHERANO, WEAKNESS;

        @Override public String asString() { return name().toLowerCase(); }

        public static Skin fromSpell(net.seep.odd.abilities.fairy.FairySpell s) {
            if (s == null) return NONE;
            return switch (s) {
                case NONE -> NONE;
                case AURA_LEVITATION -> LEVITATE;
                case AURA_HEAVY -> HEAVY;
                case AREA_MINE -> MINE;
                case STONE_PRISON -> STONE;
                case BUBBLE -> BUBBLE;
                case AURA_REGEN -> REGEN;
                case CROP_GROWTH -> GROWTH;
                case BLACKHOLE -> BLACKHOLE;
                case RECHARGE -> RECHARGE;

                case STORM -> STORM;
                case MAGIC_BULLETS -> BULLETS;
                case WATER_BREATHING -> WATER;
                case TINY_WORLD -> TINY;
                case LIFT_OFF -> LIFTOFF;
                case BANISH -> BANISH;
                case EXTINGUISH -> EXTINGUISH;
                case RETURN_POINT -> RETURN;
                case SWITCHERANO -> SWITCHERANO;
                case WEAKNESS -> WEAKNESS;
            };
        }
    }

    public FalseFlowerBlock(Settings s) {
        super(s.nonOpaque().strength(0.4f).sounds(BlockSoundGroup.AZALEA));
        setDefaultState(getStateManager().getDefaultState()
                .with(ACTIVE, false)
                .with(POWER, 1)
                .with(SKIN, Skin.NONE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, POWER, SKIN);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean hasDynamicBounds() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FalseFlowerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, net.seep.odd.block.ModBlocks.FALSE_FLOWER_BE, FalseFlowerBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof FalseFlowerBlockEntity be) {
            be.setActive(!be.isActive());
            return ActionResult.SUCCESS;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState();
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }
}
