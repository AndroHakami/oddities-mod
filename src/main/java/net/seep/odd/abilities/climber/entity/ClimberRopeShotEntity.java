package net.seep.odd.abilities.climber.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.seep.odd.abilities.power.ClimberPower;
import net.seep.odd.entity.ModEntities;

import java.util.Optional;
import java.util.UUID;

public class ClimberRopeShotEntity extends ProjectileEntity {

    public enum Mode { ANCHOR, PULL }

    private static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(ClimberRopeShotEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private static final TrackedData<Byte> MODE =
            DataTracker.registerData(ClimberRopeShotEntity.class, TrackedDataHandlerRegistry.BYTE);

    private static final TrackedData<Boolean> RETURNING =
            DataTracker.registerData(ClimberRopeShotEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<Boolean> DROPPING =
            DataTracker.registerData(ClimberRopeShotEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private double startX, startY, startZ;
    private boolean startSet = false;

    public ClimberRopeShotEntity(EntityType<? extends ClimberRopeShotEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(OWNER_UUID, Optional.empty());
        this.dataTracker.startTracking(MODE, (byte)0); // 0 = ANCHOR, 1 = PULL
        this.dataTracker.startTracking(RETURNING, false);
        this.dataTracker.startTracking(DROPPING, false);
    }

    public void setOwnerUuid(UUID id) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(id));
    }

    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER_UUID).orElse(null);
    }

    public void setMode(Mode m) {
        this.dataTracker.set(MODE, (byte)(m == Mode.PULL ? 1 : 0));
    }

    public Mode getMode() {
        return this.dataTracker.get(MODE) == 1 ? Mode.PULL : Mode.ANCHOR;
    }

    public boolean isReturning() {
        return this.dataTracker.get(RETURNING);
    }

    public boolean isDropping() {
        return this.dataTracker.get(DROPPING);
    }

    public void setStartPos(Vec3d p) {
        this.startX = p.x;
        this.startY = p.y;
        this.startZ = p.z;
        this.startSet = true;
    }

    public Vec3d getStartPos() {
        return new Vec3d(startX, startY, startZ);
    }

    /** Called when player taps again. */
    public void startReturn() {
        if (this.getWorld().isClient) return;

        this.dataTracker.set(DROPPING, false);
        this.dataTracker.set(RETURNING, true);

        // soften current velocity so it doesn't "snap"
        this.setVelocity(this.getVelocity().multiply(0.2));
    }

    /** Called when max range reached: hook stops going out and falls down. */
    private void startDrop() {
        if (this.getWorld().isClient) return;

        this.dataTracker.set(RETURNING, false);
        this.dataTracker.set(DROPPING, true);

        Vec3d v = this.getVelocity();

        // kill most horizontal speed so it visibly "drops"
        Vec3d nv = new Vec3d(v.x * 0.18, Math.min(v.y, 0.08), v.z * 0.18);
        this.setVelocity(nv);
    }

    @Override
    public void tick() {
        super.tick();

        if (!startSet) {
            setStartPos(this.getPos());
        }

        // Lifetime fallback
        if (this.age > 200) {
            this.discard();
            return;
        }

        if (isReturning()) {
            tickReturn();
            return;
        }

        // Apply gravity arc while flying/dropping
        Vec3d v0 = this.getVelocity();
        this.setVelocity(v0.x, v0.y - 0.035, v0.z);

        // If flying normally (not dropping yet), enforce 30m range in XZ plane
        if (!this.getWorld().isClient && !isDropping()) {
            Vec3d p = this.getPos();
            double dx = p.x - startX;
            double dz = p.z - startZ;
            double planar = Math.sqrt(dx * dx + dz * dz);

            if (planar >= 30.0) {
                // At max range -> DROP instead of retract/disappear
                startDrop();
                // continue ticking this frame (it will fall & collide)
            }
        }

        // While dropping, damp horizontal motion a bit every tick so it settles naturally
        if (isDropping() && !this.getWorld().isClient) {
            Vec3d v = this.getVelocity();
            this.setVelocity(v.x * 0.92, v.y, v.z * 0.92);
        }

        // Move + collision via raycast between current and next pos
        Vec3d start = this.getPos();
        Vec3d end = start.add(this.getVelocity());

        HitResult hit = this.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        // Entity hit test
        EntityHitResult ehr = net.minecraft.entity.projectile.ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                start,
                end,
                this.getBoundingBox().stretch(this.getVelocity()).expand(0.75),
                (e) -> e.isAlive() && e != this.getOwner()
                        && !(e instanceof ClimberRopeAnchorEntity)
                        && !(e instanceof ClimberPullTetherEntity)
        );

        if (ehr != null) {
            onEntityHit(ehr);
            return;
        }

        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
            return;
        }

        this.setPosition(end);

        // small visual
        if (this.getWorld() instanceof ServerWorld sw && (this.age % 2) == 0) {
            sw.spawnParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void tickReturn() {
        if (this.getWorld().isClient) return;

        UUID ownerId = getOwnerUuid();
        if (ownerId == null) {
            this.discard();
            return;
        }

        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        Vec3d target = ClimberPower.ropeOrigin(owner);
        Vec3d here = this.getPos();
        Vec3d to = target.subtract(here);
        double d = to.length();

        if (d < 1.2) {
            this.discard();
            return;
        }

        Vec3d dir = to.multiply(1.0 / d);
        Vec3d vel = dir.multiply(2.35);

        // no gravity while returning
        this.setVelocity(vel);
        this.setPosition(here.add(vel));
    }

    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) return;

        if (getMode() == Mode.ANCHOR && hit instanceof BlockHitResult bhr) {
            Entity owner = this.getOwner();
            if (!(owner instanceof ServerPlayerEntity sp)) {
                this.discard();
                return;
            }

            BlockPos bp = bhr.getBlockPos();
            if (this.getWorld().getBlockState(bp).isAir()) {
                // if we somehow hit "air", just drop/return
                if (!isDropping()) startReturn();
                else this.discard();
                return;
            }

            // Spawn anchor slightly OUT of the block face so rope is never hidden inside a full block
            Vec3d pushOut = Vec3d.of(bhr.getSide().getVector()).multiply(0.06);
            Vec3d p = bhr.getPos().add(pushOut);

            ClimberRopeAnchorEntity anchor = new ClimberRopeAnchorEntity(ModEntities.CLIMBER_ROPE_ANCHOR, this.getWorld());
            anchor.setOwnerUuid(sp.getUuid());
            anchor.setAttachedBlockPos(bp);
            anchor.refreshPositionAndAngles(p.x, p.y, p.z, 0.0f, 0.0f);

            // Initial rope length = current distance (clamped to 30)
            double len = ClimberPower.ropeOrigin(sp).distanceTo(anchor.getPos());
            anchor.setRopeLength((float) Math.max(2.0, Math.min(30.0, len)));

            this.getWorld().spawnEntity(anchor);
            ClimberPower.bindAnchor(sp, anchor);
        }

        this.discard();
    }

    protected void onEntityHit(EntityHitResult ehr) {
        if (this.getWorld().isClient) return;

        if (getMode() != Mode.PULL) {
            // primary hook doesn't latch to entities
            // If it was dropping, just keep dropping; otherwise return.
            if (!isDropping()) startReturn();
            return;
        }

        Entity owner = this.getOwner();
        if (!(owner instanceof ServerPlayerEntity sp)) {
            this.discard();
            return;
        }

        Entity target = ehr.getEntity();
        if (!(target instanceof LivingEntity le) || !le.isAlive()) {
            this.discard();
            return;
        }

        double maxHp = le.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null
                ? le.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH)
                : le.getMaxHealth();

        if (maxHp > 50.0) {
            this.discard();
            return;
        }

        le.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 2 * 20, 0, true, false, false));
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1 * 20, 2, true, false, false));

        ClimberPullTetherEntity tether = new ClimberPullTetherEntity(ModEntities.CLIMBER_PULL_TETHER, this.getWorld());
        tether.setOwnerUuid(sp.getUuid());
        tether.setTargetId(le.getId());
        tether.setLifetimeTicks(14);
        tether.refreshPositionAndAngles(le.getX(), le.getBodyY(0.5), le.getZ(), 0f, 0f);
        this.getWorld().spawnEntity(tether);

        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) setOwnerUuid(nbt.getUuid("Owner"));
        if (nbt.contains("Mode")) {
            String s = nbt.getString("Mode");
            setMode("PULL".equalsIgnoreCase(s) ? Mode.PULL : Mode.ANCHOR);
        }
        if (nbt.contains("Returning")) this.dataTracker.set(RETURNING, nbt.getBoolean("Returning"));
        if (nbt.contains("Dropping")) this.dataTracker.set(DROPPING, nbt.getBoolean("Dropping"));
        if (nbt.contains("StartX")) {
            startX = nbt.getDouble("StartX");
            startY = nbt.getDouble("StartY");
            startZ = nbt.getDouble("StartZ");
            startSet = true;
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        UUID o = getOwnerUuid();
        if (o != null) nbt.putUuid("Owner", o);
        nbt.putString("Mode", getMode().name());
        nbt.putBoolean("Returning", isReturning());
        nbt.putBoolean("Dropping", isDropping());
        if (startSet) {
            nbt.putDouble("StartX", startX);
            nbt.putDouble("StartY", startY);
            nbt.putDouble("StartZ", startZ);
        }
    }

    /** Find a shot by UUID across worlds. */
    public static ClimberRopeShotEntity findShot(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof ClimberRopeShotEntity s) return s;
        }
        return null;
    }
}
