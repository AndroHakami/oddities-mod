package net.seep.odd.entity.cultist;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.status.ModStatusEffects;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class WeepingAngelEntity extends PathAwareEntity {

    /* ---------- Synced: disguise blockstate (raw id) ---------- */
    private static final net.minecraft.entity.data.TrackedData<Integer> DISGUISE_RAW =
            net.minecraft.entity.data.DataTracker.registerData(WeepingAngelEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);

    private static final net.minecraft.entity.data.TrackedData<BlockPos> HOME_POS =
            net.minecraft.entity.data.DataTracker.registerData(WeepingAngelEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BLOCK_POS);
    private static final net.minecraft.entity.data.TrackedData<Boolean> HOME_SET =
            net.minecraft.entity.data.DataTracker.registerData(WeepingAngelEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);

    /* ---------- Tuning ---------- */
    private static final int LOOK_RANGE = 32;
    private static final int TARGET_RANGE = 28;

    private static final double CHASE_NAV_SPEED = 2.35;   // fast when not observed
    private static final double PARK_NAV_SPEED  = 1.10;

    private static final int DAMAGE_INTERVAL = 4;          // every 4 ticks
    private static final float DAMAGE_AMOUNT = 2.0F;       // 1 heart

    private static final int PARK_RADIUS = 14;

    /* ---------- Server-only state ---------- */
    @Nullable private UUID targetUuid = null;
    @Nullable private BlockPos parkPos = null;

    private int dmgTicker = 0;

    public WeepingAngelEntity(EntityType<? extends WeepingAngelEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23D) // base; nav speed is in startMovingTo
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.80D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        // default disguise if something goes wrong
        this.dataTracker.startTracking(DISGUISE_RAW, Block.getRawIdFromState(Blocks.DIRT.getDefaultState()));
        this.dataTracker.startTracking(HOME_POS, BlockPos.ORIGIN);
        this.dataTracker.startTracking(HOME_SET, false);
    }

    public void setHomePos(BlockPos pos) {
        this.dataTracker.set(HOME_POS, pos);
        this.dataTracker.set(HOME_SET, true);
    }

    public BlockPos getHomePos() {
        return this.dataTracker.get(HOME_SET) ? this.dataTracker.get(HOME_POS) : this.getBlockPos();
    }

    public BlockState getDisguiseState() {
        return Block.getStateFromRawId(this.dataTracker.get(DISGUISE_RAW));
    }

    private void setDisguiseState(BlockState state) {
        if (state == null) return;
        this.dataTracker.set(DISGUISE_RAW, Block.getRawIdFromState(state));
    }

    /**
     * Call this ONCE right after creation (before spawning),
     * after the soul sand/head are consumed. This locks disguise forever.
     *
     * @param buildBase the base position where the soul sand was (entity spawns here)
     */
    public void initCreationDisguise(ServerWorld sw, BlockPos buildBase) {
        // 1) Prefer the block UNDER the construct (what you asked for)
        BlockState under = sw.getBlockState(buildBase.down());
        BlockState chosen = isGoodDisguise(under) ? under : null;

        // 2) Fallback: search nearby blocks to blend (but still lock permanently)
        if (chosen == null) {
            chosen = findNearbyDisguise(sw, buildBase);
        }

        // 3) Final fallback: dirt
        if (chosen == null) chosen = Blocks.DIRT.getDefaultState();

        setDisguiseState(chosen);

        // choose a parking spot near walls/corners; prefer spots near blocks matching disguise
        parkPos = findBestParkingSpot(sw, buildBase, chosen);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new CoreGoal(this));
    }

    static final class CoreGoal extends Goal {
        private final WeepingAngelEntity mob;

        CoreGoal(WeepingAngelEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() { return true; }
        @Override public boolean shouldContinue() { return true; }

        @Override
        public void tick() {
            if (!mob.getWorld().isClient) mob.serverBrainTick();
        }
    }

    private void hardFreeze() {
        this.getNavigation().stop();
        Vec3d v = this.getVelocity();
        // kill horizontal drift (no sliding)
        if (v.x != 0.0 || v.z != 0.0) {
            this.setVelocity(0.0, v.y, 0.0);
        }
    }

    private void serverBrainTick() {
        // If ANY valid player is looking -> freeze completely.
        if (isObservedByAnyPlayer()) {
            hardFreeze();
            dmgTicker = 0;
            return;
        }

        // Not observed: chase-only behavior
        ServerPlayerEntity target = getTargetPlayer();

        if (target == null) {
            // acquire nearest valid victim
            ServerPlayerEntity newTarget = findNearestVictim(TARGET_RANGE);
            if (newTarget != null) {
                targetUuid = newTarget.getUuid();
                target = newTarget;
            }
        } else {
            // validate target
            if (!isValidVictim(target) || this.squaredDistanceTo(target) > (TARGET_RANGE * TARGET_RANGE * 2.25)) {
                targetUuid = null;
                target = null;
            }
        }

        if (target != null) {
            // Chase
            this.getLookControl().lookAt(target, 30f, 30f);
            this.getNavigation().startMovingTo(target, CHASE_NAV_SPEED);

            // Damage pulse (every 4 ticks) when close
            if (this.squaredDistanceTo(target) <= (1.6 * 1.6)) {
                if (++dmgTicker >= DAMAGE_INTERVAL) {
                    dmgTicker = 0;
                    target.damage(this.getDamageSources().mobAttack(this), DAMAGE_AMOUNT);
                }
            } else {
                dmgTicker = 0;
            }
            return;
        }

        // No target: return and "park" near hiding spot
        dmgTicker = 0;

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        if (parkPos == null || (this.age % 80) == 0) {
            // occasionally refresh parking spot (in case terrain changed)
            parkPos = findBestParkingSpot(sw, getHomePos(), getDisguiseState());
        }

        if (parkPos != null) {
            double distSq = this.getPos().squaredDistanceTo(Vec3d.ofCenter(parkPos));
            if (distSq <= 1.2 * 1.2) {
                // parked: stop moving
                this.getNavigation().stop();
            } else {
                this.getNavigation().startMovingTo(parkPos.getX() + 0.5, parkPos.getY(), parkPos.getZ() + 0.5, PARK_NAV_SPEED);
            }
        } else {
            // last resort: just stand still at home area
            this.getNavigation().stop();
        }
    }

    /* =========================
       Observed logic
       ========================= */

    private boolean isObservedByAnyPlayer() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return false;

        List<ServerPlayerEntity> players = sw.getPlayers(this::isValidVictimForObservation);
        for (ServerPlayerEntity p : players) {
            if (this.squaredDistanceTo(p) > (LOOK_RANGE * LOOK_RANGE)) continue;

            // must have line-of-sight
            if (!p.canSee(this)) continue;

            // must be actually looking at it
            if (isPlayerLookingAtMe(p)) return true;
        }
        return false;
    }

    private boolean isPlayerLookingAtMe(ServerPlayerEntity p) {
        Vec3d eyes = p.getCameraPosVec(1.0F);
        Vec3d look = p.getRotationVec(1.0F).normalize();

        Vec3d toMe = this.getPos().add(0, this.getStandingEyeHeight() * 0.5, 0).subtract(eyes);
        double dist = toMe.length();
        if (dist < 0.0001) return true;

        Vec3d dir = toMe.normalize();
        double dot = look.dotProduct(dir);

        // fairly tight cone so it only freezes when truly watched
        return dot > 0.965;
    }

    private boolean isValidVictimForObservation(ServerPlayerEntity p) {
        return isValidVictim(p);
    }

    private boolean isValidVictim(ServerPlayerEntity p) {
        if (p == null) return false;
        if (!p.isAlive() || p.isSpectator()) return false;
        if (p.getAbilities().creativeMode) return false;
        if (p.isInvisible()) return false;

        // Cultist + Divine Protection should never be targeted
        if (isCultist(p)) return false;
        return !p.hasStatusEffect(ModStatusEffects.DIVINE_PROTECTION);
    }

    private static boolean isCultist(PlayerEntity p) {
        if (!(p instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "cultist".equals(current);
    }

    @Nullable
    private ServerPlayerEntity findNearestVictim(int range) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;

        List<ServerPlayerEntity> players = sw.getPlayers(this::isValidVictim);
        ServerPlayerEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        double maxSq = range * range;
        for (ServerPlayerEntity p : players) {
            double d = this.squaredDistanceTo(p);
            if (d > maxSq) continue;
            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    @Nullable
    private ServerPlayerEntity getTargetPlayer() {
        if (targetUuid == null) return null;
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;

        MinecraftServer server = sw.getServer();
        if (server == null) return null;

        ServerPlayerEntity p = server.getPlayerManager().getPlayer(targetUuid);
        if (p == null) return null;
        if (p.getWorld() != this.getWorld()) return null;
        return p;
    }

    /* =========================
       Disguise + Parking selection
       ========================= */

    private boolean isGoodDisguise(BlockState s) {
        if (s == null) return false;
        if (s.isAir()) return false;

        FluidState f = s.getFluidState();
        if (!f.isEmpty()) return false;

        // avoid weird or uncopyable visuals
        if (s.isOf(Blocks.BEDROCK)) return false;

        return true;
    }

    @Nullable
    private BlockState findNearbyDisguise(ServerWorld sw, BlockPos center) {
        BlockState best = null;
        int bestScore = -1;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    BlockState s = sw.getBlockState(p);
                    if (!isGoodDisguise(s)) continue;

                    int score = 0;
                    for (Direction dir : Direction.values()) {
                        BlockState adj = sw.getBlockState(p.offset(dir));
                        if (adj.getBlock() == s.getBlock()) score++;
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        best = s;
                    }
                }
            }
        }

        return best;
    }

    @Nullable
    private BlockPos findBestParkingSpot(ServerWorld sw, BlockPos home, BlockState disguise) {
        Block disguiseBlock = disguise.getBlock();

        BlockPos best = null;
        int bestScore = -1;

        for (int tries = 0; tries < 48; tries++) {
            int dx = this.random.nextInt(PARK_RADIUS * 2 + 1) - PARK_RADIUS;
            int dz = this.random.nextInt(PARK_RADIUS * 2 + 1) - PARK_RADIUS;

            BlockPos base = home.add(dx, 0, dz);

            // Find a standable Y nearby
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos pos = base.up(dy);

                // must be empty space for entity
                if (!sw.getBlockState(pos).isAir()) continue;
                if (!sw.getBlockState(pos.up()).isAir()) continue;

                // must have a solid floor
                BlockPos floorPos = pos.down();
                BlockState floor = sw.getBlockState(floorPos);
                if (!floor.isSolidBlock(sw, floorPos)) continue;

                int score = 0;

                // Prefer “tucked near walls/corners”
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockState side = sw.getBlockState(pos.offset(dir));
                    if (!side.isAir()) score += 2;
                    if (side.getBlock() == disguiseBlock) score += 4; // bonus near same type as creation disguise
                }

                // Prefer floor matching disguise too
                if (floor.getBlock() == disguiseBlock) score += 5;

                // Prefer corners (two adjacent solids)
                int solidSides = 0;
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    if (!sw.getBlockState(pos.offset(dir)).isAir()) solidSides++;
                }
                if (solidSides >= 2) score += 4;

                if (score > bestScore) {
                    bestScore = score;
                    best = pos;
                }

                break;
            }
        }

        return best;
    }

    /* ---------- NBT ---------- */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        nbt.putInt("DisguiseRaw", this.dataTracker.get(DISGUISE_RAW));

        nbt.putBoolean("HomeSet", this.dataTracker.get(HOME_SET));
        BlockPos hp = this.dataTracker.get(HOME_POS);
        nbt.putInt("HomeX", hp.getX());
        nbt.putInt("HomeY", hp.getY());
        nbt.putInt("HomeZ", hp.getZ());

        if (targetUuid != null) nbt.putUuid("TargetUUID", targetUuid);

        if (parkPos != null) {
            nbt.putBoolean("HasPark", true);
            nbt.putInt("ParkX", parkPos.getX());
            nbt.putInt("ParkY", parkPos.getY());
            nbt.putInt("ParkZ", parkPos.getZ());
        } else {
            nbt.putBoolean("HasPark", false);
        }

        nbt.putInt("DmgTicker", dmgTicker);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.contains("DisguiseRaw")) {
            this.dataTracker.set(DISGUISE_RAW, nbt.getInt("DisguiseRaw"));
        }

        this.dataTracker.set(HOME_SET, nbt.getBoolean("HomeSet"));
        this.dataTracker.set(HOME_POS, new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ")));

        targetUuid = nbt.containsUuid("TargetUUID") ? nbt.getUuid("TargetUUID") : null;

        if (nbt.getBoolean("HasPark")) {
            parkPos = new BlockPos(nbt.getInt("ParkX"), nbt.getInt("ParkY"), nbt.getInt("ParkZ"));
        } else {
            parkPos = null;
        }

        dmgTicker = nbt.getInt("DmgTicker");
    }
}
