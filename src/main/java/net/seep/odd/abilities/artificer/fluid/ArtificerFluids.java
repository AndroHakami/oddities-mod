package net.seep.odd.abilities.artificer.fluid;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

import net.seep.odd.abilities.artificer.EssenceType;

public final class ArtificerFluids {
    private ArtificerFluids() {}

    private static Identifier id(String path) { return new Identifier("odd", path); }

    /* ====================================================================== */
    /*  Base fluid impl (per-essence)                                         */
    /* ====================================================================== */
    public static abstract class EssenceFluid extends FlowableFluid {
        public final EssenceType type;
        protected EssenceFluid(EssenceType type) { this.type = type; }

        @Override public boolean isStill(FluidState state) { return this instanceof Still; }
        @Override public int getLevel(FluidState state)    { return this instanceof Still ? 8 : state.get(Properties.LEVEL_1_8); }

        // lava-like / thick
        @Override protected int  getFlowSpeed(WorldView world)           { return 2; }
        @Override protected int  getLevelDecreasePerBlock(WorldView w)   { return 2; }
        @Override public    int  getTickRate(WorldView world)            { return 30; }
        @Override protected float getBlastResistance()                   { return 100F; }

        @Override
        protected BlockState toBlockState(FluidState state) {
            return BLOCKS[type.ordinal()].getDefaultState()
                    .with(net.minecraft.block.FluidBlock.LEVEL, getBlockStateLevel(state)); // ✅ correct property
        }

        @Override
        protected void appendProperties(StateManager.Builder<Fluid, FluidState> b) {
            super.appendProperties(b);
            b.add(Properties.LEVEL_1_8);
        }

        @Override public Item  getBucketItem() { return BUCKETS[type.ordinal()]; }
        @Override public Fluid getFlowing()    { return FLOWING[type.ordinal()]; }
        @Override public Fluid getStill()      { return STILL[type.ordinal()]; }

        // helps MC treat “this type” as the same family
        @Override
        public boolean matchesType(Fluid other) {
            return other == getStill() || other == getFlowing();
        }

        // ---- concrete flavors ----
        public static final class Still extends EssenceFluid {
            public Still(EssenceType t) { super(t); }
            @Override protected boolean isInfinite(World world) { return false; }
            @Override protected void beforeBreakingBlock(WorldAccess w, BlockPos p, BlockState s) {}
            @Override protected boolean canBeReplacedWith(FluidState st, BlockView w, BlockPos p, Fluid f, Direction dir) { return false; }
        }

        public static final class Flowing extends EssenceFluid {
            public Flowing(EssenceType t) { super(t); }
            @Override protected boolean isInfinite(World world) { return false; }
            @Override protected void beforeBreakingBlock(WorldAccess w, BlockPos p, BlockState s) {}
            @Override protected boolean canBeReplacedWith(FluidState st, BlockView w, BlockPos p, Fluid f, Direction dir) { return false; }
        }
    }

    /* ====================================================================== */
    /*  Opaque “lava-thick” fluid block                                       */
    /* ====================================================================== */
    public static class EssenceFluidBlock extends FluidBlock {
        public final EssenceType type;
        public EssenceFluidBlock(EssenceType type, FlowableFluid still, AbstractBlock.Settings settings) {
            super(still, settings);
            this.type = type;
        }

        // Opaque/dark look
        @Override public float getAmbientOcclusionLightLevel(BlockState s, BlockView w, BlockPos p) { return 0.0f; }

        @Override
        public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
            return super.isTransparent(state, world, pos);
        }

        @Override
        public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
            // Strong horizontal + modest vertical drag (similar to cobweb/powder snow style)
            // Values are (x, y, z) multipliers applied internally by the engine
            entity.slowMovement(state, new net.minecraft.util.math.Vec3d(0.30D, 0.20D, 0.30D));

            // Extra nudge so it "immediately" feels heavy—kept subtle to avoid jitter
            var v = entity.getVelocity();
            entity.setVelocity(v.x * 0.85, v.y * 0.90, v.z * 0.85);

            super.onEntityCollision(state, world, pos, entity);
        }
    }

    /* ====================================================================== */
    /*  Arrays keyed by EssenceType.ordinal()                                  */
    /* ====================================================================== */
    public static final FlowableFluid[] STILL   = new FlowableFluid[EssenceType.values().length];
    public static final FlowableFluid[] FLOWING = new FlowableFluid[EssenceType.values().length];
    public static final FluidBlock[]    BLOCKS  = new FluidBlock[EssenceType.values().length];
    public static final Item[]          BUCKETS = new Item[EssenceType.values().length];

    public static void registerAll() {
        for (EssenceType t : EssenceType.values()) {
            int i = t.ordinal();

            STILL[i]   = Registry.register(Registries.FLUID, id(t.key + "_fluid"),
                    new EssenceFluid.Still(t));
            FLOWING[i] = Registry.register(Registries.FLUID, id("flowing_" + t.key),
                    new EssenceFluid.Flowing(t));

            var blockSettings = FabricBlockSettings.copyOf(Blocks.LAVA)
                    .noCollision()
                    .dropsNothing()
                    .strength(100.0f)
                    .velocityMultiplier(0.4f)        // ← slows movement while on/at surface
                    .jumpVelocityMultiplier(0.5f);   // ← reduces hop out pop

            if (t == EssenceType.LIGHT) blockSettings = blockSettings.luminance(14);
            if (t == EssenceType.COLD) blockSettings = blockSettings.luminance(8);
            if (t == EssenceType.GAIA) blockSettings = blockSettings.luminance(8);
            if (t == EssenceType.HOT) blockSettings = blockSettings.luminance(10);
            if (t == EssenceType.DEATH) blockSettings = blockSettings.luminance(6);
            if (t == EssenceType.LIFE) blockSettings = blockSettings.luminance(10);

            BLOCKS[i]  = Registry.register(Registries.BLOCK, id(t.key + "_fluid_block"),
                    new EssenceFluidBlock(t, (FlowableFluid) STILL[i], blockSettings));

            BUCKETS[i] = Registry.register(Registries.ITEM, id("bucket_" + t.key),
                    new BucketItem(STILL[i], new Item.Settings()
                            .maxCount(1)
                            .recipeRemainder(Items.BUCKET)));
        }
    }

    /* Helpers for other code */
    public static Item          bucketFor(EssenceType t)  { return BUCKETS[t.ordinal()]; }
    public static FlowableFluid still(EssenceType t)      { return (FlowableFluid) STILL[t.ordinal()]; }
    public static FluidBlock    fluidBlock(EssenceType t) { return BLOCKS[t.ordinal()]; }
}
