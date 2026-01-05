package net.seep.odd.block.falseflower;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
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
import net.minecraft.world.World;
import net.seep.odd.abilities.fairy.FairySpell;
import org.jetbrains.annotations.Nullable;

public class FalseFlowerBlock extends BlockWithEntity {
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    public static final IntProperty POWER = IntProperty.of("power", 1, 3);
    public static final EnumProperty<Skin> SKIN = EnumProperty.of("skin", Skin.class);

    public enum Skin implements StringIdentifiable {
        NONE, LEVITATE, HEAVY, MINE, STONE, BUBBLE, REGEN, GROWTH, BLACKHOLE, RECHARGE;
        @Override public String asString() { return name().toLowerCase(); }
        public static Skin fromSpell(FairySpell s) {
            return switch (s) {
                case AURA_LEVITATION -> LEVITATE;
                case AURA_HEAVY      -> HEAVY;
                case AREA_MINE       -> MINE;
                case STONE_PRISON    -> STONE;
                case BUBBLE          -> BUBBLE;
                case AURA_REGEN      -> REGEN;
                case CROP_GROWTH     -> GROWTH;
                case BLACKHOLE       -> BLACKHOLE;
                case RECHARGE        -> RECHARGE;
            };
        }
    }

    public FalseFlowerBlock(Settings s) {
        super(s.nonOpaque().strength(0.4f).sounds(BlockSoundGroup.AZALEA));
        setDefaultState(getStateManager().getDefaultState().with(ACTIVE, false).with(POWER, 1).with(SKIN, Skin.NONE));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, POWER, SKIN);
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override public boolean hasDynamicBounds() { return true; }

    @Nullable @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FalseFlowerBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof FalseFlowerBlockEntity be) {
            boolean newActive = !state.get(ACTIVE);
            world.setBlockState(pos, state.with(ACTIVE, newActive), Block.NOTIFY_LISTENERS);
            be.setActive(newActive);
            return ActionResult.SUCCESS;
        }
        return ActionResult.SUCCESS;
    }

    @Override public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState();
    }

}
