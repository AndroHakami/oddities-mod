package net.seep.odd.entity.misty;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
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

/**
 * Visual-only GeckoLib bubble that rides the target and despawns after 'lifeMax' ticks.
 * No collision, no physics, server-spawned -> client-rendered.
 */
public class MistyBubbleEntity extends Entity implements GeoEntity {

    // 0 = feet, 0.25 = legs, 0.45 = core/hips, 0.5 = center
    private static final double ANCHOR_Y_FACTOR = 0.45;

    // ===== state =====
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int life;
    private int lifeMax = 20 * 20; // default 20s
    @Nullable private UUID targetUuid; // for resilience across chunk reloads

    public MistyBubbleEntity(EntityType<? extends MistyBubbleEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    /** Convenience ctor used by the power; uses the registered type just like Creepy. */
    public MistyBubbleEntity(ServerWorld world, UUID target, int lifeMax) {
        this(ModEntities.MISTY_BUBBLE, world); // <-- use registry field, not a local TYPE
        this.lifeMax = Math.max(1, lifeMax);
        this.targetUuid = target;
        this.setInvulnerable(true);
        this.setSilent(true);
        this.noClip = true;
        this.setNoGravity(true);
    }

    // spawn packet (vanilla)
    @Override
    public EntitySpawnS2CPacket createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    // always visible
    @Override public boolean shouldRender(double distance) { return true; }

    // no collision/pushing
    @Override public boolean isCollidable() { return false; }
    @Override protected void pushOutOfBlocks(double x, double y, double z) {}
    @Override protected void initDataTracker() {}

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        // Despawn when time’s up or target vanished
        if (++life >= lifeMax) {
            discard();
            return;
        }

        // Make sure we’re still riding/attached to the target
        if (getFirstPassenger() != null) {
            // If something rode us by mistake, ignore (we ride the target, not vice versa)
            removeAllPassengers();
        }

        Entity host = null;
        if (this.hasVehicle()) {
            host = this.getVehicle();
        } else if (targetUuid != null && getWorld() instanceof ServerWorld sw) {
            host = sw.getEntity(targetUuid);
            if (host != null) this.startRiding(host, true);
        }

        if (host == null || !host.isAlive()) {
            discard();
            return;
        }

        // Anchor around torso/legs instead of head
        double anchorY = host.getY() + host.getHeight() * ANCHOR_Y_FACTOR;
        this.setPos(host.getX(), anchorY, host.getZ());
        this.setYaw(host.getYaw());
        this.setPitch(0);
    }

    // ====== GeckoLib animator ======
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "idle",
                0,                                  // transition length (ticks)
                state -> {
                    state.setAndContinue(IDLE);     // play "idle" forever
                    return PlayState.CONTINUE;
                }
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ====== NBT (persistence across chunk unloads) ======
    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Target")) targetUuid = nbt.getUuid("Target");
        life = nbt.getInt("Life");
        lifeMax = nbt.getInt("LifeMax");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (targetUuid != null) nbt.putUuid("Target", targetUuid);
        nbt.putInt("Life", life);
        nbt.putInt("LifeMax", lifeMax);
    }
}
