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
import net.seep.odd.abilities.spotted.BuddyPersistentState;

import java.util.UUID;

/** Friendly stash-carrying companion (4×4 storage = 16 slots) with persistence. */
public class PhantomBuddyEntity extends TameableEntity implements GeoEntity {

    public static final int BUDDY_SIZE = 4 * 4;
    private final SimpleInventory storage = new SimpleInventory(BUDDY_SIZE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    /** Guard so recall doesn't trigger death cleanup logic. */
    private boolean recalling = false;

    /** Persistence generation (prevents dupes when an old body unload/reloads later). */
    private int generationTag = 0;

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

    /* ---------- GUI ---------- */

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

    /* ---------- Owner & storage ---------- */

    public SimpleInventory getStorage() { return storage; }

    /** Called by power AFTER spawn; loads any persisted contents for this owner. */
    public void setOwner(ServerPlayerEntity player) {
        this.setOwnerUuid(player.getUuid());
        this.setCustomNameVisible(false);
        SpottedStorageState.get(player.getServer()).loadInto(player.getUuid(), storage);
    }

    public void setGenerationTag(int gen) { this.generationTag = gen; }
    public int  getGenerationTag()        { return this.generationTag; }

    /**
     * Recall: persist inventory to server state for this owner, clear live inventory to avoid dupes,
     * and despawn. Also clears the owner→buddy mapping.
     */
    public void recallToOwnerAndDrop(ServerPlayerEntity owner) {
        if (!(this.getWorld() instanceof ServerWorld sw)) { this.discard(); return; }

        this.recalling = true;

        // Save items for next summon
        SpottedStorageState.get(owner.getServer()).saveInventory(owner.getUuid(), storage);

        // Clear live inv
        for (int i = 0; i < storage.size(); i++) storage.setStack(i, ItemStack.EMPTY);

        // Clear mapping (no active body now)
        BuddyPersistentState.get(owner.getServer()).clear(owner.getUuid());

        sw.playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.1f);
        this.discard();
    }

    /* ---------- Lifecycle ---------- */

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            UUID ownerId = this.getOwnerUuid();
            var server = this.getServer();
            if (ownerId != null && server != null) {
                var state = BuddyPersistentState.get(server);
                var ref = state.get(ownerId);
                // If mapping is gone, or points elsewhere/newer gen, we are stale: remove
                if (ref == null
                        || !ref.entityUuid.equals(this.getUuid())
                        || ref.generation != this.generationTag) {
                    this.discard();
                }
            }
        }
    }
    @Override
    public void remove(RemovalReason reason) {
        if (!this.getWorld().isClient) {
            if (this.recalling) {
                // Recall path: inventory already persisted & cleared; no drops.
            } else if (reason == RemovalReason.KILLED) {
                // True death only: drop the buddy's stash and wipe the persisted copy.
                dropAll();
                UUID ownerId = this.getOwnerUuid();
                if (ownerId != null && this.getServer() != null) {
                    SpottedStorageState.get(this.getServer()).clear(ownerId);
                }
            } else {
                // Any other removal (DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER,
                // CHANGED_DIMENSION, etc.) — DO NOT DROP.
                // Optional safety: persist a snapshot so recall after a reload still has items.
                UUID ownerId = this.getOwnerUuid();
                if (ownerId != null && this.getServer() != null) {
                    SpottedStorageState.get(this.getServer()).saveInventory(ownerId, storage);
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

    /* ---------- Vanilla/Gecko ---------- */

    @Override public boolean damage(DamageSource source, float amount) { return super.damage(source, amount); }
    @Override public @Nullable PassiveEntity createChild(ServerWorld world, PassiveEntity entity) { return null; }
    @Override public EntityView method_48926() { return null; } // Fabric’s EntityView hook

    @Override
    public @Nullable LivingEntity getOwner() {
        UUID id = this.getOwnerUuid();
        if (id == null) return null;
        World w = this.getWorld();
        if (w == null) return null;
        return w.getPlayerByUuid(id);
        // (If owner offline, this returns null; that’s fine.)
    }

    /* ---------- NBT ---------- */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        DefaultedList<ItemStack> list = DefaultedList.ofSize(storage.size(), ItemStack.EMPTY);
        for (int i = 0; i < storage.size(); i++) list.set(i, storage.getStack(i));
        Inventories.writeNbt(nbt, list);
        nbt.putInt("odd_gen", this.generationTag);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        DefaultedList<ItemStack> list = DefaultedList.ofSize(storage.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, list);
        for (int i = 0; i < storage.size(); i++) storage.setStack(i, list.get(i));
        if (nbt.contains("odd_gen")) this.generationTag = nbt.getInt("odd_gen");
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
