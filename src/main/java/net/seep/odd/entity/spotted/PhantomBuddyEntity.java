package net.seep.odd.entity.spotted;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.seep.odd.abilities.spotted.SpottedStorageState;
import net.seep.odd.abilities.spotted.PhantomBuddyScreenHandler;

import java.util.UUID;

/** Friendly stash-carrying companion (4×4 storage = 16 slots). */
public class PhantomBuddyEntity extends TameableEntity implements GeoEntity {

    public static final int BUDDY_SIZE = 4 * 4;
    private final SimpleInventory storage = new SimpleInventory(BUDDY_SIZE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    /** Guard so recall doesn't trigger death cleanup logic. */
    private boolean recalling = false;

    public PhantomBuddyEntity(EntityType<? extends TameableEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setTamed(true);
        this.ignoreCameraFrustum = true;
        this.experiencePoints = 0;
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

    /* ---------- GUI open ---------- */

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.isAlive()) return ActionResult.PASS;
        if (!this.getWorld().isClient && this.isOwner(player)) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, p) -> new net.seep.odd.abilities.spotted.PhantomBuddyScreenHandler(syncId, playerInv, this),
                    Text.translatable("entity.odd.phantom_buddy")
            );
            sp.openHandledScreen(factory);
            return ActionResult.CONSUME;
        }
        return super.interactMob(player, hand);
    }

    /* ---------- Owner & storage helpers ---------- */

    public SimpleInventory getStorage() { return storage; }

    /** Called by power AFTER spawn; loads any persisted contents for this owner. */
    public void setOwner(ServerPlayerEntity player) {
        this.setOwnerUuid(player.getUuid());
        this.setCustomNameVisible(false);
        SpottedStorageState.get(player.getServer()).loadInto(player.getUuid(), storage);
    }

    /**
     * Recall: persist inventory to server state for this owner, clear the live inventory to avoid dupes,
     * and despawn WITHOUT clearing the persisted stash.
     */
    public void recallToOwnerAndDrop(ServerPlayerEntity owner) {
        if (!(this.getWorld() instanceof ServerWorld sw)) { this.discard(); return; }

        // Mark as recalling so remove() knows not to run death cleanup
        this.recalling = true;

        // Save items for next summon
        SpottedStorageState.get(owner.getServer()).saveInventory(owner.getUuid(), storage);

        // Clear the live inventory so nothing dupes while the entity disappears
        for (int i = 0; i < storage.size(); i++) storage.setStack(i, ItemStack.EMPTY);

        sw.playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.1f);
        this.discard(); // triggers remove(RemovalReason.DISCARDED)
    }

    /**
     * Removal hook:
     * - On true death/destruction (NOT recall): drop items, wipe live inventory, and clear persisted stash.
     * - On recall (recalling == true): skip cleanup so the stash remains saved.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.getWorld().isClient) {
            if (!this.recalling && reason.shouldDestroy()) {
                // Death path: drop then wipe everything, including persisted stash
                dropAll();
                for (int i = 0; i < storage.size(); i++) storage.setStack(i, ItemStack.EMPTY);
                UUID ownerId = this.getOwnerUuid();
                if (ownerId != null && this.getServer() != null) {
                    SpottedStorageState.get(this.getServer()).clear(ownerId);
                }
            }
        }
        super.remove(reason);
    }

    private void dropAll() {
        for (int i = 0; i < storage.size(); i++) {
            ItemStack s = storage.getStack(i);
            if (!s.isEmpty()) this.dropStack(s.copy());
            storage.setStack(i, ItemStack.EMPTY);
        }
    }

    @Override public boolean damage(DamageSource source, float amount) { return super.damage(source, amount); }
    @Override public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) { return null; }

    @Override
    public EntityView method_48926() {
        return null;
    }

    /* ---------- Null-safe owner lookup ---------- */
    @Override
    public @Nullable LivingEntity getOwner() {
        UUID id = this.getOwnerUuid();
        if (id == null) return null;
        World w = this.getWorld();
        if (w == null) return null;
        return w.getPlayerByUuid(id);
    }

    /* ---------- NBT (entity save) ---------- */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        DefaultedList<ItemStack> list = DefaultedList.ofSize(storage.size(), ItemStack.EMPTY);
        for (int i = 0; i < storage.size(); i++) list.set(i, storage.getStack(i));
        Inventories.writeNbt(nbt, list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        DefaultedList<ItemStack> list = DefaultedList.ofSize(storage.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, list);
        for (int i = 0; i < storage.size(); i++) storage.setStack(i, list.get(i));
    }

    /* ---------- GeckoLib ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "phantom_buddy.controller", 0, state -> {
            if (state.isMoving()) state.setAndContinue(WALK);
            else state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
