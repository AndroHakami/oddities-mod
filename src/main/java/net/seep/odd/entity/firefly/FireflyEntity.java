package net.seep.odd.entity.firefly;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.seep.odd.Oddities;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.item.ModItems;
import net.minecraft.world.gen.structure.Structure;

public class FireflyEntity extends PathAwareEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    // === Behaviour knobs ===
    private static final int ATTRACT_RADIUS = 20;          // blocks
    private static final int PLAYER_SCAN_INTERVAL = 20;    // ticks
    private static final int WANDER_REPATH_MIN = 35;
    private static final int WANDER_REPATH_MAX = 85;

    private static final int GUIDE_SECONDS = 20;
    private static final int GUIDE_TICKS = GUIDE_SECONDS * 20;

    // Movement targets (server-side)
    private @Nullable Vec3d wanderTarget = null;
    private int wanderCd = 0;

    private @Nullable PlayerEntity attractedPlayer = null;
    private int scanCd = 0;

    private @Nullable BlockPos guideTarget = null;
    private int guideTicks = 0;
    private int guideRecalcCd = 0;

    public FireflyEntity(EntityType<? extends FireflyEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.moveControl = new FlightMoveControl(this, 8, false);
        this.setNoGravity(true);
        this.noClip = false;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28D)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.30D);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanPathThroughDoors(false);
        nav.setCanSwim(false);
        nav.setCanEnterOpenDoors(false);
        return nav;
    }

    @Override
    protected void initGoals() {
        // We drive movement ourselves; keep only look/ambient goals.
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.add(9, new LookAroundGoal(this));
    }

    /* =========================
       Core tick loop
       ========================= */
    @Override
    public void tick() {
        super.tick();

        if (!this.hasNoGravity()) this.setNoGravity(true);

        if (this.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) this.getWorld();

        // Scan for sap-holding players
        if (--scanCd <= 0) {
            scanCd = PLAYER_SCAN_INTERVAL;
            attractedPlayer = findNearestSapHolder(sw);
        }

        // Guide timer
        if (guideTicks > 0) {
            guideTicks--;

            // Re-locate occasionally (structure might be far)
            if (--guideRecalcCd <= 0) {
                guideRecalcCd = 60;
                if (guideTarget == null) {
                    guideTarget = locateNearestShroomVillage(sw, this.getBlockPos(), 96); // chunk radius
                }
            }

            // Glow trail while guiding
            sw.spawnParticles(ParticleTypes.GLOW_SQUID_INK, this.getX(), this.getY() + 0.35, this.getZ(),
                    1, 0.02, 0.02, 0.02, 0.0);

            if (guideTicks <= 0) {
                guideTarget = null;
            }
        }

        // Decide where to fly
        Vec3d target;
        double speed;

        if (guideTicks > 0 && guideTarget != null) {
            // Fly toward structure, slightly above it
            target = Vec3d.ofCenter(guideTarget).add(0.0, 8.0, 0.0);
            speed = 1.25;
        } else if (attractedPlayer != null && attractedPlayer.isAlive()
                && attractedPlayer.squaredDistanceTo(this) <= (ATTRACT_RADIUS * ATTRACT_RADIUS)) {
            // Follow the player holding sap
            target = attractedPlayer.getPos().add(0.0, 1.4, 0.0);
            speed = 1.05;
        } else {
            // Wander
            if (wanderTarget == null || --wanderCd <= 0 || this.getPos().distanceTo(wanderTarget) < 1.4) {
                wanderTarget = pickWanderTarget(this);
                wanderCd = WANDER_REPATH_MIN + this.random.nextInt(WANDER_REPATH_MAX - WANDER_REPATH_MIN + 1);
            }
            target = wanderTarget;
            speed = 0.85;
        }

        // Drive FlightMoveControl directly (reliable on 1.20.1)
        this.getMoveControl().moveTo(target.x, target.y, target.z, speed);

        // Keep it from “dropping” if it ever drifts down a bit
        if (this.isOnGround()) {
            this.addVelocity(0.0, 0.12, 0.0);
        }
    }

    private static Vec3d pickWanderTarget(FireflyEntity m) {
        Random r = m.getRandom();
        Vec3d base = m.getPos();

        double dx = (r.nextDouble() - 0.5) * 14.0;
        double dz = (r.nextDouble() - 0.5) * 14.0;

        // Prefer floating 2–6 blocks above current height
        double dy = 2.0 + r.nextDouble() * 4.0;

        double y = MathHelper.clamp(base.y + dy, -16.0, 320.0);
        return new Vec3d(base.x + dx, y, base.z + dz);
    }

    private @Nullable PlayerEntity findNearestSapHolder(ServerWorld world) {
        PlayerEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        for (PlayerEntity p : world.getPlayers()) {
            if (p == null || !p.isAlive()) continue;

            double d2 = p.squaredDistanceTo(this);
            if (d2 > (ATTRACT_RADIUS * ATTRACT_RADIUS)) continue;

            if (!isSap(p.getMainHandStack()) && !isSap(p.getOffHandStack())) continue;

            if (d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    private static boolean isSap(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // ✅ requested: Sweet Sap attracts them
        if (stack.isOf(ModItems.SWEET_SAP)) return true;

        // also allow Glow Sap item to attract (block item)
        Item glowSapItem = ModBlocks.GLOW_SAP.asItem(); // rename if your field differs
        return stack.isOf(glowSapItem);
    }

    /* =========================
       Feeding (right click)
       ========================= */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!isSap(stack)) {
            return super.interactMob(player, hand);
        }

        if (this.getWorld().isClient) {
            return ActionResult.SUCCESS; // let client animate hand swing
        }

        // Consume 1 (unless creative)
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        ServerWorld sw = (ServerWorld) this.getWorld();

        // Hearts burst
        sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.6, this.getZ(),
                6, 0.25, 0.25, 0.25, 0.02);

        this.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.2F);

        // Start guiding for 20s toward nearest shroom village
        this.guideTicks = GUIDE_TICKS;
        this.guideRecalcCd = 0;
        this.guideTarget = locateNearestShroomVillage(sw, this.getBlockPos(), 96);

        // While guiding, stop following player
        this.attractedPlayer = null;

        return ActionResult.CONSUME;
    }

    private static @Nullable BlockPos locateNearestShroomVillage(ServerWorld world, BlockPos center, int chunkRadius) {
        var structureReg = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        RegistryKey<Structure> key = RegistryKey.of(
                RegistryKeys.STRUCTURE,
                new Identifier(Oddities.MOD_ID, "shroom_village")
        );

        var entryOpt = structureReg.getEntry(key);
        if (entryOpt.isEmpty()) return null;

        RegistryEntryList<Structure> list = RegistryEntryList.of(entryOpt.get());

        // ChunkGenerator.locateStructure(world, list, center, radius, skipReferencedStructures) :contentReference[oaicite:3]{index=3}
        Pair<BlockPos, net.minecraft.registry.entry.RegistryEntry<Structure>> found =
                world.getChunkManager().getChunkGenerator().locateStructure(world, list, center, chunkRadius, false);

        return (found == null) ? null : found.getFirst();
    }

    /* ---------- Physics safety ---------- */
    @Override
    protected void fall(double heightDiff, boolean onGround, net.minecraft.block.BlockState state, BlockPos pos) { }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, net.minecraft.entity.damage.DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    /* ---------- GeckoLib ---------- */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "firefly.controller", 0,
                state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}