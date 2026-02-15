// FILE: src/main/java/net/seep/odd/abilities/sniper/entity/SniperGrappleShotEntity.java
package net.seep.odd.abilities.sniper.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.SniperPower;
import net.seep.odd.entity.ModEntities;

import java.util.Optional;
import java.util.UUID;

public class SniperGrappleShotEntity extends ProjectileEntity {

    private static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(SniperGrappleShotEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private double startX, startY, startZ;
    private boolean startSet = false;

    public SniperGrappleShotEntity(EntityType<? extends SniperGrappleShotEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(OWNER_UUID, Optional.empty());
    }

    public void setOwnerUuid(UUID id) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(id));
    }

    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER_UUID).orElse(null);
    }

    public void setStartPos(Vec3d p) {
        this.startX = p.x;
        this.startY = p.y;
        this.startZ = p.z;
        this.startSet = true;
    }

    @Override
    public void tick() {
        super.tick();

        if (!startSet) setStartPos(this.getPos());

        // lifetime
        if (this.age > 60) {
            this.discard();
            return;
        }

        Vec3d start = this.getPos();
        Vec3d end = start.add(this.getVelocity());

        HitResult hit = this.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
            return;
        }

        this.setPosition(end);
    }

    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) return;

        if (!(this.getOwner() instanceof ServerPlayerEntity sp)) {
            this.discard();
            return;
        }

        if (!(hit instanceof BlockHitResult bhr)) {
            this.discard();
            return;
        }

        BlockPos bp = bhr.getBlockPos();
        if (this.getWorld().getBlockState(bp).isAir()) {
            this.discard();
            return;
        }

        Vec3d pushOut = Vec3d.of(bhr.getSide().getVector()).multiply(0.06);
        Vec3d p = bhr.getPos().add(pushOut);

        SniperGrappleAnchorEntity anchor = new SniperGrappleAnchorEntity(ModEntities.SNIPER_GRAPPLE_ANCHOR, this.getWorld());
        anchor.setOwnerUuid(sp.getUuid());
        anchor.setAttachedBlockPos(bp);
        anchor.refreshPositionAndAngles(p.x, p.y, p.z, 0f, 0f);

        // rope length starts at current distance, then shortens quickly in anchor tick
        double len = sp.getPos().add(0, sp.getHeight() * 0.45, 0).distanceTo(anchor.getPos());
        anchor.setRopeLength((float) MathHelper.clamp(len, 2.2f, 48.0f));

        this.getWorld().spawnEntity(anchor);
        SniperPower.bindAnchor(sp, anchor);

        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) setOwnerUuid(nbt.getUuid("Owner"));
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
        if (startSet) {
            nbt.putDouble("StartX", startX);
            nbt.putDouble("StartY", startY);
            nbt.putDouble("StartZ", startZ);
        }
    }

    public static SniperGrappleShotEntity findShot(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e instanceof SniperGrappleShotEntity s) return s;
        }
        return null;
    }
}
