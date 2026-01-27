package net.seep.odd.abilities.climber.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;
import net.seep.odd.abilities.power.ClimberPower;
import net.seep.odd.entity.ModEntities;

public class ClimberRopeShotEntity extends ProjectileEntity {

    public enum Mode { ANCHOR, PULL }

    private Mode mode = Mode.ANCHOR;

    public ClimberRopeShotEntity(EntityType<? extends ClimberRopeShotEntity> type, World world) {
        super(type, world);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    protected void initDataTracker() {}

    @Override
    public void tick() {
        super.tick();

        // Lifetime
        if (this.age > 60) {
            this.discard();
            return;
        }

        // Gravity for arc
        Vec3d v = this.getVelocity();
        this.setVelocity(v.x, v.y - 0.035, v.z);

        // Move + collision via raycast between current and next pos
        Vec3d start = this.getPos();
        Vec3d end = start.add(this.getVelocity());

        HitResult hit = this.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        // Entity hit test (cheap: expand box along motion)
        EntityHitResult ehr = net.minecraft.entity.projectile.ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                start,
                end,
                this.getBoundingBox().stretch(this.getVelocity()).expand(0.75),
                (e) -> e.isAlive() && e != this.getOwner() && !(e instanceof ClimberRopeAnchorEntity) && !(e instanceof ClimberPullTetherEntity)
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

        // Small visual
        if (this.getWorld() instanceof ServerWorld sw && (this.age % 2) == 0) {
            sw.spawnParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) return;

        if (mode == Mode.ANCHOR && hit instanceof BlockHitResult bhr) {
            Entity owner = this.getOwner();
            if (!(owner instanceof ServerPlayerEntity sp)) {
                this.discard();
                return;
            }

            BlockPos bp = bhr.getBlockPos();
            if (this.getWorld().getBlockState(bp).isAir()) {
                this.discard();
                return;
            }

            // Spawn anchor entity at hit position
            Vec3d p = bhr.getPos();

            ClimberRopeAnchorEntity anchor = new ClimberRopeAnchorEntity(ModEntities.CLIMBER_ROPE_ANCHOR, this.getWorld());
            anchor.setOwnerUuid(sp.getUuid());
            anchor.setAttachedBlockPos(bp);
            anchor.refreshPositionAndAngles(p.x, p.y, p.z, 0.0f, 0.0f);

            // Initial rope length = current distance (clamped)
            double len = ClimberPower.ropeOrigin(sp).distanceTo(anchor.getPos());
            anchor.setRopeLength((float) Math.max(2.0, Math.min(20.0, len)));

            this.getWorld().spawnEntity(anchor);
            ClimberPower.bindAnchor(sp, anchor);
        }

        this.discard();
    }

    protected void onEntityHit(EntityHitResult ehr) {
        if (this.getWorld().isClient) return;

        if (mode != Mode.PULL) {
            this.discard();
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

        // HP gate: cannot pull targets with max HP > 50
        double maxHp = le.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null
                ? le.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH)
                : le.getMaxHealth();

        if (maxHp > 50.0) {
            this.discard();
            return;
        }

        // Apply effects: nausea 2s, slowness III 1s
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 2 * 20, 0, true, false, false));
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1 * 20, 2, true, false, false));

        // Spawn a short-lived tether puller that applies pull force over several ticks (feels much better than 1-tick velocity)
        ClimberPullTetherEntity tether = new ClimberPullTetherEntity(ModEntities.CLIMBER_PULL_TETHER, this.getWorld());
        tether.setOwnerUuid(sp.getUuid());
        tether.setTargetId(le.getId());
        tether.setLifetimeTicks(14); // ~0.7s pull
        tether.refreshPositionAndAngles(le.getX(), le.getBodyY(0.5), le.getZ(), 0f, 0f);
        this.getWorld().spawnEntity(tether);

        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Mode")) {
            try {
                this.mode = Mode.valueOf(nbt.getString("Mode"));
            } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Mode", this.mode.name());
    }
}
