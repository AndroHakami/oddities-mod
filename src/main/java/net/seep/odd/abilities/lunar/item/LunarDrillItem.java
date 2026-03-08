// FILE: src/main/java/net/seep/odd/abilities/lunar/item/LunarDrillItem.java
package net.seep.odd.abilities.lunar.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.lunar.client.LunarDrillPreview;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

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

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LunarDrillItem extends Item implements GeoItem {
    /* ===================================================================================== */
    /* ==============================  EASY TWEAK SECTION  ================================= */
    /* ===================================================================================== */

    public static final float MAX_HEAT = 100f;

    public static final float HEAT_PER_LMB_BLOCK = 2.25f;
    public static final float HEAT_BURST_FLAT = 3.0f;
    public static final float HEAT_PER_BURST_BLOCK = 0.50f;
    public static final float COOL_PER_TICK = 0.17f;
    public static final int HARD_LOCK_TICKS = 20 * 15;

    /* ===================================================================================== */
    /* =============================  Pattern/Depth (5x5)  ================================= */
    /* ===================================================================================== */

    public static final int PATTERN_SIZE = 5;
    public static final int PATTERN_BITS = PATTERN_SIZE * PATTERN_SIZE; // 25
    public static final int PATTERN_CENTER = PATTERN_SIZE / 2; // 2

    public static final String NBT_PATTERN = "LunarPattern64";
    public static final String NBT_DEPTH   = "lunar_depth";

    private static final String NBT_RAMP         = "lunar_ramp";
    private static final String NBT_LAST_BREAK_T = "lunar_lastBreakT";

    private static final String NBT_HAND = "odd_drill_hand";

    private static final String NBT_HEAT         = "odd_heat";
    private static final String NBT_HARDLOCK_END = "odd_heatHardEnd";
    private static final String NBT_LAST_HEAT_T  = "odd_heatLastT";

    private static final int   MAX_USE_T          = 72000;
    private static final int   CHARGE_BASE_T      = 16;
    private static final float CHARGE_PER_BLOCK_T = 0.8f;
    private static final double PREVIEW_REACH     = 5.5;

    private static final float RAMP_ADD_PER_BREAK   = 1.5f;
    private static final float RAMP_MAX             = 18.0f;
    private static final int   RAMP_IDLE_HALFLIFE_T = 30;
    private static final float BASE_MINING_SPEED    = 13.0f;

    private static final String REQUIRED_POWER_ID = "lunar";

    private static boolean canUseDrill(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return true;
        if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) return false;
        return REQUIRED_POWER_ID.equals(PowerAPI.get(sp));
    }

    private static void denyUseServer(World world, ServerPlayerEntity sp, String msg) {
        warnOncePerSec(world, sp, msg);
        sp.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.6f, 0.6f);
        sp.stopUsingItem();
    }

    /* -------- GeckoLib -------- */
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    private static final RawAnimation IDLE           = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation DRILL_WORK     = RawAnimation.begin().thenLoop("drill");
    private static final RawAnimation DRILL_CHARGING = RawAnimation.begin().thenLoop("drill_charging");
    private static final RawAnimation DRILL_BURST    = RawAnimation.begin().thenPlay("drill_burst");

    public LunarDrillItem(Settings settings) {
        super(settings.maxCount(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /* ===================================================================================== */
    /* ==================================  SERVER STATE  =================================== */
    /* ===================================================================================== */

    private static final Map<UUID, Long> BURST_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> WARN_UNTIL  = new HashMap<>();

    private static boolean isBurstBreaking(ServerPlayerEntity sp, World world) {
        return BURST_UNTIL.getOrDefault(sp.getUuid(), 0L) >= world.getTime();
    }

    private static void setBurstWindow(ServerPlayerEntity sp, World world, int ticks) {
        BURST_UNTIL.put(sp.getUuid(), world.getTime() + ticks);
    }

    private static void clearBurstWindow(ServerPlayerEntity sp) {
        BURST_UNTIL.remove(sp.getUuid());
    }

    private static void warnOncePerSec(World world, PlayerEntity p, String msg) {
        if (!(p instanceof ServerPlayerEntity sp)) return;
        long now = world.getTime();
        long nextOk = WARN_UNTIL.getOrDefault(sp.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(sp.getUuid(), now + 20);
        sp.sendMessage(Text.literal(msg), true);
    }

    private static boolean isHardLocked(ItemStack stack, World world) {
        long end = stack.getOrCreateNbt().getLong(NBT_HARDLOCK_END);
        return end > 0 && world.getTime() < end;
    }

    private static void coolHeat(World world, ItemStack stack) {
        var nbt = stack.getOrCreateNbt();
        long now = world.getTime();

        long last = nbt.getLong(NBT_LAST_HEAT_T);
        if (last == 0) last = now;

        float heat = nbt.getFloat(NBT_HEAT);

        if (!isHardLocked(stack, world)) {
            long dt = Math.max(0, now - last);
            if (dt > 0) {
                heat = Math.max(0f, heat - COOL_PER_TICK * dt);
            }
        } else {
            heat = MAX_HEAT;
        }

        nbt.putFloat(NBT_HEAT, MathHelper.clamp(heat, 0f, MAX_HEAT));
        nbt.putLong(NBT_LAST_HEAT_T, now);

        long hardEnd = nbt.getLong(NBT_HARDLOCK_END);
        if (hardEnd != 0 && now >= hardEnd) nbt.putLong(NBT_HARDLOCK_END, 0);
    }

    private static void addHeat(World world, PlayerEntity p, ItemStack stack, float amt) {
        coolHeat(world, stack);

        var nbt = stack.getOrCreateNbt();
        float heat = nbt.getFloat(NBT_HEAT) + amt;

        if (heat >= MAX_HEAT) {
            heat = MAX_HEAT;
            nbt.putFloat(NBT_HEAT, heat);
            nbt.putLong(NBT_HARDLOCK_END, world.getTime() + HARD_LOCK_TICKS);

            p.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.9f);
            warnOncePerSec(world, p, "§cDrill overheated! Cooling down...");
            return;
        }

        nbt.putFloat(NBT_HEAT, heat);
    }

    private static ItemStack getHeldDrill(PlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        if (main.getItem() instanceof LunarDrillItem) return main;
        ItemStack off = p.getOffHandStack();
        if (off.getItem() instanceof LunarDrillItem) return off;
        return ItemStack.EMPTY;
    }

    /* ===================================================================================== */
    /* =============================  PICKAXE (LMB)  + RAMP  =============================== */
    /* ===================================================================================== */

    @Override public boolean isSuitableFor(BlockState state) { return state.isIn(BlockTags.PICKAXE_MINEABLE); }

    @Override
    public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        if (!state.isIn(BlockTags.PICKAXE_MINEABLE)) return 1.0f;
        return BASE_MINING_SPEED + stack.getOrCreateNbt().getFloat(NBT_RAMP);
    }

    private static boolean hooksRegistered = false;

    public static void registerHooks() {
        if (hooksRegistered) return;
        hooksRegistered = true;

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (world.isClient) return true;
            if (!(player instanceof ServerPlayerEntity sp)) return true;

            ItemStack drill = getHeldDrill(sp);
            if (!(drill.getItem() instanceof LunarDrillItem)) return true;

            if (!canUseDrill(sp)) {
                denyUseServer(world, sp, "§cOnly Lunar can use this.");
                return false;
            }

            if (isBurstBreaking(sp, world)) return true;

            coolHeat(world, drill);

            if (isHardLocked(drill, world)) {
                long end = drill.getOrCreateNbt().getLong(NBT_HARDLOCK_END);
                int secs = (int) Math.ceil(Math.max(0, end - world.getTime()) / 20.0);
                warnOncePerSec(world, sp, "§cOverheated (" + secs + "s)");
                sp.playSound(SoundEvents.BLOCK_DISPENSER_FAIL, 0.6f, 0.8f);
                return false;
            }

            return true;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world.isClient) return;
            if (!(player instanceof ServerPlayerEntity sp)) return;

            ItemStack drill = getHeldDrill(sp);
            if (!(drill.getItem() instanceof LunarDrillItem item)) return;

            if (!canUseDrill(sp)) return;
            if (isBurstBreaking(sp, world)) return;

            addHeat(world, sp, drill, HEAT_PER_LMB_BLOCK);

            var nbt  = drill.getOrCreateNbt();
            long now = world.getTime();
            long last = nbt.getLong(NBT_LAST_BREAK_T);
            float ramp = nbt.getFloat(NBT_RAMP);

            if (now - last > RAMP_IDLE_HALFLIFE_T) ramp *= 0.5f;
            ramp = Math.min(RAMP_MAX, ramp + RAMP_ADD_PER_BREAK);

            nbt.putFloat(NBT_RAMP, ramp);
            nbt.putLong(NBT_LAST_BREAK_T, now);

            item.triggerAnim(sp, Hand.MAIN_HAND.ordinal(), "main", "burst");
        });
    }

    /* ===================================================================================== */
    /* =============================  RMB CHARGE + (SATIN) WORLD PREVIEW  =================== */
    /* ===================================================================================== */

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient && user instanceof ServerPlayerEntity sp && !canUseDrill(sp)) {
            denyUseServer(world, sp, "§cOnly Lunar can use this.");
            return TypedActionResult.fail(stack);
        }

        if (isHardLocked(stack, world)) return TypedActionResult.fail(stack);
        if (!world.isClient) coolHeat(world, stack);

        if (user.isSneaking()) {
            if (world.isClient && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                Client.openPatternScreen(hand);
            }
            return TypedActionResult.success(stack, world.isClient);
        }

        if (!world.isClient) stack.getOrCreateNbt().putInt(NBT_HAND, hand.ordinal());
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        if (ctx.getPlayer() != null && !ctx.getPlayer().isSneaking()) {
            PlayerEntity p = ctx.getPlayer();
            ItemStack stack = ctx.getStack();

            if (!ctx.getWorld().isClient && p instanceof ServerPlayerEntity sp && !canUseDrill(sp)) {
                denyUseServer(ctx.getWorld(), sp, "§cOnly Lunar can use this.");
                return ActionResult.FAIL;
            }

            if (isHardLocked(stack, ctx.getWorld())) return ActionResult.FAIL;

            if (!ctx.getWorld().isClient) {
                coolHeat(ctx.getWorld(), stack);
                stack.getOrCreateNbt().putInt(NBT_HAND, ctx.getHand().ordinal());
            }

            p.setCurrentHand(ctx.getHand());
            return ActionResult.CONSUME;
        }
        return super.useOnBlock(ctx);
    }

    @Override public int getMaxUseTime(ItemStack stack) { return MAX_USE_T; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }

    @Override
    public void usageTick(World world, net.minecraft.entity.LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient && user instanceof ServerPlayerEntity sp && !canUseDrill(sp)) {
            sp.stopUsingItem();
            return;
        }

        if (world.isClient && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            Client.usageTickClient(world, user, stack, remainingUseTicks);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, net.minecraft.entity.LivingEntity user, int remainingUseTicks) {
        if (world.isClient) {
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                Client.onStoppedUsingClient(user); // ✅ fix: don’t let remote players clear your local preview
            }
            return;
        }

        if (!(user instanceof ServerPlayerEntity sp)) return;

        if (!canUseDrill(sp)) return;

        coolHeat(world, stack);
        if (isHardLocked(stack, world)) return;

        BlockHitResult bhr = rayToBlock(world, sp, PREVIEW_REACH);
        if (bhr == null || bhr.getType() != HitResult.Type.BLOCK) return;

        Direction face = bhr.getSide();
        BlockPos origin = bhr.getBlockPos();
        Direction horiz = sp.getHorizontalFacing();

        var nbt = stack.getOrCreateNbt();
        long mask = normalizeMask(nbt.getLong(NBT_PATTERN));
        int depth = clampDepth(nbt.getInt(NBT_DEPTH));

        int targetedBlocks = Math.max(1, Long.bitCount(mask) * depth);
        int used  = MAX_USE_T - remainingUseTicks;
        int need  = (int) Math.ceil(CHARGE_BASE_T + CHARGE_PER_BLOCK_T * targetedBlocks);

        if (used < need) {
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6f, 0.9f);
            return;
        }

        boolean[][] grid = bitsToGrid(mask);
        List<BlockPos> targets = layoutPatternWithDepth(origin, face, horiz, grid, depth);
        Set<BlockPos> unique = new LinkedHashSet<>(targets);

        setBurstWindow(sp, world, 6);

        int broke = 0;
        for (BlockPos tp : unique) {
            BlockState bs = world.getBlockState(tp);
            if (!bs.isAir() && bs.getHardness(world, tp) >= 0) {
                world.breakBlock(tp, true, sp);
                broke++;
            }
        }

        clearBurstWindow(sp);

        if (broke > 0) {
            addHeat(world, sp, stack, HEAT_BURST_FLAT + (broke * HEAT_PER_BURST_BLOCK));

            int handId = nbt.getInt(NBT_HAND);
            this.triggerAnim(sp, handId, "main", "burst");
            world.playSound(null, origin, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 0.8f, 1.1f);
        }
    }

    private static BlockHitResult rayToBlock(World w, PlayerEntity p, double reach) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(reach));
        return w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
    }

    public static long normalizeMask(long raw) {
        long mask25 = raw & ((1L << PATTERN_BITS) - 1L);
        int centerIdx = PATTERN_CENTER * PATTERN_SIZE + PATTERN_CENTER;
        mask25 |= (1L << centerIdx);
        return mask25;
    }

    public static int clampDepth(int d) {
        if (d <= 0) return 1;
        return MathHelper.clamp(d, 1, 6);
    }

    public static boolean[][] bitsToGrid(long bits) {
        boolean[][] g = new boolean[PATTERN_SIZE][PATTERN_SIZE];
        bits = normalizeMask(bits);
        for (int r = 0; r < PATTERN_SIZE; r++) for (int c = 0; c < PATTERN_SIZE; c++) {
            int i = r * PATTERN_SIZE + c;
            g[r][c] = ((bits >>> i) & 1L) == 1L;
        }
        return g;
    }

    private static List<BlockPos> layoutPatternWithDepth(BlockPos origin, Direction face, Direction horizontal, boolean[][] grid, int depth) {
        depth = clampDepth(depth);

        Direction u, v;
        switch (face) {
            case NORTH -> { u = Direction.EAST;  v = Direction.UP; }
            case SOUTH -> { u = Direction.WEST;  v = Direction.UP; }
            case WEST  -> { u = Direction.SOUTH; v = Direction.UP; }
            case EAST  -> { u = Direction.NORTH; v = Direction.UP; }
            default -> {
                switch (horizontal) {
                    case NORTH -> { u = Direction.EAST;  v = Direction.NORTH; }
                    case SOUTH -> { u = Direction.WEST;  v = Direction.SOUTH; }
                    case WEST  -> { u = Direction.SOUTH; v = Direction.WEST; }
                    default    -> { u = Direction.NORTH; v = Direction.EAST; }
                }
            }
        }

        Direction into = face.getOpposite();

        List<BlockPos> out = new ArrayList<>(PATTERN_BITS * depth);
        for (int r = 0; r < PATTERN_SIZE; r++) for (int c = 0; c < PATTERN_SIZE; c++) {
            if (!grid[r][c]) continue;

            int dr = r - PATTERN_CENTER;
            int dc = c - PATTERN_CENTER;

            BlockPos base = origin.offset(u, dc).offset(v, -dr);

            for (int k = 0; k < depth; k++) {
                out.add(base.offset(into, k));
            }
        }
        return out;
    }

    /* ===================================================================================== */
    /* ============================  CLIENT STATE (primitives only)  ======================= */
    /* ===================================================================================== */

    private static int chargeMainTicks = 0, chargeOffTicks = 0;
    private static int drillMainTicks  = 0, drillOffTicks  = 0;

    /* ===================================================================================== */
    /* ===============================  GECKOLIB SETUP  ==================================== */
    /* ===================================================================================== */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrs) {
        AnimationController<LunarDrillItem> ctrl =
                new AnimationController<>(this, "main", 8, state -> {
                    if (state.getController().isPlayingTriggeredAnimation()) {
                        return PlayState.CONTINUE;
                    }

                    ItemStack stack = state.getData(DataTickets.ITEMSTACK);
                    Entity ent = state.getData(DataTickets.ENTITY);
                    if (!(ent instanceof PlayerEntity p) || stack == null) {
                        state.setAndContinue(IDLE);
                        return PlayState.CONTINUE;
                    }

                    boolean main = p.getMainHandStack().getItem() instanceof LunarDrillItem
                            && ItemStack.areEqual(p.getMainHandStack(), stack);
                    boolean off  = p.getOffHandStack().getItem() instanceof LunarDrillItem
                            && ItemStack.areEqual(p.getOffHandStack(), stack);

                    boolean charging = (main && chargeMainTicks > 0) || (off && chargeOffTicks > 0);
                    boolean drilling = (main && drillMainTicks > 0) || (off && drillOffTicks > 0);

                    if (charging) state.setAndContinue(DRILL_CHARGING);
                    else if (drilling) state.setAndContinue(DRILL_WORK);
                    else state.setAndContinue(IDLE);

                    return PlayState.CONTINUE;
                });

        ctrl.triggerableAnim("burst", DRILL_BURST);
        ctrs.add(ctrl);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public double getTick(Object animatable) { return RenderUtils.getCurrentTick(); }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null) {
                    renderer = Client.createRendererReflective();
                }
                return renderer;
            }
        });
    }

    /* ===================================================================================== */
    /* ===============================  CLIENT ENTRY POINT  ================================= */
    /* ===================================================================================== */

    public static void initClientHooks() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        Client.initClientHooks();
        net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.init();
    }

    /* ===================================================================================== */
    /* ===============================  CLIENT IMPLEMENTATION  ============================== */
    /* ===================================================================================== */

    @Environment(EnvType.CLIENT)
    private static final class Client {
        private Client() {}

        private static boolean hooksInit = false;
        private static boolean hudInit   = false;

        private static LoopForPlayer chargeLoop = null;
        private static LoopForPlayer drillLoop  = null;
        private static boolean chargeReadyLatched = false;

        private static float hudHeatLatch = -1f;
        private static long  hudLatchT = -1L;

        // ✅ NEW: who is currently “owning” the world preview on THIS client
        private static UUID previewOwner = null;
        private static final double OBS_PREVIEW_RANGE_SQ = 64.0 * 64.0;

        static void openPatternScreen(Hand hand) {
            final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null) return;
            mc.setScreen(new net.seep.odd.abilities.lunar.client.screen.LunarPatternScreen(hand));
        }

        static void initClientHooks() {
            if (hooksInit) return;
            hooksInit = true;

            if (!hudInit) {
                hudInit = true;
                net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT
                        .register(Client::renderOverheatHud);
            }

            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.player == null || client.world == null) {
                    stopChargeLoop();
                    stopDrillLoop();
                    LunarDrillPreview.setChargeProgress(0f);
                    net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.clear();
                    previewOwner = null;
                    chargeReadyLatched = false;
                    chargeMainTicks = chargeOffTicks = drillMainTicks = drillOffTicks = 0;
                    return;
                }

                if (chargeMainTicks > 0) chargeMainTicks--;
                if (chargeOffTicks  > 0) chargeOffTicks--;
                if (drillMainTicks  > 0) drillMainTicks--;
                if (drillOffTicks   > 0) drillOffTicks--;

                net.minecraft.client.network.ClientPlayerEntity p = client.player;

                ItemStack main = p.getMainHandStack();
                ItemStack off  = p.getOffHandStack();

                boolean mainIsDrill = main.getItem() instanceof LunarDrillItem;
                boolean offIsDrill  = off.getItem()  instanceof LunarDrillItem;

                boolean using = p.isUsingItem() && (p.getActiveItem().getItem() instanceof LunarDrillItem);
                if (using) {
                    if (p.getActiveHand() == Hand.MAIN_HAND) chargeMainTicks = 2;
                    else chargeOffTicks = 2;
                } else {
                    stopChargeLoop();
                    LunarDrillPreview.setChargeProgress(0f);

                    // ✅ only clear preview if WE owned it (prevents remote-user stop from wiping local/remote)
                    if (previewOwner != null && previewOwner.equals(p.getUuid())) {
                        net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.clear();
                        previewOwner = null;
                    }

                    chargeReadyLatched = false;
                }

                boolean attackHeld = client.options.attackKey.isPressed();
                if (attackHeld && mainIsDrill) drillMainTicks = 2;
                if (attackHeld && offIsDrill)  drillOffTicks  = 2;

                if (attackHeld && (mainIsDrill || offIsDrill)) {
                    p.handSwinging = false;
                    p.handSwingTicks = 0;
                }

                ItemStack held = mainIsDrill ? main : (offIsDrill ? off : ItemStack.EMPTY);
                boolean hard = !held.isEmpty() && (client.world.getTime() < held.getOrCreateNbt().getLong(NBT_HARDLOCK_END));

                if (attackHeld && (mainIsDrill || offIsDrill) && !hard) {
                    ItemStack s = mainIsDrill ? main : off;
                    float ramp = s.getOrCreateNbt().getFloat(NBT_RAMP);
                    float pitch = 1.0f + MathHelper.clamp(ramp * 0.06f, 0f, 0.8f);
                    startOrUpdateDrillLoop(p, pitch);
                } else {
                    stopDrillLoop();
                }
            });
        }

        static void usageTickClient(World world, net.minecraft.entity.LivingEntity user, ItemStack stack, int remainingUseTicks) {
            if (!(user instanceof PlayerEntity p)) return;

            long hardEnd = stack.getOrCreateNbt().getLong(NBT_HARDLOCK_END);
            if (hardEnd > 0 && world.getTime() < hardEnd) {
                // Only clear if this preview is owned by THIS user
                if (previewOwner != null && previewOwner.equals(p.getUuid())) {
                    net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.clear();
                    previewOwner = null;
                }
                if (isLocalPlayer(p)) {
                    LunarDrillPreview.setChargeProgress(0f);
                    chargeReadyLatched = false;
                }
                return;
            }

            // Ray from THIS player (works for remote players too because their yaw/pitch are synced)
            BlockHitResult bhr = rayToBlock(world, p, PREVIEW_REACH);
            if (bhr == null || bhr.getType() != HitResult.Type.BLOCK) {
                if (previewOwner != null && previewOwner.equals(p.getUuid())) {
                    net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.clear();
                    previewOwner = null;
                }
                return;
            }

            // Compute parameters
            Direction face = bhr.getSide();
            BlockPos origin = bhr.getBlockPos();
            Direction horiz = p.getHorizontalFacing();

            var nbt = stack.getOrCreateNbt();
            long mask = normalizeMask(nbt.getLong(NBT_PATTERN));
            int depth = clampDepth(nbt.getInt(NBT_DEPTH));

            boolean local = isLocalPlayer(p);

            // ✅ Local player: full experience (sounds + readiness latch + progress)
            float pct;
            if (local) {
                net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;

                pct = startOrUpdateChargeLoop(mc.player, stack, remainingUseTicks);
                previewOwner = p.getUuid();

                net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.setPattern(origin, face, horiz, mask, depth, pct);
                return;
            }

            // ✅ Remote player: DO NOT cast, DO NOT run local sounds/HUD,
            // but DO render the SAME world preview for observers (range-limited).
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            if (mc.player.squaredDistanceTo(p) > OBS_PREVIEW_RANGE_SQ) return;

            // Don’t override the local player’s own preview if they are using it
            if (previewOwner != null && previewOwner.equals(mc.player.getUuid())) return;

            pct = computeChargePct(stack, remainingUseTicks);

            previewOwner = p.getUuid();
            net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.setPattern(origin, face, horiz, mask, depth, pct);
        }

        static void onStoppedUsingClient(net.minecraft.entity.LivingEntity user) {
            if (!(user instanceof PlayerEntity p)) return;

            // Only clear the preview if THIS user owned it on this client
            if (previewOwner != null && previewOwner.equals(p.getUuid())) {
                net.seep.odd.abilities.lunar.client.LunarDrillPreviewFx.clear();
                previewOwner = null;
            }

            // Only stop local sounds/progress if THIS was the local player
            if (isLocalPlayer(p)) {
                stopChargeLoop();
                LunarDrillPreview.setChargeProgress(0f);
                chargeReadyLatched = false;
            }
        }

        private static boolean isLocalPlayer(PlayerEntity p) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            return mc != null && mc.player != null && mc.player.getUuid().equals(p.getUuid());
        }

        private static float computeChargePct(ItemStack stack, int remainingUseTicks) {
            var nbt = stack.getOrCreateNbt();
            long mask = normalizeMask(nbt.getLong(NBT_PATTERN));
            int depth = clampDepth(nbt.getInt(NBT_DEPTH));

            int targetedBlocks = Math.max(1, Long.bitCount(mask) * depth);
            int need  = (int) Math.ceil(CHARGE_BASE_T + CHARGE_PER_BLOCK_T * targetedBlocks);
            int used  = MAX_USE_T - remainingUseTicks;
            return MathHelper.clamp(used / (float) need, 0f, 1f);
        }

        private static void renderOverheatHud(net.minecraft.client.gui.DrawContext ctx, float tickDelta) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.world == null) return;

            ItemStack main = mc.player.getMainHandStack();
            ItemStack off  = mc.player.getOffHandStack();

            ItemStack stack = main.getItem() instanceof LunarDrillItem ? main : (off.getItem() instanceof LunarDrillItem ? off : ItemStack.EMPTY);
            if (stack.isEmpty()) return;

            var nbt = stack.getOrCreateNbt();

            long now = mc.world.getTime();
            long hardEnd = nbt.getLong(NBT_HARDLOCK_END);
            boolean hard = hardEnd > now;

            float rawHeat = nbt.getFloat(NBT_HEAT);

            if (hudHeatLatch < 0f || rawHeat != hudHeatLatch || hudLatchT < 0) {
                hudHeatLatch = rawHeat;
                hudLatchT = now;
            }

            float displayHeat = rawHeat;
            if (!hard && hudLatchT >= 0) {
                long dt = Math.max(0, now - hudLatchT);
                displayHeat = Math.max(0f, hudHeatLatch - COOL_PER_TICK * dt);
            } else if (hard) {
                displayHeat = MAX_HEAT;
            }

            float pct = MathHelper.clamp(displayHeat / MAX_HEAT, 0f, 1f);

            int sh = mc.getWindow().getScaledHeight();
            int x = 10;
            int y = sh - 62;

            int w = 110;
            int h = 12;

            ctx.fill(x, y, x + w, y + h, 0x90000000);
            ctx.fill(x, y, x + w, y + 1, 0xFF000000);
            ctx.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
            ctx.fill(x, y, x + 1, y + h, 0xFF000000);
            ctx.fill(x + w - 1, y, x + w, y + h, 0xFF000000);

            int col;
            if (hard) col = 0xFFFF2A2A;
            else if (pct < 0.50f) col = 0xFF4DE3FF;
            else if (pct < 0.80f) col = 0xFFFFD34D;
            else col = 0xFFFF6A1A;

            int innerW = w - 2;
            int fillW = (int) Math.floor(innerW * pct);
            ctx.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, col);

            String label = "OVERHEAT";

            String right;
            if (hard) {
                int secs = (int) Math.ceil(Math.max(0, hardEnd - now) / 20.0);
                right = secs + "s";
            } else {
                right = (int) (pct * 100) + "%";
            }

            ctx.drawTextWithShadow(mc.textRenderer, label, x, y - 10, 0xFFFFFF);
            int rw = mc.textRenderer.getWidth(right);
            ctx.drawTextWithShadow(mc.textRenderer, right, x + w - rw, y - 10, hard ? 0xFF5555 : 0xA0FFFFFF);
        }

        private static float startOrUpdateChargeLoop(net.minecraft.client.network.ClientPlayerEntity p, ItemStack stack, int remainingUseTicks) {
            final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null) return 0f;

            var nbt = stack.getOrCreateNbt();
            long mask = normalizeMask(nbt.getLong(NBT_PATTERN));
            int depth = clampDepth(nbt.getInt(NBT_DEPTH));

            int targetedBlocks = Math.max(1, Long.bitCount(mask) * depth);
            int need  = (int) Math.ceil(CHARGE_BASE_T + CHARGE_PER_BLOCK_T * targetedBlocks);
            int used  = MAX_USE_T - remainingUseTicks;
            float pct = MathHelper.clamp(used / (float) need, 0f, 1f);

            LunarDrillPreview.setChargeProgress(pct);

            float pitch = 0.9f + pct * 0.5f;
            float vol   = 0.35f + pct * 0.25f;

            if (chargeLoop == null || chargeLoop.isDone()) {
                chargeLoop = new LoopForPlayer(ModSounds.DRILL_CHARGE, p);
                mc.getSoundManager().play(chargeLoop);
            }

            chargeLoop.setVolume(vol);
            chargeLoop.setPitch(pitch);

            if (!chargeReadyLatched && pct >= 1.0f) {
                chargeReadyLatched = true;
                p.playSound(ModSounds.DRILL_READY, 0.9f, 1.0f);
            }

            return pct;
        }

        private static void stopChargeLoop() {
            if (chargeLoop != null) { chargeLoop.finish(); chargeLoop = null; }
        }

        private static void startOrUpdateDrillLoop(net.minecraft.client.network.ClientPlayerEntity p, float pitch) {
            final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null) return;

            if (drillLoop == null || drillLoop.isDone()) {
                drillLoop = new LoopForPlayer(ModSounds.DRILL, p);
                mc.getSoundManager().play(drillLoop);
            }
            drillLoop.setVolume(0.65f);
            drillLoop.setPitch(pitch);
        }

        private static void stopDrillLoop() {
            if (drillLoop != null) { drillLoop.finish(); drillLoop = null; }
        }

        @SuppressWarnings("unchecked")
        static GeoItemRenderer<?> createRendererReflective() {
            try {
                Class<?> c = Class.forName("net.seep.odd.abilities.lunar.item.client.LunarDrillRenderer");
                Object o = c.getDeclaredConstructor().newInstance();
                return (GeoItemRenderer<?>) o;
            } catch (Throwable t) {
                return null;
            }
        }

        private static final class LoopForPlayer extends net.minecraft.client.sound.MovingSoundInstance {
            private final net.minecraft.client.network.ClientPlayerEntity player;
            private boolean stopped = false;

            LoopForPlayer(SoundEvent event, net.minecraft.client.network.ClientPlayerEntity player) {
                super(event, SoundCategory.PLAYERS, net.minecraft.client.sound.SoundInstance.createRandom());
                this.player = player;
                this.repeat = true;
                this.repeatDelay = 0;
                this.volume = 0.6f;
                this.pitch  = 1.0f;
                updatePos();
            }

            @Override public void tick() {
                if (player == null || player.isRemoved() || player.isDead()) { stopped = true; return; }
                updatePos();
            }

            private void updatePos() {
                this.x = player.getX();
                this.y = player.getY() + 0.5;
                this.z = player.getZ();
            }

            void setVolume(float v) { this.volume = v; }
            void setPitch(float p)  { this.pitch  = p; }
            void finish()           { this.stopped = true; }
            @Override public boolean isDone() { return this.stopped || super.isDone(); }
        }
    }
}