// net/seep/odd/entity/creepy/CreepyEntity.java
package net.seep.odd.entity.creepy;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
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

import net.seep.odd.abilities.astral.AstralInventory;

public final class CreepyEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("creepy.model.idle");

    private @Nullable UUID ownerUuid;
    private @Nullable Vec3d anchor;
    private float anchorYaw;

    public CreepyEntity(EntityType<? extends CreepyEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPersistent(); // keep it around
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    /** Called after spawn to bind the owner and freeze position. */
    public void initFor(ServerPlayerEntity owner) {
        this.ownerUuid = owner.getUuid();
        this.anchor = getPos();
        this.anchorYaw = owner.getYaw();
        this.setHealth(1.0F);
        this.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, anchorYaw, 0f);
    }

    @Override
    protected void initGoals() {
        // no goals/AI
    }

    @Override
    public void tick() {
        super.tick();

        // Stay perfectly anchored and still
        if (!this.getWorld().isClient && anchor != null) {
            this.setVelocity(0, 0, 0);
            this.fallDistance = 0;
            this.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, anchorYaw, 0f);
        }
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(Entity entity) {}

    /** Any damage = pop the body + force exit astral. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!this.getWorld().isClient && !this.isRemoved()) {
            if (this.ownerUuid != null && this.getWorld() instanceof ServerWorld sw) {
                AstralInventory.onCreepyBroken(sw, this.ownerUuid);
            }
            this.discard();
            return true;
        }
        return false;
    }

    /* ---------- GeckoLib ---------- */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "creepy.model.idle", state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /* ---------- NBT ---------- */

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
        if (anchor != null) {
            nbt.putDouble("AX", anchor.x);
            nbt.putDouble("AY", anchor.y);
            nbt.putDouble("AZ", anchor.z);
            nbt.putFloat("AYaw", anchorYaw);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        if (nbt.contains("AX")) {
            anchor = new Vec3d(nbt.getDouble("AX"), nbt.getDouble("AY"), nbt.getDouble("AZ"));
            anchorYaw = nbt.getFloat("AYaw");
        }
        this.setNoGravity(true);
        this.setAiDisabled(true);
        this.setSilent(true);
    }
}
