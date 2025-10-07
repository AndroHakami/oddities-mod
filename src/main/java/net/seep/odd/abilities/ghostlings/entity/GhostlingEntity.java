package net.seep.odd.abilities.ghostlings.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.entity.projectile.thrown.SnowballEntity;

import java.util.*;

import net.seep.odd.mixin.access.CropBlockInvoker;

public class GhostlingEntity extends PathAwareEntity implements GeoEntity {
    public enum Job { NONE, FIGHTER, MINER, FARMER, COURIER }

    /* ---- Client-synced animation pose ---- */
    public enum WorkPose { NONE, REPLANT, MINING, HAPPY, DEPRESSED }
    private static int poseToInt(WorkPose p){
        return switch (p){
            case NONE -> 0; case REPLANT -> 1; case MINING -> 2; case HAPPY -> 3; case DEPRESSED -> 4;
        };
    }
    private static WorkPose intToPose(int i){
        return switch (i){
            case 1 -> WorkPose.REPLANT; case 2 -> WorkPose.MINING; case 3 -> WorkPose.HAPPY; case 4 -> WorkPose.DEPRESSED; default -> WorkPose.NONE;
        };
    }
    private static final TrackedData<Integer> POSE_IDX =
            DataTracker.registerData(GhostlingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private int poseUntilTick = 0;

    /* ---- courier travel tracking ---- */
    private static final TrackedData<Boolean> TRAVELLING =
            DataTracker.registerData(GhostlingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> TRAVEL_PROGRESS =
            DataTracker.registerData(GhostlingEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /* ---- generic job progress for UI (miner/courier bar) ---- */
    private static final TrackedData<Float> JOB_PROGRESS =
            DataTracker.registerData(GhostlingEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /* ---- mood (0..1) ---- */
    private static final TrackedData<Float> MOOD =
            DataTracker.registerData(GhostlingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final float BASE_MOVE = 0.28f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Job job = Job.NONE;
    private UUID owner;
    private boolean working = false;

    private BlockPos workOrigin = null;
    private boolean stayWithinRange = false;
    private static final int STAY_RANGE = 16;

    private BlockPos homePos = null;
    private boolean homeInitialized = false;

    /** Equipment/tool slot (1) + cargo (3×3). */
    private final SimpleInventory tool  = new SimpleInventory(1);
    private final SimpleInventory cargo = new SimpleInventory(9);

    /* ---- FARMER ---- */
    private static final int FARMER_RADIUS = 16;
    private BlockPos farmerDepositChest = null;

    /* ---- MINER ---- */
    public static record BlockSnapshot(BlockPos pos, BlockState state) {}
    private boolean minerActive = false;
    private BlockPos minerDepositChest = null;
    private final List<BlockSnapshot> minerTargets = new ArrayList<>();
    private int minerIndex = 0;
    private int mineTicks = 0;
    private int mineTicksRequired = 0;

    /* ---- COURIER ---- */
    private BlockPos courierTarget = null;
    private boolean courierActive = false;
    private boolean returning = false;
    int travelStartTick = 0;
    int travelTotalTicks = 0;

    private BlockPos pendingTeleport = null;
    private boolean pendingDeposit = false;
    private int postTeleportDelay = 0;
    private boolean queueReturnAfterDeposit = false;

    private static final ChunkTicketType<UUID> TRAVEL_TICKET =
            ChunkTicketType.create("odd:ghost_travel", UUID::compareTo);
    private ChunkPos travelTicketPos = null;
    private static final int TICKET_LEVEL = 31;

    /* ===== ctor & navigation ===== */
    public GhostlingEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setCanPickUpLoot(true);
        this.setPersistent();
        this.moveControl = new FlightMoveControl(this, 20, true);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanEnterOpenDoors(false);
        nav.setCanPathThroughDoors(false);
        nav.setCanSwim(true);
        return nav;
    }

    /* ------------------ tracked data ------------------ */
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(POSE_IDX, poseToInt(WorkPose.NONE));
        this.dataTracker.startTracking(TRAVELLING, false);
        this.dataTracker.startTracking(TRAVEL_PROGRESS, 0f);
        this.dataTracker.startTracking(JOB_PROGRESS, 0f);
        this.dataTracker.startTracking(MOOD, 0.75f); // start fairly happy
    }

    private void setWorkPose(WorkPose p){ this.dataTracker.set(POSE_IDX, poseToInt(p)); }
    private WorkPose getWorkPose(){ return intToPose(this.dataTracker.get(POSE_IDX)); }
    private void setTimedPose(WorkPose p, int ticks){
        setWorkPose(p);
        if (this.getWorld() instanceof ServerWorld sw) {
            poseUntilTick = (int) sw.getTime() + Math.max(1, ticks);
        }
    }

    private void setTravelling(boolean v) { this.dataTracker.set(TRAVELLING, v); this.setInvisible(v); }
    public boolean isTravelling() { return this.dataTracker.get(TRAVELLING); }
    private void setTravelProgress(float p) { this.dataTracker.set(TRAVEL_PROGRESS, clamp01(p)); }
    public float getTravelProgress() { return this.dataTracker.get(TRAVEL_PROGRESS); }
    private void setJobProgress(float p) { this.dataTracker.set(JOB_PROGRESS, clamp01(p)); }
    public float getJobProgress() { return this.dataTracker.get(JOB_PROGRESS); }
    public float getMood() { return clamp01(this.dataTracker.get(MOOD)); }
    private void setMood(float v) { this.dataTracker.set(MOOD, clamp01(v)); }
    private boolean isDepressed(){ return getMood() <= 0.10f; }
    private static float clamp01(float f){ return Math.max(0f, Math.min(1f, f)); }

    /* ------------------ attributes ------------------ */
    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_MOVE)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 2.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4);
    }

    /* ------------------ misc state helpers ------------------ */
    public boolean isOwner(UUID uuid) { return uuid != null && uuid.equals(owner); }
    public void setOwner(UUID uuid) { this.owner = uuid; }
    public boolean isWorking() { return working; }
    public Job getJob() { return job; }

    public void setWorkOrigin(BlockPos pos) { this.workOrigin = pos; }
    public void toggleStayWithinRange() { this.stayWithinRange = !this.stayWithinRange; }
    public boolean isStayingWithinRange() { return this.stayWithinRange; }
    public void setCourierTarget(BlockPos pos) { this.courierTarget = pos; }

    public BlockPos getHome() { return homePos; }
    public void setHome(BlockPos pos) { this.homePos = pos; }

    /* farmer deposit */
    public void setFarmerDepositChest(BlockPos pos) { this.farmerDepositChest = pos; }
    @Nullable public BlockPos getFarmerDepositChest() { return farmerDepositChest; }

    /* miner deposit */
    public void setMinerDepositChest(BlockPos pos) { this.minerDepositChest = pos; }
    @Nullable public BlockPos getMinerDepositChest() { return minerDepositChest; }

    /* tool for renderer + logic */
    public ItemStack getToolStack() { return this.tool.getStack(0); }
    @Override public ItemStack getMainHandStack() { return this.tool.getStack(0); }

    /* ------------------ inventory UI (3×3 cargo + tool slot using vanilla 9×2) ------------------ */
    public NamedScreenHandlerFactory getCargoFactory() {
        return new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, player) -> {
                    // Build the 18-slot adapter each time the UI opens:
                    Inventory combined = new CargoWithToolInventory(this, this.cargo, this.tool);
                    // Use the ctor that takes (type, syncId, playerInv, inventory, rows)
                    return new GenericContainerScreenHandler(
                            ScreenHandlerType.GENERIC_9X2, syncId, playerInv, combined, 2
                    );
                },
                Text.literal("Ghostling Cargo")
        );
    }

    /** 9x2 inventory adapter: row0 slots 0..8 = cargo, row1 slot 9 = tool, 10..17 disabled (auto-drop on close). */
    private static class CargoWithToolInventory implements Inventory {
        private final GhostlingEntity owner;
        private final SimpleInventory cargo;
        private final SimpleInventory tool;
        // we keep a tiny side inventory for the “disabled” row; if players put items there, we drop them on close
        private final SimpleInventory disabled = new SimpleInventory(8);

        CargoWithToolInventory(GhostlingEntity owner, SimpleInventory cargo, SimpleInventory tool) {
            this.owner = owner; this.cargo = cargo; this.tool = tool;
        }

        @Override public int size() { return 18; }

        @Override public boolean isEmpty() {
            for (int i=0;i<9;i++) if (!cargo.getStack(i).isEmpty()) return false;
            if (!tool.getStack(0).isEmpty()) return false;
            for (int i=0;i<8;i++) if (!disabled.getStack(i).isEmpty()) return false;
            return true;
        }

        @Override public ItemStack getStack(int slot) {
            if (slot < 9) return cargo.getStack(slot);
            if (slot == 9) return tool.getStack(0);
            return disabled.getStack(slot - 10);
        }

        @Override public ItemStack removeStack(int slot, int amount) {
            if (slot < 9) return cargo.removeStack(slot, amount);
            if (slot == 9) return tool.removeStack(0, amount);
            return disabled.removeStack(slot - 10, amount);
        }

        @Override public ItemStack removeStack(int slot) {
            if (slot < 9) return cargo.removeStack(slot);
            if (slot == 9) return tool.removeStack(0);
            return disabled.removeStack(slot - 10);
        }

        @Override public void setStack(int slot, ItemStack stack) {
            if (slot < 9) cargo.setStack(slot, stack);
            else if (slot == 9) tool.setStack(0, stack);
            else disabled.setStack(slot - 10, stack); // visually present but “not kept”
            markDirty();
        }

        @Override public void markDirty() { cargo.markDirty(); tool.markDirty(); disabled.markDirty(); }
        @Override public boolean canPlayerUse(PlayerEntity player) { return owner.isAlive(); }
        @Override public void clear() { cargo.clear(); tool.clear(); disabled.clear(); }

        @Override public void onOpen(PlayerEntity player) {}
        @Override public void onClose(PlayerEntity player) {
            // ensure disabled row never “stores” items
            for (int i = 0; i < 8; i++) {
                ItemStack s = disabled.getStack(i);
                if (!s.isEmpty()) {
                    owner.dropStack(s.copy());
                    disabled.setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(2, new GhostWorkGoal(this));
        this.goalSelector.add(7, new WanderAroundGoal(this, 1.0));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
    }

    /* ------------------ core tick (flight, noclip pref, mood) ------------------ */
    @Override
    public void tick() {
        super.tick();

        this.setNoGravity(true);
        this.fallDistance = 0f;

        if (!this.getWorld().isClient) {
            if (!this.isInsideWall() && !working) this.noClip = false;
        }

        if (!homeInitialized && !this.getWorld().isClient) {
            homeInitialized = true;
            if (homePos == null) homePos = this.getBlockPos();
        }

        if (!this.getWorld().isClient && poseUntilTick > 0 && this.getWorld() instanceof ServerWorld sw) {
            if ((int) sw.getTime() >= poseUntilTick) {
                setWorkPose(WorkPose.NONE);
                poseUntilTick = 0;
            }
        }

        // mood drain & movement mult
        if (!this.getWorld().isClient && this.age % 40 == 0) {
            float m = getMood();
            float drain = 0.0015f; // natural
            if (working) {
                switch (job) {
                    case FARMER -> drain += 0.0040f;
                    case MINER  -> drain += 0.0050f;
                    case FIGHTER-> drain += 0.0060f;
                    case COURIER-> drain += 0.0030f;
                    default -> {}
                }
            }
            setMood(m - drain);
            m = getMood();

            double mult = 1.0;
            if (m >= 0.80f) mult = 1.15;
            else if (m <= 0.10f) mult = 0.0;
            else if (m <= 0.30f) mult = 0.70;
            else if (m <= 0.50f) mult = 0.90;

            EntityAttributeInstance ai = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (ai != null) ai.setBaseValue(BASE_MOVE * mult);
        }
    }

    /* --------- immunity to wall damage while noclipping --------- */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.noClip && source.isOf(DamageTypes.IN_WALL)) return true;
        return super.isInvulnerableTo(source);
    }

    /* --------- flight helpers --------- */
    private void flyTo(BlockPos to, double speed) { flyTo(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, speed); }
    private void flyTo(double x, double y, double z, double speed) {
        this.getNavigation().stop();

        y += 0.10;

        Vec3d from = this.getPos().add(0, this.getStandingEyeHeight(), 0);
        Vec3d dst  = new Vec3d(x, y, z);

        boolean blocked = false;
        if (this.getWorld() != null) {
            HitResult hr = this.getWorld().raycast(new RaycastContext(
                    from, dst, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            blocked = hr.getType() != HitResult.Type.MISS;
        }

        this.noClip = blocked || this.isInsideWall();

        double s = MathHelper.clamp(speed, 0.0, 2.5);
        this.getMoveControl().moveTo(x, y, z, s);
    }

    /** Hard stop in air: zero velocity, stop nav, drop noclip. */
    private void hardStop() {
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);
        this.noClip = false;
    }

    // ghosts don't take fall damage
    @Override
    public boolean handleFallDamage(float distance, float damageMultiplier, DamageSource damageSource) { return false; }

    /* ------------------ interact (+10% mood on feed) ------------------ */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        ItemStack stack = player.getStackInHand(hand);

        // Feed: +10% mood + heal
        if (stack.isOf(Items.GHAST_TEAR) || stack.isOf(Items.PHANTOM_MEMBRANE)) {
            this.heal(12f);
            setMood(getMood() + 0.10f);
            setTimedPose(WorkPose.HAPPY, 40);
            if (!player.getAbilities().creativeMode) stack.decrement(1);
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, getX(), getY()+1.2, getZ(), 6, 0.3,0.3,0.3, 0.01);
            }
            playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1f, 1.0f);
            return ActionResult.CONSUME;
        }

        // Cargo UI (owner only)
        if (player.isSneaking() && isOwner(player.getUuid())) {
            player.openHandledScreen(getCargoFactory());
            return ActionResult.SUCCESS;
        }

        // Equip/replace tool (owner only) — goes to visible tool slot
        if (isOwner(player.getUuid())) {
            if (stack.getItem() instanceof HoeItem || stack.getItem() instanceof MiningToolItem || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof ShovelItem) {
                tool.setStack(0, stack.copyWithCount(1));
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                player.sendMessage(Text.literal("Equipped: " + tool.getStack(0).getItem().getName().getString()), true);
                if (job == Job.NONE) job = toolToJob(tool.getStack(0));
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    private Job toolToJob(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem)  return Job.FIGHTER;
        if (stack.getItem() instanceof HoeItem)    return Job.FARMER;
        if (stack.getItem() instanceof MiningToolItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof ShovelItem) return Job.MINER;
        if (stack.isOf(Items.CHEST))               return Job.COURIER;
        return Job.NONE;
    }

    public NamedScreenHandlerFactory getManageFactory() { return new GhostManageFactory(this); }

    /* ------------------ geckolib ------------------ */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar regs) {
        regs.add(new AnimationController<>(this, "main", 5, state -> {
            if (this.isDead()) return PlayState.STOP;

            if (this.isTravelling()) {
                return state.setAndContinue(RawAnimation.begin().then("courier_fly", Animation.LoopType.LOOP));
            }

            if (isDepressed()) {
                return state.setAndContinue(RawAnimation.begin().then("depressed", Animation.LoopType.LOOP));
            }

            WorkPose pose = getWorkPose();
            if (pose == WorkPose.REPLANT) {
                return state.setAndContinue(RawAnimation.begin().then("replant", Animation.LoopType.LOOP));
            }
            if (pose == WorkPose.MINING) {
                return state.setAndContinue(RawAnimation.begin().then("mining", Animation.LoopType.LOOP));
            }
            if (pose == WorkPose.HAPPY) {
                return state.setAndContinue(RawAnimation.begin().then("happy", Animation.LoopType.LOOP));
            }

            if (state.isMoving())
                return state.setAndContinue(RawAnimation.begin().then("move", Animation.LoopType.LOOP));

            return state.setAndContinue(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /* ------------------ persistence ------------------ */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("Job", job.name());
        if (workOrigin != null) nbt.putLong("WorkOrigin", workOrigin.asLong());
        nbt.putBoolean("StayRange", stayWithinRange);
        if (homePos != null) nbt.putLong("HomePos", homePos.asLong());
        nbt.putBoolean("HomeInit", homeInitialized);

        nbt.putInt("PoseIdx", poseToInt(getWorkPose()));
        nbt.putInt("PoseUntil", poseUntilTick);
        nbt.putFloat("Mood", getMood());

        if (farmerDepositChest != null) nbt.putLong("FarmerDepositChest", farmerDepositChest.asLong());

        // tool
        NbtCompound toolNbt = new NbtCompound();
        ItemStack ts = tool.getStack(0);
        if (!ts.isEmpty()) toolNbt = ts.writeNbt(new NbtCompound());
        nbt.put("ToolStack", toolNbt);

        // cargo (persist 3×3)
        NbtCompound cargoNbt = new NbtCompound();
        cargoNbt.putInt("Size", cargo.size());
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack s = cargo.getStack(i);
            if (!s.isEmpty()) {
                NbtCompound e = new NbtCompound();
                e.putInt("Slot", i);
                s.writeNbt(e);
                cargoNbt.put("S" + i, e);
            }
        }
        nbt.put("Cargo", cargoNbt);

        // miner/courier state
        nbt.putBoolean("MinerActive", minerActive);
        nbt.putInt("MinerIndex", minerIndex);
        if (minerDepositChest != null) nbt.putLong("MinerDepositChest", minerDepositChest.asLong());
        NbtCompound list = new NbtCompound();
        list.putInt("Count", minerTargets.size());
        for (int i = 0; i < minerTargets.size(); i++) {
            BlockSnapshot s = minerTargets.get(i);
            NbtCompound e = new NbtCompound();
            e.putLong("Pos", s.pos().asLong());
            e.putInt("State", Block.getRawIdFromState(s.state()));
            list.put("E" + i, e);
        }
        nbt.put("MinerTargets", list);

        if (courierTarget != null) nbt.putLong("CourierTarget", courierTarget.asLong());
        nbt.putBoolean("CourierActive", courierActive);
        nbt.putBoolean("Returning", returning);
        nbt.putInt("TravelStart", travelStartTick);
        nbt.putInt("TravelTotal", travelTotalTicks);
        nbt.putBoolean("Travelling", isTravelling());
        nbt.putFloat("TravelProgress", getTravelProgress());

        if (pendingTeleport != null) nbt.putLong("PendingTeleport", pendingTeleport.asLong());
        nbt.putBoolean("PendingDeposit", pendingDeposit);
        nbt.putInt("PostTeleportDelay", postTeleportDelay);
        nbt.putBoolean("QueueReturnAfterDeposit", queueReturnAfterDeposit);

        nbt.putFloat("JobProgress", getJobProgress());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
        if (nbt.contains("Job")) job = Job.valueOf(nbt.getString("Job"));
        workOrigin = nbt.contains("WorkOrigin") ? BlockPos.fromLong(nbt.getLong("WorkOrigin")) : null;
        stayWithinRange = nbt.getBoolean("StayRange");
        homePos = nbt.contains("HomePos") ? BlockPos.fromLong(nbt.getLong("HomePos")) : null;
        homeInitialized = nbt.getBoolean("HomeInit");

        setWorkPose(intToPose(nbt.getInt("PoseIdx")));
        poseUntilTick = nbt.getInt("PoseUntil");
        setMood(nbt.contains("Mood") ? nbt.getFloat("Mood") : 0.75f);

        farmerDepositChest = nbt.contains("FarmerDepositChest") ? BlockPos.fromLong(nbt.getLong("FarmerDepositChest")) : null;

        // tool
        if (nbt.contains("ToolStack")) {
            ItemStack ts = ItemStack.fromNbt(nbt.getCompound("ToolStack"));
            tool.setStack(0, ts);
        } else {
            tool.setStack(0, ItemStack.EMPTY);
        }

        // cargo
        cargo.clear();
        if (nbt.contains("Cargo")) {
            NbtCompound cargoNbt = nbt.getCompound("Cargo");
            int sz = cargoNbt.getInt("Size");
            for (int i = 0; i < sz; i++) {
                String key = "S" + i;
                if (cargoNbt.contains(key)) {
                    NbtCompound e = cargoNbt.getCompound(key);
                    int slot = e.getInt("Slot");
                    ItemStack s = ItemStack.fromNbt(e);
                    if (slot >= 0 && slot < cargo.size()) cargo.setStack(slot, s);
                }
            }
        }

        // miner
        minerActive = nbt.getBoolean("MinerActive");
        minerIndex  = nbt.getInt("MinerIndex");
        minerDepositChest = nbt.contains("MinerDepositChest") ? BlockPos.fromLong(nbt.getLong("MinerDepositChest")) : null;
        minerTargets.clear();
        if (nbt.contains("MinerTargets")) {
            NbtCompound list = nbt.getCompound("MinerTargets");
            int count = list.getInt("Count");
            for (int i = 0; i < count; i++) {
                NbtCompound e = list.getCompound("E" + i);
                BlockPos p = BlockPos.fromLong(e.getLong("Pos"));
                BlockState s = Block.getStateFromRawId(e.getInt("State"));
                minerTargets.add(new BlockSnapshot(p, s));
            }
        }

        // courier
        courierTarget = nbt.contains("CourierTarget") ? BlockPos.fromLong(nbt.getLong("CourierTarget")) : null;
        courierActive = nbt.getBoolean("CourierActive");
        returning = nbt.getBoolean("Returning");
        travelStartTick = nbt.getInt("TravelStart");
        travelTotalTicks = nbt.getInt("TravelTotal");
        setTravelling(nbt.getBoolean("Travelling"));
        setTravelProgress(nbt.getFloat("TravelProgress"));

        pendingTeleport = nbt.contains("PendingTeleport") ? BlockPos.fromLong(nbt.getLong("PendingTeleport")) : null;
        pendingDeposit  = nbt.getBoolean("PendingDeposit");
        postTeleportDelay = nbt.getInt("PostTeleportDelay");
        queueReturnAfterDeposit = nbt.getBoolean("QueueReturnAfterDeposit");

        setJobProgress(nbt.getFloat("JobProgress"));
    }

    /* ------------------ work driver ------------------ */

    boolean hasWorkToDo() {
        if (isDepressed()) return false;
        if (isTravelling() || pendingTeleport != null || postTeleportDelay > 0 || pendingDeposit) return true;
        if (stayWithinRange && workOrigin != null && !getBlockPos().isWithinDistance(workOrigin, STAY_RANGE)) {
            return true;
        }
        return switch (job) {
            case FIGHTER -> findFighterTarget() != null;
            case MINER   -> minerActive || (minerDepositChest != null && !isCargoEmpty());
            case FARMER  -> hasMatureCropNearby() || (farmerDepositChest != null && !isCargoEmpty());
            case COURIER -> courierActive;
            default      -> false;
        };
    }

    private boolean hasMatureCropNearby() {
        BlockPos center = (workOrigin != null) ? workOrigin : getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy=-1; dy<=1; dy++) {
            for (int dx=-FARMER_RADIUS; dx<=FARMER_RADIUS; dx++) {
                for (int dz=-FARMER_RADIUS; dz<=FARMER_RADIUS; dz++) {
                    m.set(center.getX()+dx, center.getY()+dy, center.getZ()+dz);
                    BlockState s = this.getWorld().getBlockState(m);
                    if (s.getBlock() instanceof CropBlock crop && crop.isMature(s)) return true;
                }
            }
        }
        return false;
    }

    private LivingEntity findFighterTarget() {
        return this.getWorld().getClosestEntity(
                LivingEntity.class, TargetPredicate.DEFAULT, this,
                getX(), getY(), getZ(), getBoundingBox().expand(10));
    }

    void tickWork() {
        if (isDepressed()) {
            working = false;
            setWorkPose(WorkPose.DEPRESSED);
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);
            return;
        }

        // deferred courier arrival / deposit handling
        if (pendingTeleport != null && this.getWorld() instanceof ServerWorld sw) {
            ChunkPos cp = new ChunkPos(pendingTeleport);
            if (sw.isChunkLoaded(cp.x, cp.z)) {
                teleportSafely(pendingTeleport);
                postTeleportDelay = 3;
                if (courierActive && !returning) {
                    pendingDeposit = true;
                    queueReturnAfterDeposit = true;
                }
                pendingTeleport = null;
            }
            return;
        }
        if (postTeleportDelay > 0) { postTeleportDelay--; return; }
        if (pendingDeposit) {
            arriveAndPlaceChest();
            pendingDeposit = false;
            if (queueReturnAfterDeposit) {
                queueReturnAfterDeposit = false;
                if (homePos != null) {
                    returning = true;
                    startTravelTowards(homePos);
                } else {
                    finishCourier();
                }
            }
            return;
        }
        if (courierActive && job == Job.COURIER && isTravelling()) { tickCourierTravel(); return; }

        // stay range leash
        if (stayWithinRange && workOrigin != null) {
            if (!getBlockPos().isWithinDistance(workOrigin, STAY_RANGE)) {
                flyTo(workOrigin.getX()+0.5, workOrigin.getY()+1, workOrigin.getZ()+0.5, 1.10);
                working = true;
                return;
            }
        }

        switch (job) {
            case FIGHTER -> tickFighter();
            case MINER   -> tickMiner();
            case FARMER  -> tickFarmer();
            case COURIER -> working = false;
            default      -> working = false;
        }
    }

    /* ------------------ fighter ------------------ */
    private void tickFighter() {
        working = false;
        PlayerEntity ownerPlayer = getOwnerPlayer();
        if (ownerPlayer == null) return;

        LivingEntity target = findFighterTarget();
        if (target == null || target == ownerPlayer || target == this || !target.isAlive() || !target.isAttackable()) return;

        working = true;
        ItemStack weap = tool.getStack(0);
        boolean melee = weap.getItem() instanceof SwordItem || weap.getItem() instanceof AxeItem;

        if (melee) {
            flyTo(target.getX(), target.getY() + 0.3, target.getZ(), 1.10);
            if (this.distanceTo(target) < 2.6f && this.age % 12 == 0) {
                float dmg = 3.0f + EnchantmentHelper.getAttackDamage(weap, target.getGroup());
                target.damage(getDamageSources().mobAttack(this), dmg);
                playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.0f);
            }
        } else {
            if (age % 30 == 0) {
                SnowballEntity proj = new SnowballEntity(this.getWorld(), this);
                Vec3d dir = target.getPos().add(0, target.getStandingEyeHeight(), 0)
                        .subtract(getPos().add(0, getStandingEyeHeight(), 0)).normalize();
                proj.setVelocity(dir.x, dir.y, dir.z, 1.35f, 1f);
                this.getWorld().spawnEntity(proj);
                playSound(SoundEvents.ENTITY_SNOWBALL_THROW, 1f, 0.9f + this.getWorld().random.nextFloat()*0.2f);
            }
        }
    }

    /* ------------------ miner ------------------ */
    public void beginMinerJob(List<BlockSnapshot> snapshots) {
        this.job = Job.MINER;
        this.minerTargets.clear();
        this.minerTargets.addAll(snapshots);
        this.minerIndex = 0;
        this.mineTicks = 0;
        this.mineTicksRequired = 0;
        this.minerActive = !this.minerTargets.isEmpty();
        setJobProgress(0f);
    }

    private boolean isCargoNearlyFull() {
        int empties = 0;
        for (int i = 0; i < cargo.size(); i++) if (cargo.getStack(i).isEmpty()) empties++;
        return empties == 0;
    }

    private void tickMiner() {
        if (!minerActive) {
            if (minerDepositChest != null && !isCargoEmpty()) {
                depositToSpecificChest(minerDepositChest);
                return;
            }
            working = false;
            if (getWorkPose() == WorkPose.MINING) setWorkPose(WorkPose.NONE);
            setJobProgress(0f);
            return;
        }

        if (minerDepositChest != null && isCargoNearlyFull()) {
            depositToSpecificChest(minerDepositChest);
            return;
        }

        BlockSnapshot target = null;
        while (minerIndex < minerTargets.size()) {
            BlockSnapshot sn = minerTargets.get(minerIndex);
            BlockState cur = getWorld().getBlockState(sn.pos());
            if (!cur.isAir() && cur.getBlock() == sn.state().getBlock()) { target = sn; break; }
            minerIndex++;
            setJobProgress(minerTargets.isEmpty() ? 0f : minerIndex / (float) minerTargets.size());
        }

        if (target == null) {
            minerActive = false;
            if (minerDepositChest != null && !isCargoEmpty()) depositToSpecificChest(minerDepositChest);
            working = false;
            if (getWorkPose() == WorkPose.MINING) setWorkPose(WorkPose.NONE);
            setJobProgress(1f);
            return;
        }

        BlockPos p = target.pos();
        flyTo(p, 0.95);

        if (!getBlockPos().isWithinDistance(p, 1.9)) {
            working = false;
            return;
        }

        // FULL STOP before breaking
        hardStop();

        this.getLookControl().lookAt(p.getX()+0.5, p.getY()+0.5, p.getZ()+0.5);
        setTimedPose(WorkPose.MINING, 6);

        working = true;
        if (mineTicksRequired <= 0) {
            mineTicks = 0;
            mineTicksRequired = computeMineTicks(getWorld().getBlockState(p));
        } else {
            mineTicks++;
        }

        setJobProgress(minerTargets.isEmpty() ? 0f :
                (minerIndex + (mineTicks / (float)Math.max(1, mineTicksRequired))) / minerTargets.size());

        if (mineTicks >= mineTicksRequired) {
            ServerWorld sw = (ServerWorld) this.getWorld();
            BlockState state = sw.getBlockState(p);

            if (!state.isAir() && state.getBlock() == target.state().getBlock()) {
                sw.syncWorldEvent(2001, p, Block.getRawIdFromState(state));

                LootContextParameterSet.Builder ctx = new LootContextParameterSet.Builder(sw)
                        .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(p))
                        .add(LootContextParameters.TOOL, tool.getStack(0));
                java.util.List<ItemStack> drops = state.getBlock().getDroppedStacks(state, ctx);

                sw.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

                for (ItemStack s : drops) {
                    ItemStack rem = insertStack(cargo, s);
                    if (!rem.isEmpty()) this.dropStack(rem);
                }

                sw.playSound(null, p, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.5f, 1.0f);
            }

            minerIndex++;
            mineTicks = 0;
            mineTicksRequired = 0;
            setJobProgress(minerTargets.isEmpty() ? 0f : minerIndex / (float) minerTargets.size());
        }
    }

    private int computeMineTicks(BlockState state) {
        ItemStack t = tool.getStack(0);
        float speed = 1f;
        if (t.getItem() instanceof MiningToolItem mti) speed = mti.getMiningSpeedMultiplier(t, state);
        int base = 30;
        return Math.max(10, Math.round(base / Math.max(1f, speed)));
    }

    /* ------------------ farmer ------------------ */
    private void tickFarmer() {
        working = false;

        if (farmerDepositChest != null && !hasMatureCropNearby() && !isCargoEmpty()) {
            depositToSpecificChest(farmerDepositChest);
            return;
        }

        if (!hasHoe()) return;
        BlockPos center = (workOrigin != null) ? workOrigin : getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        outer:
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -FARMER_RADIUS; dx <= FARMER_RADIUS; dx++) {
                for (int dz = -FARMER_RADIUS; dz <= FARMER_RADIUS; dz++) {
                    m.set(center.getX()+dx, center.getY()+dy, center.getZ()+dz);
                    BlockState state = sw.getBlockState(m);
                    if (!(state.getBlock() instanceof CropBlock crop)) continue;
                    if (!crop.isMature(state)) continue;

                    flyTo(m.getX()+0.5, m.getY()+1, m.getZ()+0.5, 0.90);
                    if (!getBlockPos().isWithinDistance(m, 1.85)) { working = false; return; }

                    // FULL STOP before harvesting
                    hardStop();

                    this.getLookControl().lookAt(m.getX()+0.5, m.getY()+0.6, m.getZ()+0.5);

                    sw.syncWorldEvent(2001, m, Block.getRawIdFromState(state));
                    LootContextParameterSet.Builder ctx = new LootContextParameterSet.Builder(sw)
                            .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(m))
                            .add(LootContextParameters.TOOL, tool.getStack(0));
                    java.util.List<ItemStack> drops = state.getBlock().getDroppedStacks(state, ctx);
                    sw.setBlockState(m, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

                    Item seedItem = ((CropBlockInvoker)(Object)crop).odd$getSeedsItem().asItem();
                    boolean replanted = false;

                    setTimedPose(WorkPose.REPLANT, 35);
                    working = true;

                    for (ItemStack s : drops) {
                        if (!s.isEmpty() && s.isOf(seedItem) && !replanted) {
                            s.decrement(1);
                            sw.setBlockState(m, crop.getDefaultState(), Block.NOTIFY_ALL);
                            replanted = true;
                        }
                        if (!s.isEmpty()) {
                            ItemStack rem = insertStack(cargo, s);
                            if (!rem.isEmpty()) this.dropStack(rem);
                        }
                    }

                    if (!replanted) {
                        for (int i = 0; i < cargo.size(); i++) {
                            ItemStack s = cargo.getStack(i);
                            if (!s.isEmpty() && s.isOf(seedItem)) {
                                s.decrement(1);
                                sw.setBlockState(m, crop.getDefaultState(), Block.NOTIFY_ALL);
                                break;
                            }
                        }
                    }

                    playSound(SoundEvents.ITEM_HOE_TILL, 0.8f, 0.95f + getWorld().random.nextFloat() * 0.1f);
                    break outer; // one crop per tick
                }
            }
        }

        if (farmerDepositChest != null && !isCargoEmpty() && !hasMatureCropNearby()) {
            depositToSpecificChest(farmerDepositChest);
        }
    }

    private boolean hasHoe() {
        ItemStack t = tool.getStack(0);
        return !t.isEmpty() && t.getItem() instanceof HoeItem;
    }

    /* ------------------ deposit helper used by farmer+miner ------------------ */
    private void depositToSpecificChest(BlockPos chestPos) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        flyTo(chestPos, 0.92);
        if (!getBlockPos().isWithinDistance(chestPos, 1.85)) { working = false; return; }

        hardStop();

        this.getLookControl().lookAt(chestPos.getX() + 0.5, chestPos.getY() + 0.7, chestPos.getZ() + 0.5);
        setTimedPose(WorkPose.REPLANT, 35);
        working = true;

        BlockEntity be = sw.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            sw.playSound(null, chestPos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.6f, 1.0f);

            for (int i = 0; i < cargo.size(); i++) {
                ItemStack s = cargo.getStack(i);
                if (s.isEmpty()) continue;
                ItemStack rem = insertStack(chest, s.copy());
                cargo.setStack(i, rem);
            }
            chest.markDirty();

            sw.playSound(null, chestPos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.6f, 1.0f);
        } else {
            if (job == Job.FARMER) farmerDepositChest = null;
            if (job == Job.MINER)  minerDepositChest  = null;
        }
    }

    /* ------------------ courier ------------------ */
    public void beginCourierRun(BlockPos target) {
        this.job = Job.COURIER;
        this.courierTarget = target;
        this.courierActive = true;
        this.returning = false;
        startTravelTowards(target);
    }

    private void startTravelTowards(BlockPos dest) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        double dist = this.getPos().distanceTo(dest.toCenterPos());
        int seconds = Math.max(2, Math.min(60, (int)Math.ceil(dist / 64.0)));
        this.travelTotalTicks = seconds * 20;
        this.travelStartTick = (int) sw.getTime();
        setTravelling(true);
        setTravelProgress(0f);
        working = true;
        retargetTravelTicket(new ChunkPos(dest));
        sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY()+0.6, getZ(), 10, 0.25,0.25,0.25, 0.02);
        playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
    }

    private void tickCourierTravel() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        int now = (int) sw.getTime();
        int elapsed = Math.max(0, now - travelStartTick);
        if (travelTotalTicks <= 0) travelTotalTicks = 40;
        float progress = Math.min(1f, elapsed / (float) travelTotalTicks);
        setTravelProgress(progress);

        if (elapsed >= travelTotalTicks) {
            if (pendingTeleport != null) return;

            if (!returning) {
                pendingTeleport = courierTarget;
                pendingDeposit  = true;
                queueReturnAfterDeposit = true;
            } else {
                pendingTeleport = (homePos != null) ? homePos : this.getBlockPos();
                pendingDeposit  = false;
                queueReturnAfterDeposit = false;
                if (homePos != null) retargetTravelTicket(new ChunkPos(homePos));
            }
        }
    }

    private void finishCourier() {
        detachTravelTicket();
        setTravelling(false);
        working = false;
        returning = false;
        courierActive = false;
        courierTarget = null;
        pendingTeleport = null;
        pendingDeposit = false;
        postTeleportDelay = 0;
        queueReturnAfterDeposit = false;
        travelStartTick = 0;
        travelTotalTicks = 0;
        setTravelProgress(0f);
    }

    private void retargetTravelTicket(ChunkPos newPos) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        ServerChunkManager cm = sw.getChunkManager();
        if (travelTicketPos != null && travelTicketPos.equals(newPos)) return;
        if (travelTicketPos != null) cm.removeTicket(TRAVEL_TICKET, travelTicketPos, TICKET_LEVEL, this.getUuid());
        cm.addTicket(TRAVEL_TICKET, newPos, TICKET_LEVEL, this.getUuid());
        travelTicketPos = newPos;
    }
    private void detachTravelTicket() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (travelTicketPos != null) {
            sw.getChunkManager().removeTicket(TRAVEL_TICKET, travelTicketPos, TICKET_LEVEL, this.getUuid());
            travelTicketPos = null;
        }
    }

    private void teleportSafely(BlockPos dest) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        BlockPos pos = findStandable(dest);
        this.refreshPositionAfterTeleport(pos.getX() + 0.5, pos.getY() + 0.01, pos.getZ() + 0.5);
        this.setVelocity(Vec3d.ZERO);
        this.fallDistance = 0.0f;
        sw.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.2,0.2,0.2, 0.02);
        playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);

        if (returning && !pendingDeposit && postTeleportDelay == 0) {
            finishCourier();
        }
    }

    private BlockPos findStandable(BlockPos base) {
        World w = this.getWorld();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy = 0; dy <= 6; dy++) {
            m.set(base.getX(), base.getY() + dy, base.getZ());
            if (w.getBlockState(m).isAir() && w.getBlockState(m.up()).isAir()) return m.toImmutable();
        }
        for (int dy = -1; dy >= -6; dy--) {
            m.set(base.getX(), base.getY() + dy, base.getZ());
            if (w.getBlockState(m).isAir() && w.getBlockState(m.up()).isAir()) return m.toImmutable();
        }
        return base;
    }

    private void arriveAndPlaceChest() {
        World w = this.getWorld();
        BlockPos chestPos = courierTarget;

        BlockState state = w.getBlockState(chestPos);
        if (state.isAir() || state.isReplaceable()) {
            w.setBlockState(chestPos, net.minecraft.block.Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
        }

        BlockEntity be = w.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            depositAllCargoInto(chest);
            chest.markDirty();
        } else {
            for (int i=0;i<cargo.size();i++) {
                ItemStack s = cargo.getStack(i);
                if (!s.isEmpty()) { dropStack(s); cargo.setStack(i, ItemStack.EMPTY); }
            }
        }
        if (homePos == null) finishCourier();
    }

    private boolean isCargoEmpty() {
        for (int i = 0; i < cargo.size(); i++) if (!cargo.getStack(i).isEmpty()) return false;
        return true;
    }
    private void depositAllCargoInto(Inventory dest) {
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack s = cargo.getStack(i);
            if (s.isEmpty()) continue;
            ItemStack remaining = insertStack(dest, s.copy());
            cargo.setStack(i, remaining);
        }
    }

    private ItemStack insertStack(Inventory inv, ItemStack src) {
        if (src.isEmpty()) return ItemStack.EMPTY;

        // Merge
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack cur = inv.getStack(slot);
            if (cur.isEmpty()) continue;
            if (!ItemStack.canCombine(cur, src)) continue;

            int max = Math.min(cur.getMaxCount(), inv.getMaxCountPerStack());
            int canMove = Math.min(src.getCount(), max - cur.getCount());
            if (canMove > 0) {
                cur.increment(canMove);
                src.decrement(canMove);
                if (src.isEmpty()) return ItemStack.EMPTY;
            }
        }
        // First empty
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack cur = inv.getStack(slot);
            if (cur.isEmpty()) {
                inv.setStack(slot, src.copy());
                return ItemStack.EMPTY;
            }
        }
        return src;
    }

    @Override
    public void remove(RemovalReason reason) {
        detachTravelTicket();
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource source) {
        detachTravelTicket();
        super.onDeath(source);
    }

    @Nullable
    private PlayerEntity getOwnerPlayer() {
        World w = this.getWorld();
        if (owner == null || !(w instanceof ServerWorld sw)) return null;
        return sw.getServer().getPlayerManager().getPlayer(owner);
    }

    /* goal impl */
    private static class GhostWorkGoal extends Goal {
        private final GhostlingEntity g;
        GhostWorkGoal(GhostlingEntity g) { this.g = g; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() { return g.hasWorkToDo(); }
        @Override public boolean shouldContinue() { return g.hasWorkToDo(); }
        @Override public void tick() { g.tickWork(); }
    }
}
