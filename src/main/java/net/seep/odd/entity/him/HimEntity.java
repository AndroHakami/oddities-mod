package net.seep.odd.entity.him;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;

import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.quest.QuestManager;
import net.seep.odd.sound.ModSounds;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class HimEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    private static final TrackedData<Optional<UUID>> QUEST_OWNER =
            DataTracker.registerData(HimEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Boolean> BRIEF_IDLE =
            DataTracker.registerData(HimEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final double OWNER_FLEE_RANGE = 18.0D;
    private static final double HOME_WANDER_RADIUS = 15.0D;
    private static final double ESCAPE_SPEED = 1.30D;
    private static final double WANDER_SPEED = 1.12D;
    private static final double MAZE_SPEED = 1.30D;
    private static final double MAZE_REACHED_RADIUS = 2.6D;
    private static final int MAZE_SCAN_RADIUS = 48;
    private static final int MAZE_SCAN_HEIGHT = 8;
    private static final int MAZE_ROUTE_NODE_LIMIT = 18000;
    private static final int MAZE_WAYPOINT_LEAD = 5;


    private static final int[][] MAZE_APPROACH_OFFSETS = new int[][]{
            {0, 0, 0},
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {3, 0, 0}, {-3, 0, 0}, {0, 0, 3}, {0, 0, -3},
            {2, 0, 1}, {2, 0, -1}, {-2, 0, 1}, {-2, 0, -1},
            {1, 0, 2}, {-1, 0, 2}, {1, 0, -2}, {-1, 0, -2},
            {4, 0, 0}, {-4, 0, 0}, {0, 0, 4}, {0, 0, -4},
            {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {0, -1, 0}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}
    };

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private BlockPos homeCenter;
    @Nullable
    private BlockPos mazeStartMarker;
    @Nullable
    private BlockPos mazeExitMarker;
    @Nullable
    private List<BlockPos> mazeRoute;
    private int mazeRouteIndex;
    private boolean mazeRunner;
    private int idleTicks;

    public HimEntity(EntityType<? extends HimEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0F);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        MobNavigation nav = new MobNavigation(this, world);
        nav.setCanSwim(true);
        nav.setCanPathThroughDoors(true);
        nav.setCanEnterOpenDoors(true);
        return nav;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUEST_OWNER, Optional.empty());
        this.dataTracker.startTracking(BRIEF_IDLE, false);
    }

    @Nullable
    public UUID getQuestOwnerUuid() {
        return this.dataTracker.get(QUEST_OWNER).orElse(null);
    }

    @Nullable
    public ServerPlayerEntity getQuestOwner() {
        UUID uuid = this.getQuestOwnerUuid();
        if (uuid == null || this.getWorld().isClient) return null;
        return this.getWorld().getServer() == null ? null : this.getWorld().getServer().getPlayerManager().getPlayer(uuid);
    }

    public void assignQuestOwner(ServerPlayerEntity player) {
        this.dataTracker.set(QUEST_OWNER, Optional.of(player.getUuid()));
        this.setPersistent();
    }

    public void setHomeCenter(@Nullable BlockPos pos) {
        this.homeCenter = pos == null ? null : pos.toImmutable();
    }

    @Nullable
    public BlockPos getHomeCenter() {
        return this.homeCenter;
    }

    public boolean isBriefIdle() {
        return this.dataTracker.get(BRIEF_IDLE);
    }

    private void setBriefIdle(boolean value) {
        this.dataTracker.set(BRIEF_IDLE, value);
    }

    public void startBriefIdle(int ticks) {
        this.idleTicks = Math.max(this.idleTicks, ticks);
        this.setBriefIdle(this.idleTicks > 0);
        this.getNavigation().stop();
    }

    public void beginMazeRun(BlockPos startMarker, BlockPos exitMarker) {
        this.mazeRunner = true;
        this.mazeStartMarker = startMarker.toImmutable();
        this.mazeExitMarker = exitMarker.toImmutable();
        this.mazeRoute = null;
        this.mazeRouteIndex = 0;
        this.idleTicks = 0;
        this.setBriefIdle(false);
        rebuildMazeRoute();
        this.attemptMazePath(true);
    }

    public boolean isMazeRunner() {
        return this.mazeRunner;
    }

    @Nullable
    public BlockPos getMazeExitMarker() {
        return this.mazeExitMarker;
    }

    public boolean hasReachedMazeExit() {
        if (!this.mazeRunner || this.mazeExitMarker == null) return false;
        Vec3d exit = Vec3d.ofCenter(this.mazeExitMarker.up());
        return this.getPos().squaredDistanceTo(exit) <= MAZE_REACHED_RADIUS * MAZE_REACHED_RADIUS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            if (this.getQuestOwnerUuid() != null) {
                this.setPersistent();
            }
            if (this.idleTicks > 0) {
                this.idleTicks--;
                if (this.idleTicks <= 0) {
                    this.idleTicks = 0;
                    this.setBriefIdle(false);
                }
            }
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MazeRunGoal(this));
        this.goalSelector.add(2, new FleeQuestOwnerGoal(this));
        this.goalSelector.add(3, new RoamNervouslyGoal(this));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
        this.goalSelector.add(5, new LookAroundGoal(this));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) return true;
        if (source.getAttacker() instanceof PlayerEntity) {
            this.playSound(SoundEvents.ENTITY_ALLAY_HURT, 0.85F, 1.15F);
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.HIM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ALLAY_DEATH;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 40;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.getQuestOwnerUuid() != null) {
            nbt.putUuid("QuestOwner", this.getQuestOwnerUuid());
        }
        if (this.homeCenter != null) {
            nbt.putInt("HomeX", this.homeCenter.getX());
            nbt.putInt("HomeY", this.homeCenter.getY());
            nbt.putInt("HomeZ", this.homeCenter.getZ());
        }
        if (this.mazeStartMarker != null) {
            nbt.putInt("MazeStartX", this.mazeStartMarker.getX());
            nbt.putInt("MazeStartY", this.mazeStartMarker.getY());
            nbt.putInt("MazeStartZ", this.mazeStartMarker.getZ());
        }
        if (this.mazeExitMarker != null) {
            nbt.putInt("MazeExitX", this.mazeExitMarker.getX());
            nbt.putInt("MazeExitY", this.mazeExitMarker.getY());
            nbt.putInt("MazeExitZ", this.mazeExitMarker.getZ());
        }
        nbt.putBoolean("MazeRunner", this.mazeRunner);
        nbt.putInt("IdleTicks", this.idleTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("QuestOwner")) {
            this.dataTracker.set(QUEST_OWNER, Optional.of(nbt.getUuid("QuestOwner")));
            this.setPersistent();
        }
        if (nbt.contains("HomeX") && nbt.contains("HomeY") && nbt.contains("HomeZ")) {
            this.homeCenter = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
        } else {
            this.homeCenter = this.getBlockPos();
        }
        if (nbt.contains("MazeStartX") && nbt.contains("MazeStartY") && nbt.contains("MazeStartZ")) {
            this.mazeStartMarker = new BlockPos(nbt.getInt("MazeStartX"), nbt.getInt("MazeStartY"), nbt.getInt("MazeStartZ"));
        }
        if (nbt.contains("MazeExitX") && nbt.contains("MazeExitY") && nbt.contains("MazeExitZ")) {
            this.mazeExitMarker = new BlockPos(nbt.getInt("MazeExitX"), nbt.getInt("MazeExitY"), nbt.getInt("MazeExitZ"));
        }
        this.mazeRunner = nbt.getBoolean("MazeRunner");
        this.mazeRoute = null;
        this.mazeRouteIndex = 0;
        this.idleTicks = Math.max(0, nbt.getInt("IdleTicks"));
        this.setBriefIdle(this.idleTicks > 0 && !this.mazeRunner);
        if (this.mazeRunner && !this.getWorld().isClient) {
            rebuildMazeRoute();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "him.controller", 0, state -> {
            if (!this.mazeRunner && this.isBriefIdle()) {
                state.setAndContinue(IDLE);
                return PlayState.CONTINUE;
            }
            state.setAndContinue(RUN);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private boolean shouldFleeOwner() {
        if (this.mazeRunner) return false;
        ServerPlayerEntity owner = this.getQuestOwner();
        return owner != null && owner.isAlive() && this.squaredDistanceTo(owner) <= OWNER_FLEE_RANGE * OWNER_FLEE_RANGE;
    }

    private boolean moveAwayFrom(ServerPlayerEntity owner, double distance, double speed) {
        Vec3d away = this.getPos().subtract(owner.getPos());
        if (away.lengthSquared() < 0.001D) {
            float yaw = this.random.nextFloat() * (float) (Math.PI * 2.0D);
            away = new Vec3d(MathHelper.cos(yaw), 0.0D, MathHelper.sin(yaw));
        }
        away = away.normalize();

        for (int i = 0; i < 8; i++) {
            double yawOffset = (this.random.nextDouble() - 0.5D) * Math.toRadians(70.0D);
            double cos = Math.cos(yawOffset);
            double sin = Math.sin(yawOffset);
            double rx = away.x * cos - away.z * sin;
            double rz = away.x * sin + away.z * cos;

            double chosenDistance = distance + this.random.nextDouble() * 5.0D;
            int x = MathHelper.floor(this.getX() + rx * chosenDistance);
            int z = MathHelper.floor(this.getZ() + rz * chosenDistance);
            BlockPos floor = QuestManager.findNearestFloor(this.getWorld(), new BlockPos(x, this.getBlockY() + 5, z));
            if (floor == null) continue;
            if (this.getNavigation().findPathTo(floor, 0) == null) continue;
            this.getNavigation().startMovingTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D, speed);
            return true;
        }
        return false;
    }

    private boolean moveAroundHome(double speed) {
        BlockPos center = this.homeCenter == null ? this.getBlockPos() : this.homeCenter;
        for (int i = 0; i < 8; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = 5.0D + this.random.nextDouble() * HOME_WANDER_RADIUS;
            int x = center.getX() + MathHelper.floor(Math.cos(angle) * distance);
            int z = center.getZ() + MathHelper.floor(Math.sin(angle) * distance);
            BlockPos floor = QuestManager.findNearestFloor(this.getWorld(), new BlockPos(x, center.getY() + 5, z));
            if (floor == null) continue;
            if (this.squaredDistanceTo(Vec3d.ofCenter(floor)) < 4.0D) continue;
            if (this.getNavigation().findPathTo(floor, 0) == null) continue;
            this.getNavigation().startMovingTo(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D, speed);
            return true;
        }
        return false;
    }

    private boolean attemptMazePath(boolean startMoving) {
        if (this.mazeExitMarker == null) {
            return false;
        }

        if (this.mazeRoute == null || this.mazeRoute.isEmpty()) {
            rebuildMazeRoute();
        }

        if (this.mazeRoute != null && !this.mazeRoute.isEmpty()) {
            syncMazeRouteIndex();
            BlockPos waypoint = selectMazeWaypoint();
            if (waypoint != null) {
                if (startMoving) {
                    Path path = this.getNavigation().findPathTo(waypoint, 0);
                    if (path != null) {
                        this.getNavigation().startMovingAlong(path, MAZE_SPEED);
                    } else {
                        this.getNavigation().startMovingTo(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D, MAZE_SPEED);
                    }
                }
                return true;
            }
        }

        return attemptDirectMazePath(startMoving);
    }

    private boolean attemptDirectMazePath(boolean startMoving) {
        if (this.mazeExitMarker == null) {
            return false;
        }

        BlockPos exactFeet = this.mazeExitMarker.up();
        Path bestPath = null;
        BlockPos bestFeet = null;
        double bestScore = Double.MAX_VALUE;

        for (int[] off : MAZE_APPROACH_OFFSETS) {
            BlockPos candidateFeet = exactFeet.add(off[0], off[1], off[2]);
            if (!isMazeStandCandidate(candidateFeet)) {
                continue;
            }

            Path path = this.getNavigation().findPathTo(candidateFeet, 0);
            if (path == null) {
                continue;
            }

            double score = candidateFeet.getSquaredDistance(exactFeet) * 3.0D
                    + candidateFeet.getSquaredDistance(this.getBlockPos());
            if (score < bestScore) {
                bestScore = score;
                bestPath = path;
                bestFeet = candidateFeet;
                if (off[0] == 0 && off[1] == 0 && off[2] == 0) {
                    break;
                }
            }
        }

        if (bestFeet != null) {
            this.mazeRoute = new ArrayList<>();
            this.mazeRoute.add(this.getBlockPos());
            this.mazeRoute.add(bestFeet.toImmutable());
            this.mazeRouteIndex = 1;
        }

        if (bestPath != null) {
            if (startMoving) {
                this.getNavigation().startMovingAlong(bestPath, MAZE_SPEED);
            }
            return true;
        }

        return false;
    }

    private void rebuildMazeRoute() {
        if (this.mazeExitMarker == null) {
            this.mazeRoute = null;
            this.mazeRouteIndex = 0;
            return;
        }

        BlockPos startFeet = findNearestMazeStandPos(this.getBlockPos(), 3);
        if (startFeet == null && this.mazeStartMarker != null) {
            startFeet = findNearestMazeStandPos(this.mazeStartMarker.up(), 4);
        }
        if (startFeet == null) {
            startFeet = this.getBlockPos();
        }

        BlockPos goalFeet = pickBestMazeGoalFeet(startFeet);
        if (goalFeet == null) {
            this.mazeRoute = null;
            this.mazeRouteIndex = 0;
            return;
        }

        List<BlockPos> route = buildMazeRoute(startFeet, goalFeet);
        if (route == null || route.isEmpty()) {
            this.mazeRoute = null;
            this.mazeRouteIndex = 0;
            return;
        }

        this.mazeRoute = route;
        this.mazeRouteIndex = 0;
        syncMazeRouteIndex();
    }

    @Nullable
    private BlockPos pickBestMazeGoalFeet(BlockPos startFeet) {
        if (this.mazeExitMarker == null) return null;

        BlockPos exactFeet = this.mazeExitMarker.up();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int[] off : MAZE_APPROACH_OFFSETS) {
            BlockPos candidate = exactFeet.add(off[0], off[1], off[2]);
            if (!isMazeStandCandidate(candidate)) {
                continue;
            }

            double score = candidate.getSquaredDistance(exactFeet) * 4.0D
                    + candidate.getSquaredDistance(startFeet) * 0.15D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate.toImmutable();
            }
        }

        return best != null ? best : exactFeet;
    }

    @Nullable
    private List<BlockPos> buildMazeRoute(BlockPos startFeet, BlockPos goalFeet) {
        if (startFeet.equals(goalFeet)) {
            List<BlockPos> single = new ArrayList<>();
            single.add(goalFeet.toImmutable());
            return single;
        }

        int minX = Math.min(startFeet.getX(), goalFeet.getX()) - MAZE_SCAN_RADIUS;
        int maxX = Math.max(startFeet.getX(), goalFeet.getX()) + MAZE_SCAN_RADIUS;
        int minY = Math.min(startFeet.getY(), goalFeet.getY()) - MAZE_SCAN_HEIGHT;
        int maxY = Math.max(startFeet.getY(), goalFeet.getY()) + MAZE_SCAN_HEIGHT;
        int minZ = Math.min(startFeet.getZ(), goalFeet.getZ()) - MAZE_SCAN_RADIUS;
        int maxZ = Math.max(startFeet.getZ(), goalFeet.getZ()) + MAZE_SCAN_RADIUS;

        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        open.add(startFeet);
        cameFrom.put(startFeet, startFeet);

        BlockPos reached = null;
        int explored = 0;

        while (!open.isEmpty() && explored < MAZE_ROUTE_NODE_LIMIT) {
            BlockPos current = open.removeFirst();
            explored++;

            if (current.equals(goalFeet) || current.getSquaredDistance(goalFeet) <= 1.25D) {
                reached = current;
                break;
            }

            for (Direction dir : orderedDirections(current, goalFeet)) {
                BlockPos next = stepMazeNeighbor(current, dir);
                if (next == null) continue;
                if (next.getX() < minX || next.getX() > maxX || next.getY() < minY || next.getY() > maxY || next.getZ() < minZ || next.getZ() > maxZ) {
                    continue;
                }
                if (cameFrom.containsKey(next)) {
                    continue;
                }
                cameFrom.put(next, current);
                open.addLast(next);
            }
        }

        if (reached == null) {
            return null;
        }

        ArrayList<BlockPos> out = new ArrayList<>();
        BlockPos cursor = reached;
        while (true) {
            out.add(0, cursor.toImmutable());
            BlockPos prev = cameFrom.get(cursor);
            if (prev == null || prev.equals(cursor)) {
                break;
            }
            cursor = prev;
        }
        return out;
    }

    private void syncMazeRouteIndex() {
        if (this.mazeRoute == null || this.mazeRoute.isEmpty()) {
            this.mazeRouteIndex = 0;
            return;
        }

        int bestIndex = this.mazeRouteIndex;
        double bestDist = Double.MAX_VALUE;
        int from = Math.max(0, this.mazeRouteIndex - 4);
        int to = Math.min(this.mazeRoute.size() - 1, this.mazeRouteIndex + 16);

        for (int i = from; i <= to; i++) {
            BlockPos step = this.mazeRoute.get(i);
            double dist = this.getPos().squaredDistanceTo(step.getX() + 0.5D, step.getY(), step.getZ() + 0.5D);
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
            }
        }

        while (bestIndex + 1 < this.mazeRoute.size()) {
            BlockPos step = this.mazeRoute.get(bestIndex);
            double dist = this.getPos().squaredDistanceTo(step.getX() + 0.5D, step.getY(), step.getZ() + 0.5D);
            if (dist > 2.25D) {
                break;
            }
            bestIndex++;
        }

        this.mazeRouteIndex = MathHelper.clamp(bestIndex, 0, this.mazeRoute.size() - 1);
    }

    @Nullable
    private BlockPos selectMazeWaypoint() {
        if (this.mazeRoute == null || this.mazeRoute.isEmpty()) {
            return null;
        }

        int targetIndex = Math.min(this.mazeRoute.size() - 1, this.mazeRouteIndex + MAZE_WAYPOINT_LEAD);
        for (int i = targetIndex; i >= this.mazeRouteIndex; i--) {
            BlockPos candidate = this.mazeRoute.get(i);
            if (isMazeStandCandidate(candidate)) {
                return candidate;
            }
        }
        return this.mazeRoute.get(MathHelper.clamp(this.mazeRouteIndex, 0, this.mazeRoute.size() - 1));
    }

    @Nullable
    private BlockPos findNearestMazeStandPos(BlockPos center, int radius) {
        int[] yOffsets = new int[]{0, 1, -1, 2, -2};
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 0; r <= radius; r++) {
            for (int yOff : yOffsets) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos feet = center.add(dx, yOff, dz);
                        if (!isMazeStandCandidate(feet)) {
                            continue;
                        }
                        double score = feet.getSquaredDistance(center) + Math.abs(yOff) * 2.0D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = feet.toImmutable();
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos stepMazeNeighbor(BlockPos currentFeet, Direction dir) {
        BlockPos forward = currentFeet.offset(dir);
        if (isMazeStandCandidate(forward)) {
            return forward.toImmutable();
        }
        if (isMazeStandCandidate(forward.up())) {
            return forward.up().toImmutable();
        }
        if (isMazeStandCandidate(forward.down())) {
            return forward.down().toImmutable();
        }
        return null;
    }

    private Direction[] orderedDirections(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);

        Direction xDir = dx >= 0 ? Direction.EAST : Direction.WEST;
        Direction zDir = dz >= 0 ? Direction.SOUTH : Direction.NORTH;

        if (xFirst) {
            return new Direction[]{xDir, zDir, xDir.getOpposite(), zDir.getOpposite()};
        }
        return new Direction[]{zDir, xDir, zDir.getOpposite(), xDir.getOpposite()};
    }

    private boolean isMazeStandCandidate(BlockPos feet) {
        if (!this.getWorld().isInBuildLimit(feet) || !this.getWorld().isInBuildLimit(feet.up()) || !this.getWorld().isInBuildLimit(feet.down())) {
            return false;
        }

        BlockState floor = this.getWorld().getBlockState(feet.down());
        if (floor.isAir()) {
            return false;
        }
        if (!this.getWorld().getBlockState(feet).isAir()) {
            return false;
        }
        if (!this.getWorld().getBlockState(feet.up()).isAir()) {
            return false;
        }
        return floor.isSideSolidFullSquare(this.getWorld(), feet.down(), Direction.UP) || floor.blocksMovement();
    }

    static final class MazeRunGoal extends Goal {
        private final HimEntity him;
        private int repathCooldown;
        private int stuckTicks;
        private Vec3d lastPos = Vec3d.ZERO;

        MazeRunGoal(HimEntity him) {
            this.him = him;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return this.him.mazeRunner && this.him.mazeExitMarker != null;
        }

        @Override
        public boolean shouldContinue() {
            return this.him.mazeRunner && this.him.mazeExitMarker != null && !this.him.hasReachedMazeExit();
        }

        @Override
        public void start() {
            this.repathCooldown = 0;
            this.stuckTicks = 0;
            this.lastPos = this.him.getPos();
            this.him.setBriefIdle(false);
            this.him.idleTicks = 0;
            this.him.rebuildMazeRoute();
            this.him.attemptMazePath(true);
        }

        @Override
        public void tick() {
            if (this.him.mazeExitMarker == null) {
                return;
            }

            this.him.getLookControl().lookAt(
                    this.him.mazeExitMarker.getX() + 0.5D,
                    this.him.getEyeY(),
                    this.him.mazeExitMarker.getZ() + 0.5D,
                    20.0F,
                    this.him.getMaxLookPitchChange()
            );

            if (this.repathCooldown > 0) {
                this.repathCooldown--;
            }

            double movedSq = this.him.getPos().squaredDistanceTo(this.lastPos);
            if (movedSq < 0.02D) {
                this.stuckTicks++;
            } else {
                this.stuckTicks = 0;
                this.lastPos = this.him.getPos();
            }

            boolean hardRebuild = this.stuckTicks > 12;
            if (hardRebuild) {
                this.him.rebuildMazeRoute();
            }

            if (this.him.getNavigation().isIdle() || this.repathCooldown <= 0 || hardRebuild) {
                boolean found = this.him.attemptMazePath(true);
                this.repathCooldown = hardRebuild ? 2 : 6;

                if (!found && hardRebuild) {
                    Vec3d nudge = Vec3d.ofCenter(this.him.mazeExitMarker).subtract(this.him.getPos());
                    nudge = new Vec3d(nudge.x, 0.0D, nudge.z);
                    if (nudge.lengthSquared() > 0.0001D) {
                        nudge = nudge.normalize().multiply(0.16D);
                        this.him.addVelocity(nudge.x, 0.12D, nudge.z);
                        this.him.velocityDirty = true;
                    }
                }

                if (hardRebuild) {
                    this.stuckTicks = 0;
                    this.lastPos = this.him.getPos();
                }
            }
        }

        @Override
        public void stop() {
            this.him.getNavigation().stop();
            this.stuckTicks = 0;
            this.repathCooldown = 0;
        }
    }

    static final class FleeQuestOwnerGoal extends Goal {
        private final HimEntity him;
        private int repathCooldown;

        FleeQuestOwnerGoal(HimEntity him) {
            this.him = him;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return this.him.shouldFleeOwner();
        }

        @Override
        public boolean shouldContinue() {
            return this.him.shouldFleeOwner();
        }

        @Override
        public void start() {
            this.repathCooldown = 0;
            this.him.setBriefIdle(false);
            this.him.idleTicks = 0;
            tick();
        }

        @Override
        public void tick() {
            ServerPlayerEntity owner = this.him.getQuestOwner();
            if (owner == null) return;

            this.him.getLookControl().lookAt(
                    this.him.getX() + (this.him.getX() - owner.getX()),
                    this.him.getEyeY(),
                    this.him.getZ() + (this.him.getZ() - owner.getZ())
            );

            if (this.repathCooldown > 0) {
                this.repathCooldown--;
            }

            if (this.repathCooldown <= 0 || this.him.getNavigation().isIdle() || this.him.squaredDistanceTo(owner) < 36.0D) {
                this.him.moveAwayFrom(owner, 8.0D, ESCAPE_SPEED);
                this.repathCooldown = 6;
            }
        }

        @Override
        public void stop() {
            this.repathCooldown = 0;
        }
    }

    static final class RoamNervouslyGoal extends Goal {
        private final HimEntity him;
        private int repathCooldown;

        RoamNervouslyGoal(HimEntity him) {
            this.him = him;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return !this.him.mazeRunner && !this.him.shouldFleeOwner();
        }

        @Override
        public boolean shouldContinue() {
            return !this.him.mazeRunner && !this.him.shouldFleeOwner();
        }

        @Override
        public void start() {
            this.repathCooldown = 0;
        }

        @Override
        public void tick() {
            if (this.him.idleTicks > 0) return;

            if (this.repathCooldown > 0) {
                this.repathCooldown--;
            }

            if (this.him.getNavigation().isIdle()) {
                if (this.him.random.nextInt(8) == 0) {
                    this.him.startBriefIdle(10 + this.him.random.nextInt(16));
                    this.repathCooldown = 0;
                    return;
                }
                this.him.setBriefIdle(false);
                this.him.moveAroundHome(WANDER_SPEED);
                this.repathCooldown = 6;
            } else if (this.repathCooldown <= 0) {
                this.him.setBriefIdle(false);
                this.him.moveAroundHome(WANDER_SPEED);
                this.repathCooldown = 12;
            }
        }

        @Override
        public void stop() {
            this.him.setBriefIdle(false);
            if (this.him.idleTicks <= 0) {
                this.him.getNavigation().stop();
            }
        }
    }
}
