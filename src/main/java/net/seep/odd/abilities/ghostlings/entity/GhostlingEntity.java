package net.seep.odd.abilities.ghostlings.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

// invoker for protected CropBlock#getSeedsItem()
import net.seep.odd.mixin.access.CropBlockInvoker;
import net.minecraft.item.ItemConvertible;

public class GhostlingEntity extends PathAwareEntity implements GeoEntity {
    public enum Job { NONE, FIGHTER, GATHERER, FARMER, COURIER }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Job job = Job.NONE;
    private UUID owner;

    private boolean working = false;
    private BlockPos workOrigin = null;
    private boolean stayWithinRange = false;
    private static final int STAY_RANGE = 16;

    private final SimpleInventory tool  = new SimpleInventory(1);
    private final SimpleInventory cargo = new SimpleInventory(9);

    private int gatherQuota = 0;
    private Optional<BlockPos> targetBlock = Optional.empty();
    private String gatherSampleItemId = "";

    private BlockPos courierTarget = null;

    public GhostlingEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setCanPickUpLoot(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4);
    }

    public boolean isOwner(UUID uuid) { return uuid != null && uuid.equals(owner); }
    public void setOwner(UUID uuid) { this.owner = uuid; }
    public boolean isWorking() { return working; }
    public Job getJob() { return job; }

    public void setWorkOrigin(BlockPos pos) { this.workOrigin = pos; }
    public void toggleStayWithinRange() { this.stayWithinRange = !this.stayWithinRange; }
    public void setCourierTarget(BlockPos pos) { this.courierTarget = pos; }

    public void setGatherSample(String itemId) {
        this.gatherSampleItemId = itemId == null ? "" : itemId;
        this.gatherQuota = 2; // per spec
        this.targetBlock = Optional.empty();
    }

    @Override
    protected void initGoals() {
        // Work goal now only runs when there is actual work (see canStart/shouldContinue)
        this.goalSelector.add(2, new GhostWorkGoal(this));
        this.goalSelector.add(7, new WanderAroundGoal(this, 1.0));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        ItemStack stack = player.getStackInHand(hand);

        if (stack.isOf(Items.GHAST_TEAR) || stack.isOf(Items.PHANTOM_MEMBRANE)) {
            this.heal(12f);
            if (!player.getAbilities().creativeMode) stack.decrement(1);
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, getX(), getY()+1.2, getZ(), 6, 0.3,0.3,0.3, 0.01);
            }
            playSound(SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 1.4f);
            return ActionResult.CONSUME;
        }

        if (job == Job.NONE) {
            Job chosen = toolToJob(stack);
            if (chosen != Job.NONE) {
                job = chosen;
                tool.setStack(0, stack.copyWithCount(1));
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                player.sendMessage(Text.literal("Assigned job: " + job), true);
                return ActionResult.SUCCESS;
            }
        } else if (job == Job.GATHERER) {
            String id = getItemId(stack.getItem());
            if (!id.isEmpty()) {
                setGatherSample(id);
                player.sendMessage(Text.literal("Gather sample set: " + id + " (quota 2)"), true);
                return ActionResult.SUCCESS;
            }
        }

        player.openHandledScreen(getManageFactory());
        return ActionResult.SUCCESS;
    }

    private String getItemId(Item item) {
        var id = net.minecraft.registry.Registries.ITEM.getId(item);
        return id == null ? "" : id.toString();
    }

    private Job toolToJob(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem) return Job.FIGHTER;
        if (stack.getItem() instanceof PickaxeItem || stack.getItem() instanceof AxeItem) return Job.GATHERER;
        if (stack.getItem() instanceof HoeItem) return Job.FARMER;
        if (stack.isOf(Items.CHEST)) return Job.COURIER;
        return Job.NONE;
    }

    public NamedScreenHandlerFactory getManageFactory() { return new GhostManageFactory(this); }

    // ── GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "main", 5, state -> {
            if (this.isDead()) return PlayState.STOP;

            if (this.working) {
                return state.setAndContinue(switch (job) {
                    case FIGHTER -> RawAnimation.begin().then("attack", Animation.LoopType.PLAY_ONCE);
                    case GATHERER -> RawAnimation.begin().then("mine", Animation.LoopType.PLAY_ONCE);
                    case FARMER   -> RawAnimation.begin().then("replant", Animation.LoopType.LOOP);
                    case COURIER  -> RawAnimation.begin().then("move", Animation.LoopType.LOOP);
                    default       -> RawAnimation.begin().then("idle", Animation.LoopType.LOOP);
                });
            }

            if (state.isMoving())
                return state.setAndContinue(RawAnimation.begin().then("move", Animation.LoopType.LOOP));
            return state.setAndContinue(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));
        }));

    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("Job", job.name());
        if (workOrigin != null) nbt.putLong("WorkOrigin", workOrigin.asLong());
        nbt.putBoolean("StayRange", stayWithinRange);
        nbt.putInt("GatherQuota", gatherQuota);
        nbt.putString("GatherSample", gatherSampleItemId);
        if (courierTarget != null) nbt.putLong("CourierTarget", courierTarget.asLong());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
        if (nbt.contains("Job")) job = Job.valueOf(nbt.getString("Job"));
        workOrigin = nbt.contains("WorkOrigin") ? BlockPos.fromLong(nbt.getLong("WorkOrigin")) : null;
        stayWithinRange = nbt.getBoolean("StayRange");
        gatherQuota = nbt.getInt("GatherQuota");
        gatherSampleItemId = nbt.getString("GatherSample");
        courierTarget = nbt.contains("CourierTarget") ? BlockPos.fromLong(nbt.getLong("CourierTarget")) : null;
    }

    /* ===================== work driver ===================== */

    // New: tell the goal system if we have something to do
    boolean hasWorkToDo() {
        if (stayWithinRange && workOrigin != null && !getBlockPos().isWithinDistance(workOrigin, STAY_RANGE)) {
            return true;
        }
        return switch (job) {
            case FIGHTER -> findFighterTarget() != null;
            case GATHERER -> {
                if (gatherQuota <= 0) yield false;
                if (targetBlock.isPresent() && isValidTarget(targetBlock.get())) yield true;
                targetBlock = GhostlingGatherables.findNearestTarget(this, gatherSampleItemId, workOrigin);
                yield targetBlock.isPresent();
            }
            case FARMER -> hasMatureCropNearby();
            case COURIER -> courierTarget != null && (!getBlockPos().isWithinDistance(courierTarget, 2.0) || !isCargoEmpty());
            default -> false;
        };
    }

    private boolean hasMatureCropNearby() {
        BlockPos center = (workOrigin != null) ? workOrigin : getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx=-4; dx<=4; dx++) for (int dz=-4; dz<=4; dz++) for (int dy=-1; dy<=1; dy++) {
            m.set(center.getX()+dx, center.getY()+dy, center.getZ()+dz);
            BlockState s = this.getWorld().getBlockState(m);
            if (s.getBlock() instanceof CropBlock crop && crop.isMature(s)) return true;
        }
        return false;
    }

    private boolean isCargoEmpty() {
        for (int i=0;i<cargo.size();i++) if (!cargo.getStack(i).isEmpty()) return false;
        return true;
    }

    private LivingEntity findFighterTarget() {
        return this.getWorld().getClosestEntity(
                LivingEntity.class, TargetPredicate.DEFAULT, this,
                getX(), getY(), getZ(), getBoundingBox().expand(10));
    }

    void tickWork() {
        if (stayWithinRange && workOrigin != null) {
            if (!getBlockPos().isWithinDistance(workOrigin, STAY_RANGE)) {
                getNavigation().startMovingTo(workOrigin.getX()+0.5, workOrigin.getY()+1, workOrigin.getZ()+0.5, 1.15);
                working = true;
                return;
            }
        }

        switch (job) {
            case FIGHTER -> tickFighter();
            case GATHERER -> tickGatherer();
            case FARMER   -> tickFarmer();
            case COURIER  -> tickCourier();
            default       -> working = false;
        }
    }

    private void tickFighter() {
        working = false;
        PlayerEntity ownerPlayer = getOwnerPlayer();
        if (ownerPlayer == null) return;

        LivingEntity target = findFighterTarget();

        if (target != null && target != ownerPlayer && target != this && target.isAlive() && target.isAttackable()) {
            working = true;
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

    private void tickGatherer() {
        if (gatherQuota <= 0) { working = false; return; }
        working = true;

        if (targetBlock.isEmpty() || !isValidTarget(targetBlock.get())) {
            targetBlock = GhostlingGatherables.findNearestTarget(this, gatherSampleItemId, workOrigin);
            if (targetBlock.isEmpty()) { working = false; return; }
        }

        BlockPos pos = targetBlock.get();
        getNavigation().startMovingTo(pos.getX()+0.5, pos.getY()+1, pos.getZ()+0.5, 1.1);
        if (getBlockPos().isWithinDistance(pos, 2.4)) {
            BlockState bs = this.getWorld().getBlockState(pos);
            if (!bs.isAir()) {
                this.getWorld().syncWorldEvent(2001, pos, Block.getRawIdFromState(bs));
                this.getWorld().breakBlock(pos, true, this);
                playSound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 1.2f);
                gatherQuota--;
                targetBlock = Optional.empty();
            }
        }
    }

    private boolean isValidTarget(BlockPos pos) {
        return GhostlingGatherables.matchesTarget(this.getWorld().getBlockState(pos), gatherSampleItemId);
    }

    private void tickFarmer() {
        working = false;
        BlockPos center = (workOrigin != null) ? workOrigin : getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx=-4; dx<=4; dx++) for (int dz=-4; dz<=4; dz++) for (int dy=-1; dy<=1; dy++) {
            m.set(center.getX()+dx, center.getY()+dy, center.getZ()+dz);
            BlockState state = this.getWorld().getBlockState(m);
            if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
                working = true;
                getNavigation().startMovingTo(m.getX()+0.5, m.getY()+1, m.getZ()+0.5, 1.0);
                if (getBlockPos().isWithinDistance(m, 2.0)) {
                    this.getWorld().syncWorldEvent(2001, m, Block.getRawIdFromState(state));
                    this.getWorld().breakBlock(m, true, this);

                    ItemConvertible seedConv = ((CropBlockInvoker)(Object)crop).odd$getSeedsItem();
                    Item seedItem = seedConv.asItem();

                    for (int i=0;i<cargo.size();i++) {
                        ItemStack s = cargo.getStack(i);
                        if (!s.isEmpty() && s.isOf(seedItem)) {
                            this.getWorld().setBlockState(m, crop.getDefaultState());
                            s.decrement(1);
                            break;
                        }
                    }
                    return;
                }
            }
        }
    }

    private void tickCourier() {
        if (courierTarget == null) { working = false; return; }
        working = true;
        getNavigation().startMovingTo(courierTarget.getX()+0.5, courierTarget.getY()+1, courierTarget.getZ()+0.5, 1.05);
        if (getBlockPos().isWithinDistance(courierTarget, 2.0)) {
            BlockEntity be = this.getWorld().getBlockEntity(courierTarget);
            if (be instanceof ChestBlockEntity chest) {
                for (int i=0;i<cargo.size();i++) {
                    ItemStack s = cargo.getStack(i);
                    if (!s.isEmpty()) {
                        for (int slot=0; slot<chest.size(); slot++) {
                            if (chest.getStack(slot).isEmpty()) {
                                chest.setStack(slot, s.copy());
                                cargo.setStack(i, ItemStack.EMPTY);
                                break;
                            }
                        }
                    }
                }
                chest.markDirty();
            } else {
                for (int i=0;i<cargo.size();i++) {
                    ItemStack s = cargo.getStack(i);
                    if (!s.isEmpty()) { dropStack(s); cargo.setStack(i, ItemStack.EMPTY); }
                }
            }
            working = false; // idle at location
        }
    }

    @Nullable
    private PlayerEntity getOwnerPlayer() {
        World w = this.getWorld();
        if (owner == null || !(w instanceof ServerWorld sw)) return null;
        return sw.getServer().getPlayerManager().getPlayer(owner);
    }

    // goal impl
    private static class GhostWorkGoal extends Goal {
        private final GhostlingEntity g;
        GhostWorkGoal(GhostlingEntity g) { this.g = g; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() { return g.hasWorkToDo(); }
        @Override public boolean shouldContinue() { return g.hasWorkToDo(); }
        @Override public void tick() { g.tickWork(); }
    }
}
