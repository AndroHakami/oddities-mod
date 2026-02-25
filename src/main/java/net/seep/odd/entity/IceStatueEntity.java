// FILE: src/main/java/net/seep/odd/entity/IceStatueEntity.java
package net.seep.odd.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;

import java.util.Optional;
import java.util.UUID;

public class IceStatueEntity extends LivingEntity {

    // ---- owner ----
    private static final TrackedData<Optional<UUID>> OWNER =
            DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    // ---- crouch flag ----
    private static final TrackedData<Boolean> CROUCH =
            DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // ---- pose floats (frozen model parts) ----
    private static final TrackedData<Float> HEAD_P = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HEAD_Y = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HEAD_R = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> BODY_P = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BODY_Y = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BODY_R = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> RA_P = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RA_Y = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RA_R = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> LA_P = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LA_Y = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LA_R = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> RL_P = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RL_Y = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RL_R = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final TrackedData<Float> LL_P = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LL_Y = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> LL_R = DataTracker.registerData(IceStatueEntity.class, TrackedDataHandlerRegistry.FLOAT);

    public IceStatueEntity(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
        this.setNoGravity(false);
        this.setInvulnerable(false);
        this.setSilent(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(OWNER, Optional.empty());
        this.dataTracker.startTracking(CROUCH, false);

        this.dataTracker.startTracking(HEAD_P, 0f);
        this.dataTracker.startTracking(HEAD_Y, 0f);
        this.dataTracker.startTracking(HEAD_R, 0f);

        this.dataTracker.startTracking(BODY_P, 0f);
        this.dataTracker.startTracking(BODY_Y, 0f);
        this.dataTracker.startTracking(BODY_R, 0f);

        this.dataTracker.startTracking(RA_P, 0f);
        this.dataTracker.startTracking(RA_Y, 0f);
        this.dataTracker.startTracking(RA_R, 0f);

        this.dataTracker.startTracking(LA_P, 0f);
        this.dataTracker.startTracking(LA_Y, 0f);
        this.dataTracker.startTracking(LA_R, 0f);

        this.dataTracker.startTracking(RL_P, 0f);
        this.dataTracker.startTracking(RL_Y, 0f);
        this.dataTracker.startTracking(RL_R, 0f);

        this.dataTracker.startTracking(LL_P, 0f);
        this.dataTracker.startTracking(LL_Y, 0f);
        this.dataTracker.startTracking(LL_R, 0f);
    }

    // ---- slippery + pushable ----
    @Override
    public void tickMovement() {
        super.tickMovement();

        // no self-walking, but allow pushes / knockback / sliding
        Vec3d v = this.getVelocity();

        // "slippery": very light damping on ground
        if (this.isOnGround()) {
            this.setVelocity(v.x * 0.985, v.y, v.z * 0.985);
        } else {
            this.setVelocity(v.x * 0.990, v.y, v.z * 0.990);
        }
    }

    @Override
    public boolean isPushable() { return true; }

    @Override
    protected void pushAway(Entity entity) {
        super.pushAway(entity);
    }

    // ---- owner ----
    public void setOwnerUuid(UUID uuid) { this.dataTracker.set(OWNER, Optional.of(uuid)); }
    public @org.jetbrains.annotations.Nullable UUID getOwnerUuid() { return this.dataTracker.get(OWNER).orElse(null); }

    // ---- pose API ----
    public void setCrouch(boolean crouch) { this.dataTracker.set(CROUCH, crouch); }
    public boolean getCrouch() { return this.dataTracker.get(CROUCH); }

    public void applyPose(
            boolean crouch,
            float hp, float hy, float hr,
            float bp, float by, float br,
            float rap, float ray, float rar,
            float lap, float lay, float lar,
            float rlp, float rly, float rlr,
            float llp, float lly, float llr
    ) {
        this.dataTracker.set(CROUCH, crouch);

        this.dataTracker.set(HEAD_P, hp); this.dataTracker.set(HEAD_Y, hy); this.dataTracker.set(HEAD_R, hr);
        this.dataTracker.set(BODY_P, bp); this.dataTracker.set(BODY_Y, by); this.dataTracker.set(BODY_R, br);

        this.dataTracker.set(RA_P, rap); this.dataTracker.set(RA_Y, ray); this.dataTracker.set(RA_R, rar);
        this.dataTracker.set(LA_P, lap); this.dataTracker.set(LA_Y, lay); this.dataTracker.set(LA_R, lar);

        this.dataTracker.set(RL_P, rlp); this.dataTracker.set(RL_Y, rly); this.dataTracker.set(RL_R, rlr);
        this.dataTracker.set(LL_P, llp); this.dataTracker.set(LL_Y, lly); this.dataTracker.set(LL_R, llr);
    }

    public float headP(){ return this.dataTracker.get(HEAD_P); }
    public float headY(){ return this.dataTracker.get(HEAD_Y); }
    public float headR(){ return this.dataTracker.get(HEAD_R); }

    public float bodyP(){ return this.dataTracker.get(BODY_P); }
    public float bodyY(){ return this.dataTracker.get(BODY_Y); }
    public float bodyR(){ return this.dataTracker.get(BODY_R); }

    public float raP(){ return this.dataTracker.get(RA_P); }
    public float raY(){ return this.dataTracker.get(RA_Y); }
    public float raR(){ return this.dataTracker.get(RA_R); }

    public float laP(){ return this.dataTracker.get(LA_P); }
    public float laY(){ return this.dataTracker.get(LA_Y); }
    public float laR(){ return this.dataTracker.get(LA_R); }

    public float rlP(){ return this.dataTracker.get(RL_P); }
    public float rlY(){ return this.dataTracker.get(RL_Y); }
    public float rlR(){ return this.dataTracker.get(RL_R); }

    public float llP(){ return this.dataTracker.get(LL_P); }
    public float llY(){ return this.dataTracker.get(LL_Y); }
    public float llR(){ return this.dataTracker.get(LL_R); }

    // ---- LivingEntity abstract stuff (no equipment) ----
    @Override public Iterable<ItemStack> getArmorItems() { return java.util.List.of(); }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) {}
    @Override public Arm getMainArm() { return Arm.RIGHT; }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // allow players to kill it; still respect invuln tags etc
        if (this.isInvulnerableTo(source)) return false;
        return super.damage(source, amount);
    }

    @Override
    protected void drop(DamageSource source) {
        // no drops (you said it's fine)
    }

    // ---- NBT ----
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        UUID o = getOwnerUuid();
        if (o != null) nbt.putUuid("Owner", o);

        nbt.putBoolean("Crouch", getCrouch());

        nbt.putFloat("hp", headP()); nbt.putFloat("hy", headY()); nbt.putFloat("hr", headR());
        nbt.putFloat("bp", bodyP()); nbt.putFloat("by", bodyY()); nbt.putFloat("br", bodyR());

        nbt.putFloat("rap", raP()); nbt.putFloat("ray", raY()); nbt.putFloat("rar", raR());
        nbt.putFloat("lap", laP()); nbt.putFloat("lay", laY()); nbt.putFloat("lar", laR());

        nbt.putFloat("rlp", rlP()); nbt.putFloat("rly", rlY()); nbt.putFloat("rlr", rlR());
        nbt.putFloat("llp", llP()); nbt.putFloat("lly", llY()); nbt.putFloat("llr", llR());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.containsUuid("Owner")) setOwnerUuid(nbt.getUuid("Owner"));

        boolean crouch = nbt.getBoolean("Crouch");
        applyPose(
                crouch,
                nbt.getFloat("hp"), nbt.getFloat("hy"), nbt.getFloat("hr"),
                nbt.getFloat("bp"), nbt.getFloat("by"), nbt.getFloat("br"),
                nbt.getFloat("rap"), nbt.getFloat("ray"), nbt.getFloat("rar"),
                nbt.getFloat("lap"), nbt.getFloat("lay"), nbt.getFloat("lar"),
                nbt.getFloat("rlp"), nbt.getFloat("rly"), nbt.getFloat("rlr"),
                nbt.getFloat("llp"), nbt.getFloat("lly"), nbt.getFloat("llr")
        );
    }

    @Override
    public EntitySpawnS2CPacket createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
