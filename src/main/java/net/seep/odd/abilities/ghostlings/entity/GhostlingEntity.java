// FILE: net/seep/odd/abilities/ghostlings/entity/GhostlingEntity.java
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
import net.minecraft.entity.mob.HostileEntity;
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
import net.minecraft.util.math.Box;
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
    public enum BehaviorMode { NORMAL, FOLLOW, GUARD }

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
    private BehaviorMode behavior = BehaviorMode.NORMAL;

    private UUID owner;
    private boolean working = false;

    private BlockPos workOrigin = null;
    private boolean stayWithinRange = false;
    private static final int STAY_RANGE = 16;
    private boolean inventoryDropped = false;

    private BlockPos guardCenter = null;

    private BlockPos homePos = null;
    private boolean homeInitialized = false;

    /** Equipment/tool slot (1) + cargo (3×3). */
    private final SimpleInventory tool  = new SimpleInventory(1);
    private final SimpleInventory cargo = new SimpleInventory(9);

    /* ---- FARMER ---- */
    private static final int FARMER_RADIUS = 16;
    private BlockPos farmerDepositChest = null;

    // ✅ NEW: Deposit should be after job completion, and should stop spamming full chests
    private boolean farmerWorkedSinceDepositSet = false;
    private int farmerDepositCooldown = 0;

    /* ---- MINER ---- */
    public static record BlockSnapshot(BlockPos pos, BlockState state) {}
    private boolean minerActive = false;
    private BlockPos minerDepositChest = null;
    private final List<BlockSnapshot> minerTargets = new ArrayList<>();
    private int minerIndex = 0;
    private int mineTicks = 0;
    private int mineTicksRequired = 0;

    // ✅ NEW: Deposit should be after job completion, and should stop spamming full chests
    private boolean minerWorkedSinceDepositSet = false;
    private int minerDepositCooldown = 0;

    /* ---- FIGHTER ---- */
    private int fighterAttackCooldown = 0;

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

    // light SFX state to avoid spam
    private boolean wasDepressed = false;
    private int hoverSfxCooldown = 0;
    private int depressedSfxCooldown = 0;

    // Client-synced "what item should be shown in hand"
    private static final TrackedData<ItemStack> RENDER_TOOL =
            DataTracker.registerData(GhostlingEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    // keep noclip on for a few ticks after wall/blocked movement so they don't suffocate
    private int noclipGraceTicks = 0;

    /* ===== ctor & navigation ===== */
    public GhostlingEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setPersistent();
        this.moveControl = new FlightMoveControl(this, 20, true);

        // keep renderer stack synced on inventory changes
        this.tool.addListener(inv -> syncRenderTool());
        this.cargo.addListener(inv -> syncRenderTool());
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanEnterOpenDoors(false);
        nav.setCanPathThroughDoors(false);
        nav.setCanSwim(true);
        return nav;
    }
    private void dropAllInventoryContents() {
        if (inventoryDropped) return;
        inventoryDropped = true;

        if (this.getWorld().isClient) return;

        // Drop tool slot
        ItemStack t = tool.getStack(0);
        if (!t.isEmpty()) {
            this.dropStack(t.copy());
            tool.setStack(0, ItemStack.EMPTY);
        }

        // Drop cargo slots (drops EVERYTHING in cargo, including tools inside cargo)
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack s = cargo.getStack(i);
            if (!s.isEmpty()) {
                this.dropStack(s.copy());
                cargo.setStack(i, ItemStack.EMPTY);
            }
        }

        // keep client render in sync if you use the RENDER_TOOL tracker
        if (!this.getWorld().isClient) {
            syncRenderTool();
        }
    }

    /* ------------------ tracked data ------------------ */
    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(POSE_IDX, poseToInt(WorkPose.NONE));
        this.dataTracker.startTracking(TRAVELLING, false);
        this.dataTracker.startTracking(TRAVEL_PROGRESS, 0f);
        this.dataTracker.startTracking(JOB_PROGRESS, 0f);
        this.dataTracker.startTracking(MOOD, 0.75f);
        this.dataTracker.startTracking(RENDER_TOOL, ItemStack.EMPTY);
    }

    private void setWorkPose(WorkPose p){
        this.dataTracker.set(POSE_IDX, poseToInt(p));
    }
    private WorkPose getWorkPose(){ return intToPose(this.dataTracker.get(POSE_IDX)); }
    private void setTimedPose(WorkPose p, int ticks){
        setWorkPose(p);
        if (this.getWorld() instanceof ServerWorld sw) {
            poseUntilTick = (int) sw.getTime() + Math.max(1, ticks);
            if (p == WorkPose.HAPPY) {
                this.playSound(SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 0.8f, 0.95f + this.getWorld().random.nextFloat()*0.1f);
            }
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
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_MOVE)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 8.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 1.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4);
    }

    /* ------------------ misc state helpers ------------------ */
    public boolean isOwner(UUID uuid) { return uuid != null && uuid.equals(owner); }
    public void setOwner(UUID uuid) { this.owner = uuid; }
    public boolean isWorking() { return working; }
    public Job getJob() { return job; }
    public BehaviorMode getBehavior() { return behavior; }

    public void setWorkOrigin(BlockPos pos) { this.workOrigin = pos; }
    public void toggleStayWithinRange() { this.stayWithinRange = !this.stayWithinRange; }
    public boolean isStayingWithinRange() { return this.stayWithinRange; }
    public void setCourierTarget(BlockPos pos) { this.courierTarget = pos; }

    public BlockPos getHome() { return homePos; }
    public void setHome(BlockPos pos) { this.homePos = pos; }

    /* Behavior control (called via packets) */
    public void setFollowMode(boolean enable) {
        if (enable) behavior = BehaviorMode.FOLLOW;
        else if (behavior == BehaviorMode.FOLLOW) behavior = BehaviorMode.NORMAL;
    }
    public void setGuardCenter(@Nullable BlockPos pos) {
        guardCenter = pos;
        if (pos == null) { if (behavior == BehaviorMode.GUARD) behavior = BehaviorMode.NORMAL; }
        else behavior = BehaviorMode.GUARD;
    }
    public @Nullable BlockPos getGuardCenter() { return guardCenter; }

    /* farmer deposit */
    public void setFarmerDepositChest(BlockPos pos) {
        this.farmerDepositChest = pos;
        this.farmerWorkedSinceDepositSet = false; // ✅ do NOT immediately deposit existing items
        this.farmerDepositCooldown = 0;
    }
    @Nullable public BlockPos getFarmerDepositChest() { return farmerDepositChest; }

    /* miner deposit */
    public void setMinerDepositChest(BlockPos pos) {
        this.minerDepositChest = pos;
        // ✅ do NOT immediately deposit existing items; only after a mining run completes
        this.minerWorkedSinceDepositSet = this.minerActive; // if set mid-run, deposit after it finishes
        this.minerDepositCooldown = 0;
    }
    @Nullable public BlockPos getMinerDepositChest() { return minerDepositChest; }

    /* tool for renderer + logic */
    public ItemStack getToolStack() { return this.tool.getStack(0); }

    @Override
    public ItemStack getMainHandStack() {
        if (this.getWorld().isClient) {
            ItemStack s = getRenderToolStack();
            return s == null ? ItemStack.EMPTY : s;
        }
        return getDisplayedTool();
    }

    /** What the client should render in the ghostling's hand. */
    public ItemStack getRenderToolStack() { return this.dataTracker.get(RENDER_TOOL); }

    /** Server-side: pick the most appropriate tool to display. */
    private ItemStack computeRenderToolServer() {
        if (job == Job.FARMER) {
            ItemStack hoe = getAnyHoe();
            if (!hoe.isEmpty()) return hoe;
        }
        if (job == Job.FIGHTER) {
            ItemStack weapon = getBestWeapon();
            if (!weapon.isEmpty()) return weapon;
        }
        if (job == Job.MINER) {
            if (minerActive && minerIndex >= 0 && minerIndex < minerTargets.size()) {
                BlockSnapshot sn = minerTargets.get(minerIndex);
                BlockPos p = sn.pos();
                BlockState st = getWorld().getBlockState(p);
                if (!st.isAir() && st.getHardness(getWorld(), p) >= 0.0f) {
                    ItemStack mt = getEffectiveMiningTool(st);
                    if (!mt.isEmpty() && !mt.isOf(Items.AIR)) return mt;
                }
            }
        }
        return getDisplayedTool();
    }

    /** Server-side: push render tool to client (only when changed). */
    private void syncRenderTool() {
        if (this.getWorld().isClient) return;

        ItemStack desired = computeRenderToolServer();
        if (desired.isOf(Items.AIR)) desired = ItemStack.EMPTY;

        ItemStack current = this.dataTracker.get(RENDER_TOOL);
        if (ItemStack.areEqual(current, desired)) return;

        ItemStack copy = desired.isEmpty() ? ItemStack.EMPTY : desired.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        this.dataTracker.set(RENDER_TOOL, copy);
    }

    /* ------------------ inventory UI (3×3 cargo + tool slot via 9×2) ------------------ */
    public NamedScreenHandlerFactory getCargoFactory() {
        return new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, player) -> {
                    Inventory combined = new CargoWithToolInventory(this, this.cargo, this.tool);
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
            else disabled.setStack(slot - 10, stack);
            markDirty();
        }
        @Override public void markDirty() { cargo.markDirty(); tool.markDirty(); disabled.markDirty(); }
        @Override public boolean canPlayerUse(PlayerEntity player) { return owner.isAlive(); }
        @Override public void clear() { cargo.clear(); tool.clear(); disabled.clear(); }
        @Override public void onOpen(PlayerEntity player) {}
        @Override public void onClose(PlayerEntity player) {
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

    /* ------------------ core tick ------------------ */
    @Override
    public void tick() {
        super.tick();

        this.setNoGravity(true);
        this.fallDistance = 0f;

        if (!this.getWorld().isClient) {
            if (noclipGraceTicks > 0) noclipGraceTicks--;

            if (fighterAttackCooldown > 0) fighterAttackCooldown--;
            if (minerDepositCooldown > 0) minerDepositCooldown--;
            if (farmerDepositCooldown > 0) farmerDepositCooldown--;

            boolean forceNoClip =
                    this.isInsideWall()
                            || this.isTravelling()
                            || pendingTeleport != null
                            || postTeleportDelay > 0
                            || pendingDeposit;

            if (forceNoClip) {
                this.noClip = true;
                noclipGraceTicks = 10;
            } else if (!working && noclipGraceTicks == 0) {
                this.noClip = false;
            }

            if (this.age % 10 == 0) syncRenderTool();
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

        // mood drain & speed modulation
        if (!this.getWorld().isClient && this.age % 40 == 0) {
            float m = getMood();
            float drain = 0.0015f;
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

        // sound logic (server only)
        if (!this.getWorld().isClient) {
            boolean dep = isDepressed();

            if (dep && !wasDepressed) {
                this.playSound(SoundEvents.ENTITY_ALLAY_ITEM_TAKEN, 0.8f, 0.9f + this.getWorld().random.nextFloat()*0.2f);
                depressedSfxCooldown = 80 + this.getWorld().random.nextInt(60);
            }
            wasDepressed = dep;

            if (hoverSfxCooldown > 0) hoverSfxCooldown--;
            if (depressedSfxCooldown > 0) depressedSfxCooldown--;

            boolean hovering = isTravelling()
                    || this.getVelocity().lengthSquared() > 0.01
                    || (this.working && this.getPose() == net.minecraft.entity.EntityPose.STANDING);
            if (!dep && hovering && hoverSfxCooldown == 0) {
                this.playSound(SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.5f, 0.95f + this.getWorld().random.nextFloat()*0.1f);
                hoverSfxCooldown = 50 + this.getWorld().random.nextInt(30);
            }

            if (dep && depressedSfxCooldown == 0) {
                this.playSound(SoundEvents.ENTITY_ALLAY_ITEM_TAKEN, 0.7f, 0.95f + this.getWorld().random.nextFloat()*0.1f);
                depressedSfxCooldown = 120 + this.getWorld().random.nextInt(80);
            }
        }
    }

    /* --------- no wall damage while phasing / in grace window --------- */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.isOf(DamageTypes.IN_WALL)
                && (this.noClip || this.isInsideWall() || this.isTravelling() || noclipGraceTicks > 0)) {
            return true;
        }
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

        if (blocked || this.isInsideWall()) {
            this.noClip = true;
            noclipGraceTicks = 10;
        } else if (noclipGraceTicks == 0 && !isTravelling()) {
            this.noClip = false;
        }

        double s = MathHelper.clamp(speed, 0.0, 2.5);
        this.getMoveControl().moveTo(x, y, z, s);
    }

    /** Hard stop in air: zero velocity, stop nav. Keep noclip if intersecting blocks. */
    private void hardStop() {
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);

        if (this.isInsideWall()) {
            this.noClip = true;
            noclipGraceTicks = 10;
        } else if (!this.isTravelling() && noclipGraceTicks == 0) {
            this.noClip = false;
        }
    }

    @Override
    public boolean handleFallDamage(float distance, float damageMultiplier, DamageSource damageSource) { return false; }

    /* ------------------ interact ------------------ */
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
            this.playSound(SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 0.9f, 0.95f + this.getWorld().random.nextFloat()*0.1f);
            return ActionResult.CONSUME;
        }

        // Cargo UI (owner only)
        if (player.isSneaking() && isOwner(player.getUuid())) {
            player.openHandledScreen(getCargoFactory());
            return ActionResult.SUCCESS;
        }

        // Role assign from hand (owner only)
        if (isOwner(player.getUuid())) {
            Item item = stack.getItem();
            if (item instanceof SwordItem || item instanceof HoeItem || item instanceof MiningToolItem
                    || item instanceof AxeItem || item instanceof ShovelItem || stack.isOf(Items.CHEST)) {
                Job newJob = toolToJob(stack);
                if (newJob != Job.NONE) {
                    this.job = newJob;
                    player.sendMessage(Text.literal("Assigned job: " + newJob.name() + " (uses tools from cargo/tool slot)"), true);
                    return ActionResult.SUCCESS;
                }
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
        nbt.putString("Behavior", behavior.name());
        if (workOrigin != null) nbt.putLong("WorkOrigin", workOrigin.asLong());
        nbt.putBoolean("StayRange", stayWithinRange);
        if (homePos != null) nbt.putLong("HomePos", homePos.asLong());
        nbt.putBoolean("HomeInit", homeInitialized);
        if (guardCenter != null) nbt.putLong("GuardCenter", guardCenter.asLong());

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
        if (nbt.contains("Behavior")) behavior = BehaviorMode.valueOf(nbt.getString("Behavior"));
        workOrigin = nbt.contains("WorkOrigin") ? BlockPos.fromLong(nbt.getLong("WorkOrigin")) : null;
        stayWithinRange = nbt.getBoolean("StayRange");
        homePos = nbt.contains("HomePos") ? BlockPos.fromLong(nbt.getLong("HomePos")) : null;
        homeInitialized = nbt.getBoolean("HomeInit");
        guardCenter = nbt.contains("GuardCenter") ? BlockPos.fromLong(nbt.getLong("GuardCenter")) : null;

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

        // after load, do NOT immediately deposit; also re-sync render tool
        farmerWorkedSinceDepositSet = false;
        minerWorkedSinceDepositSet = minerActive;
        farmerDepositCooldown = 0;
        minerDepositCooldown = 0;
        fighterAttackCooldown = 0;

        if (!this.getWorld().isClient) syncRenderTool();
    }

    /* ------------------ work driver ------------------ */

    private boolean shouldDepositMiner() {
        return minerDepositChest != null
                && minerDepositCooldown == 0
                && minerWorkedSinceDepositSet
                && hasDepositableCargo();
    }

    private boolean shouldDepositFarmer() {
        return farmerDepositChest != null
                && farmerDepositCooldown == 0
                && farmerWorkedSinceDepositSet
                && hasDepositableCargo();
    }

    boolean hasWorkToDo() {
        if (isDepressed()) return false;

        if (behavior == BehaviorMode.FOLLOW) return true;
        if (behavior == BehaviorMode.GUARD && guardCenter != null && !getBlockPos().isWithinDistance(guardCenter, STAY_RANGE)) return true;

        if (isTravelling() || pendingTeleport != null || postTeleportDelay > 0 || pendingDeposit) return true;
        if (stayWithinRange && workOrigin != null && !getBlockPos().isWithinDistance(workOrigin, STAY_RANGE)) return true;

        return switch (job) {
            case FIGHTER -> findFighterTarget() != null;
            case MINER   -> minerActive || shouldDepositMiner();
            case FARMER  -> hasMatureCropNearby() || shouldDepositFarmer();
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

    /** Hostiles only (NO LOS requirement) */
    private LivingEntity findFighterTarget() {
        Box box = this.getBoundingBox().expand(12.0);

        List<HostileEntity> list = this.getWorld().getEntitiesByClass(
                HostileEntity.class,
                box,
                e -> e != null && e.isAlive() && !e.isSpectator()
        );

        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;

        for (HostileEntity h : list) {
            double d = this.squaredDistanceTo(h);
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    void tickWork() {
        if (isDepressed()) {
            working = false;
            setWorkPose(WorkPose.DEPRESSED);
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);
            return;
        }

        // FOLLOW/GUARD layer (highest priority)
        if (behavior == BehaviorMode.FOLLOW) {
            PlayerEntity p = getOwnerPlayer();
            if (p != null) {
                double d = this.distanceTo(p);
                if (d > 2.4) {
                    flyTo(p.getX(), p.getY() + 1.0, p.getZ(), 1.15);
                    working = true;
                    return;
                }
            } else behavior = BehaviorMode.NORMAL;
        } else if (behavior == BehaviorMode.GUARD && guardCenter != null) {
            if (!getBlockPos().isWithinDistance(guardCenter, STAY_RANGE)) {
                flyTo(guardCenter, 1.05);
                working = true;
                return;
            }
        }

        // courier steps
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
    private ItemStack getBestWeapon() {
        ItemStack t = tool.getStack(0);
        if (isWeapon(t)) return t;

        ItemStack best = ItemStack.EMPTY;
        for (int i=0;i<cargo.size();i++) {
            ItemStack s = cargo.getStack(i);
            if (isWeapon(s)) {
                if (best.isEmpty() || weaponScore(s) > weaponScore(best)) best = s;
            }
        }
        return best;
    }
    private boolean isWeapon(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        return s.getItem() instanceof SwordItem
                || s.getItem() instanceof AxeItem
                || s.getItem() instanceof ShovelItem
                || s.getItem() instanceof PickaxeItem;
    }
    private float weaponScore(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof SwordItem sw) return 6f + sw.getAttackDamage();
        if (it instanceof AxeItem ax)   return 5f + (float)ax.getMaterial().getAttackDamage();
        if (it instanceof PickaxeItem)  return 3.5f;
        if (it instanceof ShovelItem)   return 3.0f;
        return 1f;
    }

    private void tickFighter() {
        working = false;
        PlayerEntity ownerPlayer = getOwnerPlayer();
        if (ownerPlayer == null) return;

        LivingEntity target = findFighterTarget();
        if (target == null || target == ownerPlayer || target == this || !target.isAlive()) return;

        // Don't fight things the owner can't interact with
        if (!target.isAttackable()) return;

        ItemStack weap = getBestWeapon();
        boolean melee = isWeapon(weap) || weap.isEmpty(); // ✅ even unarmed, still attack (so "fighters do nothing" never happens)

        working = true;

        // move into range
        flyTo(target.getX(), target.getY() + 0.3, target.getZ(), 1.20);
        this.getLookControl().lookAt(target, 30.0f, 30.0f);

        if (melee) {
            if (this.distanceTo(target) < 2.75f && fighterAttackCooldown == 0) {
                // ✅ swing so you SEE it
                this.swingHand(Hand.MAIN_HAND, true);

                float base = 2.0f; // fists
                if (!weap.isEmpty()) {
                    if (weap.getItem() instanceof SwordItem sw) base = 4.0f + sw.getAttackDamage();
                    else if (weap.getItem() instanceof AxeItem ax) base = 4.0f + (float)ax.getMaterial().getAttackDamage();
                    else if (weap.getItem() instanceof PickaxeItem) base = 3.5f;
                    else if (weap.getItem() instanceof ShovelItem)  base = 3.0f;
                }

                float dmg = base + (weap.isEmpty() ? 0f : EnchantmentHelper.getAttackDamage(weap, target.getGroup()));

                boolean hit = target.damage(getDamageSources().mobAttack(this), dmg);
                if (hit) {
                    playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
                }

                fighterAttackCooldown = 12; // ~0.6s
            }
        } else {
            // ranged fallback (should rarely be reached now)
            if (this.age % 30 == 0) {
                SnowballEntity proj = new SnowballEntity(this.getWorld(), this);
                Vec3d dir = target.getPos().add(0, target.getStandingEyeHeight(), 0)
                        .subtract(getPos().add(0, getStandingEyeHeight(), 0)).normalize();
                proj.setVelocity(dir.x, dir.y, dir.z, 1.35f, 1f);
                this.getWorld().spawnEntity(proj);
                playSound(SoundEvents.ENTITY_SNOWBALL_THROW, 1f, 0.9f + this.getWorld().random.nextFloat()*0.2f);
            }
        }
    }

    /* ------------------ miner / farmer / courier ------------------ */

    private ItemStack getEffectiveMiningTool(BlockState state) {
        ItemStack best = tool.getStack(0);
        float bestSpeed = miningSpeed(best, state);
        for (int i=0;i<cargo.size();i++) {
            ItemStack s = cargo.getStack(i);
            float sp = miningSpeed(s, state);
            if (sp > bestSpeed) { best = s; bestSpeed = sp; }
        }
        return best.isEmpty() ? new ItemStack(Items.AIR) : best;
    }
    private float miningSpeed(ItemStack s, BlockState st) {
        if (s.isEmpty()) return 1f;
        Item it = s.getItem();
        if (it instanceof MiningToolItem mti) return Math.max(1f, mti.getMiningSpeedMultiplier(s, st));
        return 1f;
    }

    // unbreakable check (bedrock + modded)
    private boolean isUnbreakable(BlockPos pos, BlockState state) {
        return state.getHardness(this.getWorld(), pos) < 0.0f;
    }

    public void beginMinerJob(List<BlockSnapshot> snapshots) {
        this.job = Job.MINER;
        this.minerTargets.clear();

        for (BlockSnapshot sn : snapshots) {
            BlockPos p = sn.pos();
            BlockState cur = this.getWorld().getBlockState(p);
            if (cur.isAir()) continue;
            if (cur.getBlock() == sn.state().getBlock() && !isUnbreakable(p, cur)) {
                this.minerTargets.add(sn);
            }
        }

        this.minerIndex = 0;
        this.mineTicks = 0;
        this.mineTicksRequired = 0;
        this.minerActive = !this.minerTargets.isEmpty();
        setJobProgress(0f);

        // ✅ If a deposit chest has been set, only deposit AFTER this run finishes
        if (this.minerDepositChest != null) this.minerWorkedSinceDepositSet = true;
    }

    private boolean isCargoNearlyFull() {
        int empties = 0;
        for (int i = 0; i < cargo.size(); i++) if (cargo.getStack(i).isEmpty()) empties++;
        return empties == 0;
    }

    // ✅ "tool" means: do NOT deposit any ToolItem from cargo during mining/farming deposit
    private static boolean isToolLike(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        Item it = s.getItem();
        return it instanceof ToolItem; // includes swords, pickaxes, axes, hoes, shovels
    }

    private boolean hasDepositableCargo() {
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack s = cargo.getStack(i);
            if (!s.isEmpty() && !isToolLike(s)) return true;
        }
        return false;
    }

    /**
     * Deposit ONLY non-tools from cargo into the given chest.
     * @return true if ANY items moved this call
     */
    private boolean depositNonToolsInto(Inventory dest) {
        boolean movedAny = false;

        for (int i = 0; i < cargo.size(); i++) {
            ItemStack s = cargo.getStack(i);
            if (s.isEmpty()) continue;

            // ✅ skip tools
            if (isToolLike(s)) continue;

            int before = s.getCount();
            ItemStack rem = insertStack(dest, s.copy());
            cargo.setStack(i, rem);

            int after = rem.isEmpty() ? 0 : rem.getCount();
            if (after < before) movedAny = true;
        }

        return movedAny;
    }

    private void tickMiner() {
        // ✅ Never deposit mid-run anymore (deposit is AFTER job completion)
        if (!minerActive) {
            if (shouldDepositMiner()) {
                attemptDepositToChest(minerDepositChest, true); // miner=true for cooldown/flags
                return;
            }

            working = false;
            if (getWorkPose() == WorkPose.MINING) setWorkPose(WorkPose.NONE);
            setJobProgress(0f);
            return;
        }

        // (Optional) keep mining even if full; extra drops will spill from insertStack(cargo, drop)
        // ✅ Removed: mid-run deposit when nearly full.

        BlockSnapshot target = null;

        while (minerIndex < minerTargets.size()) {
            BlockSnapshot sn = minerTargets.get(minerIndex);
            BlockPos p = sn.pos();
            BlockState cur = getWorld().getBlockState(p);

            if (cur.isAir() || cur.getBlock() != sn.state().getBlock()) {
                minerIndex++;
                setJobProgress(minerTargets.isEmpty() ? 0f : minerIndex / (float) minerTargets.size());
                continue;
            }

            if (isUnbreakable(p, cur)) {
                minerIndex++;
                mineTicks = 0;
                mineTicksRequired = 0;
                setJobProgress(minerTargets.isEmpty() ? 0f : minerIndex / (float) minerTargets.size());
                continue;
            }

            target = sn;
            break;
        }

        if (target == null) {
            minerActive = false;

            // ✅ After run completes, deposit if armed AND has non-tools
            if (shouldDepositMiner()) {
                attemptDepositToChest(minerDepositChest, true);
                return;
            }

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

            if (state.isAir() || state.getBlock() != target.state().getBlock() || isUnbreakable(p, state)) {
                minerIndex++;
                mineTicks = 0;
                mineTicksRequired = 0;
                setJobProgress(minerTargets.isEmpty() ? 0f : minerIndex / (float) minerTargets.size());
                return;
            }

            sw.syncWorldEvent(2001, p, Block.getRawIdFromState(state));

            ItemStack useTool = getEffectiveMiningTool(state);
            LootContextParameterSet.Builder ctx = new LootContextParameterSet.Builder(sw)
                    .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(p))
                    .add(LootContextParameters.TOOL, useTool.isEmpty() ? ItemStack.EMPTY : useTool);
            java.util.List<ItemStack> drops = state.getBlock().getDroppedStacks(state, ctx);

            sw.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

            for (ItemStack s : drops) {
                ItemStack rem = insertStack(cargo, s);
                if (!rem.isEmpty()) this.dropStack(rem); // overflow spills (deposit no longer interrupts)
            }

            sw.playSound(null, p, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.5f, 1.0f);

            minerIndex++;
            mineTicks = 0;
            mineTicksRequired = 0;
            setJobProgress(minerTargets.isEmpty() ? 0f : minerIndex / (float) minerTargets.size());
        }
    }

    private int computeMineTicks(BlockState state) {
        ItemStack t = getEffectiveMiningTool(state);
        float speed = miningSpeed(t, state);
        int base = 30;
        return Math.max(10, Math.round(base / Math.max(1f, speed)));
    }

    private boolean hasHoe() {
        if (!tool.getStack(0).isEmpty() && tool.getStack(0).getItem() instanceof HoeItem) return true;
        for (int i=0;i<cargo.size();i++) if (cargo.getStack(i).getItem() instanceof HoeItem) return true;
        return false;
    }
    private ItemStack getAnyHoe() {
        if (!tool.getStack(0).isEmpty() && tool.getStack(0).getItem() instanceof HoeItem) return tool.getStack(0);
        for (int i=0;i<cargo.size();i++) if (cargo.getStack(i).getItem() instanceof HoeItem) return cargo.getStack(i);
        return ItemStack.EMPTY;
    }

    private void tickFarmer() {
        working = false;

        boolean anyCrop = hasMatureCropNearby();

        // ✅ Deposit only after job completion (no crops) AND only if we harvested since chest was set
        if (!anyCrop && shouldDepositFarmer()) {
            attemptDepositToChest(farmerDepositChest, false); // farmer=false
            return;
        }

        if (!hasHoe()) return;
        ItemStack hoeTool = getAnyHoe();
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

                    hardStop();

                    this.getLookControl().lookAt(m.getX()+0.5, m.getY()+0.6, m.getZ()+0.5);

                    sw.syncWorldEvent(2001, m, Block.getRawIdFromState(state));
                    LootContextParameterSet.Builder ctx = new LootContextParameterSet.Builder(sw)
                            .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(m))
                            .add(LootContextParameters.TOOL, hoeTool.isEmpty() ? ItemStack.EMPTY : hoeTool);
                    java.util.List<ItemStack> drops = state.getBlock().getDroppedStacks(state, ctx);
                    sw.setBlockState(m, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

                    Item seedItem = ((CropBlockInvoker)(Object)crop).odd$getSeedsItem().asItem();
                    boolean replanted = false;

                    setTimedPose(WorkPose.REPLANT, 35);
                    working = true;

                    // ✅ mark "worked" so we can deposit AFTER the farm run ends
                    if (farmerDepositChest != null) farmerWorkedSinceDepositSet = true;

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

        // after harvesting, if now no crops remain, deposit (still respects "worked since set")
        if (!hasMatureCropNearby() && shouldDepositFarmer()) {
            attemptDepositToChest(farmerDepositChest, false);
        }
    }

    /**
     * Shared miner/farmer deposit routine:
     * - deposits ONLY non-tools from cargo
     * - if chest is full (no progress), sets a cooldown and gives up (keeps items)
     */
    private void attemptDepositToChest(@Nullable BlockPos chestPos, boolean miner) {
        if (chestPos == null) return;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // nothing to deposit (only tools) -> we're done; stop trying
        if (!hasDepositableCargo()) {
            if (miner) minerWorkedSinceDepositSet = false;
            else farmerWorkedSinceDepositSet = false;
            working = false;
            return;
        }

        flyTo(chestPos, 0.92);
        if (!getBlockPos().isWithinDistance(chestPos, 1.85)) { working = false; return; }

        hardStop();

        this.getLookControl().lookAt(chestPos.getX() + 0.5, chestPos.getY() + 0.7, chestPos.getZ() + 0.5);
        setTimedPose(WorkPose.REPLANT, 20);
        working = true;

        BlockEntity be = sw.getBlockEntity(chestPos);
        if (!(be instanceof ChestBlockEntity chest)) {
            // chest gone -> forget it
            if (miner) minerDepositChest = null;
            else farmerDepositChest = null;
            working = false;
            return;
        }

        sw.playSound(null, chestPos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.6f, 1.0f);

        boolean moved = depositNonToolsInto(chest);
        chest.markDirty();

        sw.playSound(null, chestPos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.6f, 1.0f);

        if (!moved) {
            // ✅ chest full -> give up for a while, keep items
            if (miner) minerDepositCooldown = 200;    // 10s
            else farmerDepositCooldown = 200;         // 10s
            working = false;
            return;
        }

        // if we've deposited everything depositable, stop trying until next job run
        if (!hasDepositableCargo()) {
            if (miner) minerWorkedSinceDepositSet = false;
            else farmerWorkedSinceDepositSet = false;
            working = false;
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
            depositAllCargoInto(chest); // courier deposits everything in cargo (including tools) as requested
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
    public void onDeath(DamageSource source) {
        // ✅ drop inventories on death
        dropAllInventoryContents();

        detachTravelTicket();
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // ✅ safety: some removals can bypass onDeath in weird edge cases
        if (reason == RemovalReason.KILLED) {
            dropAllInventoryContents();
        }

        detachTravelTicket();
        super.remove(reason);
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

    /* === helpers for renderer === */
    private ItemStack getDisplayedTool() {
        ItemStack t = tool.getStack(0);
        if (!t.isEmpty()) return t;
        for (int i=0;i<cargo.size();i++){
            ItemStack s = cargo.getStack(i);
            if (s.getItem() instanceof ToolItem) return s;
        }
        return ItemStack.EMPTY;
    }
}