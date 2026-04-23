package net.seep.odd.item.outerblaster;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.entity.outerblaster.BlasterProjectileEntity;
import net.seep.odd.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OuterBlasterItem extends Item implements GeoItem {
    private static final String NBT_HEAT = "outer_blaster_heat";
    private static final String NBT_HEAT_TIME = "outer_blaster_heat_time";
    private static final String NBT_HEAT_MODE_HELD = "outer_blaster_heat_mode_held";
    private static final String NBT_OVERHEATED = "outer_blaster_overheated";
    private static final String NBT_OVERHEAT_END = "outer_blaster_overheat_end";

    public static final int MAX_HEAT = 100;
    public static final int HEAT_PER_SHOT = 22;

    public static final float COOL_PER_TICK_HELD = 1.10f;
    public static final float COOL_PER_TICK_IDLE = 0.45f;

    /** fire anim is ~0.29s, requested actual fire rate = 0.25s */
    public static final int FIRE_ANIM_TICKS = 6;
    public static final int FIRE_COOLDOWN_TICKS = 5;

    /** full lockout until heat reaches 0 again */
    public static final int OVERHEAT_RECOVERY_TICKS = 34;

    private static final float PROJECTILE_SPEED = 3.2f;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("fire");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final EntityType<? extends BlasterProjectileEntity> projectileType;

    private static int fireMainTicks = 0;
    private static int fireOffTicks = 0;

    public OuterBlasterItem(Settings settings, EntityType<? extends BlasterProjectileEntity> projectileType) {
        super(settings);
        this.projectileType = projectileType;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static void initClientHooks() {
        net.seep.odd.item.outerblaster.client.OuterBlasterClient.init();
    }

    public static void clientStartFireAnim(Hand hand) {
        if (hand == Hand.MAIN_HAND) fireMainTicks = FIRE_ANIM_TICKS;
        else fireOffTicks = FIRE_ANIM_TICKS;
    }

    public static void clientTickDownAnimCounters() {
        if (fireMainTicks > 0) fireMainTicks--;
        if (fireOffTicks > 0) fireOffTicks--;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(Items.NETHERITE_INGOT) || super.canRepair(stack, ingredient);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            if (!canClientAttemptFire(user, stack)) {
                return TypedActionResult.fail(stack);
            }

            clientStartFireAnim(hand);
            return TypedActionResult.success(stack, false);
        }

        if (user instanceof ServerPlayerEntity sp) {
            boolean fired = tryFire(sp, stack, hand);
            return fired ? TypedActionResult.success(stack, false) : TypedActionResult.fail(stack);
        }

        return TypedActionResult.pass(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(entity instanceof PlayerEntity player)) return;

        boolean activelyHeld = isActivelyHeldBy(player, stack);
        long now = world.getTime();

        NbtCompound nbt = stack.getOrCreateNbt();
        ensureTracking(nbt, now, activelyHeld);

        clearOverheatIfExpired(nbt, now, activelyHeld);

        if (isOverheatedAt(nbt, now)) {
            if (activelyHeld && world.getTime() % 3L == 0L) {
                spawnOverheatSmoke((ServerWorld) world, player, getHeldHand(player, stack));
            }
            return;
        }

        boolean storedHeldMode = nbt.getBoolean(NBT_HEAT_MODE_HELD);
        if (storedHeldMode != activelyHeld) {
            float heatNow = computeHeatAt(nbt, now, storedHeldMode);
            setNormalHeatSnapshot(nbt, heatNow, now, activelyHeld);
        }
    }

    private boolean canClientAttemptFire(PlayerEntity player, ItemStack stack) {
        if (player.getItemCooldownManager().isCoolingDown(this)) return false;
        if (isOverheated(stack)) return false;
        if (stack.isDamageable() && stack.getDamage() >= stack.getMaxDamage() - 1) return false;
        return true;
    }

    private boolean tryFire(ServerPlayerEntity player, ItemStack stack, Hand hand) {
        if (player.getItemCooldownManager().isCoolingDown(this)) return false;
        if (stack.isDamageable() && stack.getDamage() >= stack.getMaxDamage() - 1) return false;

        long now = player.getWorld().getTime();
        NbtCompound nbt = stack.getOrCreateNbt();

        ensureTracking(nbt, now, true);
        clearOverheatIfExpired(nbt, now, true);

        if (isOverheatedAt(nbt, now)) {
            player.sendMessage(Text.literal("Overheated"), true);
            return false;
        }

        float heatBefore = computeHeatAt(nbt, now, true);
        float heatAfter = Math.min(MAX_HEAT, heatBefore + HEAT_PER_SHOT);

        setNormalHeatSnapshot(nbt, heatAfter, now, true);

        player.getItemCooldownManager().set(this, FIRE_COOLDOWN_TICKS);

        spawnProjectile((ServerWorld) player.getWorld(), player, hand);

        float pitch = MathHelper.lerp(heatAfter / (float) MAX_HEAT, 0.92f, 1.55f);
        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.OUTER_BLASTER_FIRE,
                SoundCategory.PLAYERS,
                0.95f,
                pitch
        );

        stack.damage(1, player, p -> p.sendToolBreakStatus(hand));

        if (!stack.isEmpty() && heatAfter >= MAX_HEAT) {
            triggerOverheat((ServerWorld) player.getWorld(), player, stack, hand, now);
        }

        return true;
    }

    private void spawnProjectile(ServerWorld world, ServerPlayerEntity player, Hand hand) {
        BlasterProjectileEntity projectile = this.projectileType.create(world);
        if (projectile == null) return;

        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d up = new Vec3d(0.0, 1.0, 0.0);
        Vec3d right = look.crossProduct(up);

        if (right.lengthSquared() < 1.0E-6) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }

        double side = hand == Hand.MAIN_HAND ? -0.24 : 0.24;
        Vec3d muzzle = player.getEyePos()
                .add(look.multiply(0.72))
                .add(right.multiply(side))
                .add(0.0, -0.16, 0.0);

        projectile.setOwner(player);
        projectile.setPos(muzzle.x, muzzle.y, muzzle.z);
        projectile.setVelocity(look.multiply(PROJECTILE_SPEED));
        projectile.syncRotationToVelocity();

        world.spawnEntity(projectile);
    }

    private void triggerOverheat(ServerWorld world, ServerPlayerEntity player, ItemStack stack, Hand hand, long now) {
        NbtCompound nbt = stack.getOrCreateNbt();

        nbt.putBoolean(NBT_OVERHEATED, true);
        nbt.putLong(NBT_OVERHEAT_END, now + OVERHEAT_RECOVERY_TICKS);
        nbt.putFloat(NBT_HEAT, MAX_HEAT);
        nbt.putLong(NBT_HEAT_TIME, now);
        nbt.putBoolean(NBT_HEAT_MODE_HELD, true);

        player.getItemCooldownManager().set(this, OVERHEAT_RECOVERY_TICKS);

        world.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.OUTER_BLASTER_OVERHEAT,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
        );

        for (int i = 0; i < 10; i++) {
            spawnOverheatSmoke(world, player, hand);
        }
    }

    private void spawnOverheatSmoke(ServerWorld world, PlayerEntity player, Hand hand) {
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d up = new Vec3d(0.0, 1.0, 0.0);
        Vec3d right = look.crossProduct(up);

        if (right.lengthSquared() < 1.0E-6) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }

        double side = hand == Hand.MAIN_HAND ? -0.22 : 0.22;
        Vec3d muzzle = player.getEyePos()
                .add(look.multiply(0.65))
                .add(right.multiply(side))
                .add(0.0, -0.18, 0.0);

        world.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.03, 0.03, 0.01);
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 1, 0.02, 0.02, 0.02, 0.004);
    }

    private static boolean isActivelyHeldBy(PlayerEntity player, ItemStack stack) {
        return ItemStack.areEqual(stack, player.getMainHandStack()) || ItemStack.areEqual(stack, player.getOffHandStack());
    }

    private static Hand getHeldHand(PlayerEntity player, ItemStack stack) {
        if (ItemStack.areEqual(stack, player.getOffHandStack())) return Hand.OFF_HAND;
        return Hand.MAIN_HAND;
    }

    private static void ensureTracking(NbtCompound nbt, long now, boolean heldMode) {
        if (!nbt.contains(NBT_HEAT)) nbt.putFloat(NBT_HEAT, 0.0f);
        if (!nbt.contains(NBT_HEAT_TIME)) nbt.putLong(NBT_HEAT_TIME, now);
        if (!nbt.contains(NBT_HEAT_MODE_HELD)) nbt.putBoolean(NBT_HEAT_MODE_HELD, heldMode);
        if (!nbt.contains(NBT_OVERHEATED)) nbt.putBoolean(NBT_OVERHEATED, false);
        if (!nbt.contains(NBT_OVERHEAT_END)) nbt.putLong(NBT_OVERHEAT_END, 0L);
    }

    private static void setNormalHeatSnapshot(NbtCompound nbt, float heat, long now, boolean heldMode) {
        nbt.putFloat(NBT_HEAT, MathHelper.clamp(heat, 0.0f, MAX_HEAT));
        nbt.putLong(NBT_HEAT_TIME, now);
        nbt.putBoolean(NBT_HEAT_MODE_HELD, heldMode);
    }

    private static void clearOverheatIfExpired(NbtCompound nbt, long now, boolean heldMode) {
        if (nbt.getBoolean(NBT_OVERHEATED) && now >= nbt.getLong(NBT_OVERHEAT_END)) {
            nbt.putBoolean(NBT_OVERHEATED, false);
            nbt.putLong(NBT_OVERHEAT_END, 0L);
            setNormalHeatSnapshot(nbt, 0.0f, now, heldMode);
        }
    }

    private static boolean isOverheatedAt(NbtCompound nbt, long now) {
        return nbt.getBoolean(NBT_OVERHEATED) && now < nbt.getLong(NBT_OVERHEAT_END);
    }

    private static float computeHeatAt(NbtCompound nbt, long now, boolean heldModeFallback) {
        if (nbt == null) return 0.0f;

        if (isOverheatedAt(nbt, now)) {
            long end = nbt.getLong(NBT_OVERHEAT_END);
            float remain01 = MathHelper.clamp((end - now) / (float) OVERHEAT_RECOVERY_TICKS, 0.0f, 1.0f);
            return MAX_HEAT * remain01;
        }

        float storedHeat = nbt.getFloat(NBT_HEAT);
        long storedTime = nbt.contains(NBT_HEAT_TIME) ? nbt.getLong(NBT_HEAT_TIME) : now;
        boolean storedHeldMode = nbt.contains(NBT_HEAT_MODE_HELD) ? nbt.getBoolean(NBT_HEAT_MODE_HELD) : heldModeFallback;

        long elapsed = Math.max(0L, now - storedTime);
        float cooling = storedHeldMode ? COOL_PER_TICK_HELD : COOL_PER_TICK_IDLE;

        return Math.max(0.0f, storedHeat - (elapsed * cooling));
    }

    public static float getHeat(ItemStack stack) {
        if (stack == null || !stack.hasNbt()) return 0.0f;

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return ClientAccess.getDisplayHeat(stack);
        }

        return stack.getNbt().getFloat(NBT_HEAT);
    }

    public static int getOverheatTicks(ItemStack stack) {
        if (stack == null || !stack.hasNbt()) return 0;

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return ClientAccess.getDisplayOverheatTicks(stack);
        }

        return stack.getNbt().getBoolean(NBT_OVERHEATED) ? 1 : 0;
    }

    public static boolean isOverheated(ItemStack stack) {
        if (stack == null || !stack.hasNbt()) return false;

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return ClientAccess.isDisplayOverheated(stack);
        }

        return stack.getNbt().getBoolean(NBT_OVERHEATED);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", state -> {
            Object ent = state.getData(DataTickets.ENTITY);
            PlayerEntity player = ent instanceof PlayerEntity p ? p : null;

            boolean anyFire = fireMainTicks > 0 || fireOffTicks > 0;

            if (player != null && player.getWorld() != null && player.getWorld().isClient) {
                ItemStack renderStack = state.getData(DataTickets.ITEMSTACK);

                boolean mainHeld = player.getMainHandStack().isOf(this);
                boolean offHeld = player.getOffHandStack().isOf(this);

                boolean isMain = false;
                boolean isOff = false;

                if (renderStack != null) {
                    if (mainHeld && ItemStack.areEqual(renderStack, player.getMainHandStack())) isMain = true;
                    if (offHeld && ItemStack.areEqual(renderStack, player.getOffHandStack())) isOff = true;
                }

                if (!isMain && !isOff) {
                    if (mainHeld && !offHeld) isMain = true;
                    else if (offHeld && !mainHeld) isOff = true;
                    else if (mainHeld) isMain = true;
                }

                boolean playFire =
                        (isMain && fireMainTicks > 0) ||
                                (isOff && fireOffTicks > 0) ||
                                (anyFire && !isMain && !isOff);

                if (playFire) state.setAndContinue(FIRE);
                else state.setAndContinue(IDLE);

                return PlayState.CONTINUE;
            }

            if (anyFire) state.setAndContinue(FIRE);
            else state.setAndContinue(IDLE);

            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Environment(EnvType.CLIENT)
    private Supplier<Object> renderProvider;

    @Environment(EnvType.CLIENT)
    @Override
    public Supplier<Object> getRenderProvider() {
        if (this.renderProvider == null) {
            this.renderProvider = GeoItem.makeRenderer(this);
        }
        return this.renderProvider;
    }

    @Environment(EnvType.CLIENT)
    @Override
    public double getTick(Object animatable) {
        return RenderUtils.getCurrentTick();
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null) {
                    renderer = new net.seep.odd.item.outerblaster.client.OuterBlasterRenderer();
                }
                return renderer;
            }
        });
    }

    @Environment(EnvType.CLIENT)
    private static final class ClientAccess {
        private static boolean isHeldByClient(ItemStack stack, PlayerEntity player) {
            return ItemStack.areEqual(stack, player.getMainHandStack()) || ItemStack.areEqual(stack, player.getOffHandStack());
        }

        private static float getDisplayHeat(ItemStack stack) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null || !stack.hasNbt()) {
                return stack.hasNbt() ? stack.getNbt().getFloat(NBT_HEAT) : 0.0f;
            }

            return computeHeatAt(stack.getNbt(), mc.world.getTime(), isHeldByClient(stack, mc.player));
        }

        private static int getDisplayOverheatTicks(ItemStack stack) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.world == null || !stack.hasNbt()) return 0;

            NbtCompound nbt = stack.getNbt();
            if (!isOverheatedAt(nbt, mc.world.getTime())) return 0;

            return (int) Math.max(0L, nbt.getLong(NBT_OVERHEAT_END) - mc.world.getTime());
        }

        private static boolean isDisplayOverheated(ItemStack stack) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.world == null || !stack.hasNbt()) return false;

            return isOverheatedAt(stack.getNbt(), mc.world.getTime());
        }
    }
}