package net.seep.odd.entity.spotted;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PhantomBuddyEntity extends TameableEntity /* implements GeoEntity later */ {

    private final SimpleInventory storage = new SimpleInventory(12 * 12); // 144 slots

    public PhantomBuddyEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setTamed(true); // acts like already tamed on spawn
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return TameableEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new FollowOwnerGoal(this, 1.1, 3.0f, 1.0f, false));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
    }

    /* ===== storage ===== */
    public SimpleInventory getStorage() { return storage; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        Inventories.writeNbt(nbt, storage.stacks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        Inventories.readNbt(nbt, storage.stacks);
    }

    /* ===== lifecycle ===== */
    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean res = super.damage(source, amount);
        if (!this.world.isClient && this.isAlive() && this.getHealth() <= 0.01f) {
            dropAll();
        }
        return res;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.world.isClient && reason.shouldDestroy()) {
            dropAll();
        }
        super.remove(reason);
    }

    public void setOwner(ServerPlayerEntity player) {
        this.setOwnerUuid(player.getUuid());
        this.setCustomName(player.getName().copy().append("’s Phantom Buddy"));
        this.setCustomNameVisible(false);
    }

    /** Recall behaviour from the power: dump inventory near owner and vanish. */
    public void recallToOwnerAndDrop(ServerPlayerEntity owner) {
        if (!(this.world instanceof ServerWorld sw)) { this.discard(); return; }
        // Try to insert into owner inventory first
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.getStack(i);
            if (stack.isEmpty()) continue;
            if (!owner.getInventory().insertStack(stack.copy())) {
                // drop if no room
                this.dropStack(stack.copy());
            }
            storage.setStack(i, ItemStack.EMPTY);
        }
        sw.playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.1f);
        this.discard();
    }

    private void dropAll() {
        for (int i = 0; i < storage.size(); i++) {
            ItemStack s = storage.getStack(i);
            if (!s.isEmpty()) this.dropStack(s.copy());
            storage.setStack(i, ItemStack.EMPTY);
        }
    }

    @Nullable
    @Override
    public UUID getOwnerUuid() {
        return super.getOwnerUuid();
    }
}
