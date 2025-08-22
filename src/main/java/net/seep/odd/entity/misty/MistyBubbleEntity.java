// net/seep/odd/entity/misty/MistyBubbleEntity.java
package net.seep.odd.entity.misty;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

import net.seep.odd.entity.ModEntities;

public class MistyBubbleEntity extends Entity implements GeoEntity {

    public static EntityType<MistyBubbleEntity> buildType() {
        return FabricEntityTypeBuilder
                .<MistyBubbleEntity>create(SpawnGroup.MISC, MistyBubbleEntity::new)
                .dimensions(net.minecraft.entity.EntityDimensions.fixed(0f, 0f))
                .trackRangeChunks(8)
                .trackedUpdateRate(1)
                .build();
    }

    // === synced (read by renderer) ===
    private static final TrackedData<Integer> TARGET_ID =
            DataTracker.registerData(MistyBubbleEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> Y_OFFSET =
            DataTracker.registerData(MistyBubbleEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // === server state ===
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int life;
    private int lifeMax = 20 * 20;
    @Nullable private UUID targetUuid;
    private double yOffset = 0.05;

    public MistyBubbleEntity(EntityType<? extends MistyBubbleEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
    }

    public MistyBubbleEntity(ServerWorld world, UUID target, int lifeMax) {
        this(ModEntities.MISTY_BUBBLE, world);
        this.lifeMax = Math.max(1, lifeMax);
        this.targetUuid = target;
        this.yOffset = 0.05;
        Entity host = world.getEntity(target);
        if (host != null) {
            this.getDataTracker().set(TARGET_ID, host.getId());
            this.getDataTracker().set(Y_OFFSET, (float)this.yOffset);
        }
    }

    public MistyBubbleEntity(ServerWorld world, UUID target, int lifeMax, double yOffset) {
        this(world, target, lifeMax);
        this.yOffset = yOffset;
        this.getDataTracker().set(Y_OFFSET, (float)this.yOffset);
    }

    @Override protected void initDataTracker() {
        this.getDataTracker().startTracking(TARGET_ID, 0);
        this.getDataTracker().startTracking(Y_OFFSET, 0.05f);
    }

    @Override public EntitySpawnS2CPacket createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
    @Override public boolean shouldRender(double distance) { return true; }
    @Override public boolean isCollidable() { return false; }
    @Override protected void pushOutOfBlocks(double x, double y, double z) {}

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return; // let renderer handle smooth follow on client

        if (++life >= lifeMax) { discard(); return; }

        Entity host = null;
        if (targetUuid != null && getWorld() instanceof ServerWorld sw) {
            host = sw.getEntity(targetUuid);
            if (host != null && this.getDataTracker().get(TARGET_ID) != host.getId()) {
                this.getDataTracker().set(TARGET_ID, host.getId());
                this.getDataTracker().set(Y_OFFSET, (float)this.yOffset);
            }
        }
        if (host == null || !host.isAlive()) { discard(); return; }

        // server anchor at feet (authoritative)
        this.setPos(host.getX(), host.getY() + this.yOffset, host.getZ());
        this.setYaw(host.getYaw());
        this.setPitch(0);
    }

    // === GeckoLib ===
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "idle", 0, s -> { s.setAndContinue(IDLE); return PlayState.CONTINUE; }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // === NBT ===
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Target")) targetUuid = nbt.getUuid("Target");
        life = nbt.getInt("Life");
        lifeMax = nbt.getInt("LifeMax");
        if (nbt.contains("YOffset")) yOffset = nbt.getDouble("YOffset");
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        nbt.putInt("Life", life);
        nbt.putInt("LifeMax", lifeMax);
        nbt.putDouble("YOffset", yOffset);
    }

    // === getters used by renderer ===
    public int getTrackedTargetId() { return this.getDataTracker().get(TARGET_ID); }
    public float getTrackedYOffset() { return this.getDataTracker().get(Y_OFFSET); }
}
