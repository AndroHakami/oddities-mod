package net.seep.odd.abilities.lunar.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
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
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.seep.odd.abilities.lunar.client.LunarDrillPreview;
import net.seep.odd.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
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

    // Heat gained per normal LMB block break
    public static final float HEAT_PER_LMB_BLOCK = 6.25f;

    // Heat gained by RMB burst: flat + per broken block
    public static final float HEAT_BURST_FLAT = 4.0f;
    public static final float HEAT_PER_BURST_BLOCK = 0.70f;

    // Cooling rate (applies “lazily” when we next evaluate heat)
    public static final float COOL_PER_TICK = 0.12f;

    // Hard lock duration (ticks) - 30 seconds
    public static final int HARD_LOCK_TICKS = 20 * 30;

    /* ===================================================================================== */
    /* =============================  Pattern/Depth (5x5)  ================================= */
    /* ===================================================================================== */

    public static final int PATTERN_SIZE = 5;
    public static final int PATTERN_BITS = PATTERN_SIZE * PATTERN_SIZE; // 25
    public static final int PATTERN_CENTER = PATTERN_SIZE / 2; // 2

    /* -------- NBT -------- */
    public static final String NBT_PATTERN = "LunarPattern64";
    public static final String NBT_DEPTH   = "lunar_depth";

    private static final String NBT_RAMP         = "lunar_ramp";
    private static final String NBT_LAST_BREAK_T = "lunar_lastBreakT";

    // which hand started RMB use (server)
    private static final String NBT_HAND = "odd_drill_hand";

    // overheat (server authoritative; written only on interactions — not every tick)
    private static final String NBT_HEAT         = "odd_heat";
    private static final String NBT_HARDLOCK_END = "odd_heatHardEnd";
    private static final String NBT_LAST_HEAT_T  = "odd_heatLastT";

    /* -------- RMB charge tuning -------- */
    private static final int   MAX_USE_T          = 72000;
    private static final int   CHARGE_BASE_T      = 16;
    private static final float CHARGE_PER_BLOCK_T = 0.8f;
    private static final double PREVIEW_REACH     = 5.5;

    /* -------- LMB ramp tuning -------- */
    private static final float RAMP_ADD_PER_BREAK   = 0.35f;
    private static final float RAMP_MAX             = 8.0f;
    private static final int   RAMP_IDLE_HALFLIFE_T = 30;
    private static final float BASE_MINING_SPEED    = 4.0f;

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

    // Used to suppress LMB heat/ramp counting while we are doing the RMB burst break loop.
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
        sp.sendMessage(net.minecraft.text.Text.literal(msg), true);
    }

    private static boolean isHardLocked(ItemStack stack, World world) {
        long end = stack.getOrCreateNbt().getLong(NBT_HARDLOCK_END);
        return end > 0 && world.getTime() < end;
    }

    /**
     * Lazily apply cooling since last evaluation.
     * IMPORTANT: Only runs when we "touch" the drill (break/use/stopUsing),
     * not every tick -> prevents NBT spam and held-item wobble.
     */
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
            // While hard locked, keep it pegged at max
            heat = MAX_HEAT;
        }

        nbt.putFloat(NBT_HEAT, MathHelper.clamp(heat, 0f, MAX_HEAT));
        nbt.putLong(NBT_LAST_HEAT_T, now);

        // clear hard end once time passed (cleanup)
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

    /** call from common init */
    public static void registerHooks() {
        if (hooksRegistered) return;
        hooksRegistered = true;

        // Cancel normal LMB block breaks if hard-overheated (but allow burst window)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (world.isClient) return true;
            if (!(player instanceof ServerPlayerEntity sp)) return true;

            ItemStack drill = getHeldDrill(sp);
            if (!(drill.getItem() instanceof LunarDrillItem)) return true;

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

        // Heat/ramp only for normal LMB breaks (not burst)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world.isClient) return;
            if (!(player instanceof ServerPlayerEntity sp)) return;

            ItemStack drill = getHeldDrill(sp);
            if (!(drill.getItem() instanceof LunarDrillItem item)) return;

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

            // optional small pop on LMB mining
            item.triggerAnim(sp, Hand.MAIN_HAND.ordinal(), "main", "burst");
        });
    }

    /* ===================================================================================== */
    /* =============================  RMB CHARGE + PREVIEW  ================================= */
    /* ===================================================================================== */

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // Prevent using while hard locked
        if (isHardLocked(stack, world)) return TypedActionResult.fail(stack);
        if (!world.isClient) coolHeat(world, stack);

        if (user.isSneaking()) {
            if (world.isClient) {
                MinecraftClient.getInstance().setScreen(
                        new net.seep.odd.abilities.lunar.client.screen.LunarPatternScreen(hand));
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
        if (!world.isClient || !(user instanceof PlayerEntity p)) return;

        // If hard locked client-side, don't do any preview/charge fx
        long hardEnd = stack.getOrCreateNbt().getLong(NBT_HARDLOCK_END);
        if (hardEnd > 0 && world.getTime() < hardEnd) {
            LunarDrillPreview.setChargeProgress(0f);
            LunarDrillPreview.setTargets(Collections.emptyList());
            return;
        }

        // Keep charge loop alive + update progress every tick (client-only)
        startOrUpdateChargeLoop((ClientPlayerEntity) p, stack, remainingUseTicks);

        BlockHitResult bhr = rayToBlock(world, p, PREVIEW_REACH);
        if (bhr == null || bhr.getType() != HitResult.Type.BLOCK) {
            LunarDrillPreview.setTargets(Collections.emptyList());
            return;
        }

        Direction face = bhr.getSide();
        BlockPos origin = bhr.getBlockPos();
        Direction horiz = p.getHorizontalFacing();

        var nbt = stack.getOrCreateNbt();
        long mask = normalizeMask(nbt.getLong(NBT_PATTERN));
        int depth = clampDepth(nbt.getInt(NBT_DEPTH));

        boolean[][] grid = bitsToGrid(mask);
        List<BlockPos> targets = layoutPatternWithDepth(origin, face, horiz, grid, depth);

        LunarDrillPreview.setTargets(targets);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, net.minecraft.entity.LivingEntity user, int remainingUseTicks) {
        if (world.isClient) {
            stopChargeLoop();
            LunarDrillPreview.setChargeProgress(0f);
            LunarDrillPreview.clear();
            chargeReadyLatched = false;
            return;
        }

        if (!(user instanceof ServerPlayerEntity sp)) return;

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

        // Suppress the LMB AFTER hook while we do the burst loop
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

    /* ---------------- ray + pattern helpers ---------------- */

    private static BlockHitResult rayToBlock(World w, PlayerEntity p, double reach) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(reach));
        return w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
    }

    /** Force center bit on, and keep only lowest 25 bits. */
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

    /** 5x5 grid from 25-bit mask. */
    public static boolean[][] bitsToGrid(long bits) {
        boolean[][] g = new boolean[PATTERN_SIZE][PATTERN_SIZE];
        bits = normalizeMask(bits);
        for (int r = 0; r < PATTERN_SIZE; r++) for (int c = 0; c < PATTERN_SIZE; c++) {
            int i = r * PATTERN_SIZE + c;
            g[r][c] = ((bits >>> i) & 1L) == 1L;
        }
        return g;
    }

    /** Builds targets for the 5x5 pattern on the hit face, extruded inward by depth (1..6). */
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
    /* ============================  CLIENT ANIM + SOUND + HUD  ============================ */
    /* ===================================================================================== */

    @Environment(EnvType.CLIENT) private static boolean hooksInit = false;
    @Environment(EnvType.CLIENT) private static boolean hudInit   = false;

    // client-only state (NO NBT WRITES -> prevents wobble)
    @Environment(EnvType.CLIENT) private static int chargeMainTicks = 0, chargeOffTicks = 0;
    @Environment(EnvType.CLIENT) private static int drillMainTicks  = 0, drillOffTicks  = 0;

    // continuous loop SFX
    @Environment(EnvType.CLIENT) private static LoopForPlayer chargeLoop = null;
    @Environment(EnvType.CLIENT) private static LoopForPlayer drillLoop  = null;
    @Environment(EnvType.CLIENT) private static boolean chargeReadyLatched = false;

    // HUD smoothing (client-only)
    @Environment(EnvType.CLIENT) private static float hudHeatLatch = -1f;
    @Environment(EnvType.CLIENT) private static long  hudLatchT = -1L;

    /** call once from client init */
    @Environment(EnvType.CLIENT)
    public static void initClientHooks() {
        if (hooksInit) return;
        hooksInit = true;

        if (!hudInit) {
            hudInit = true;
            HudRenderCallback.EVENT.register(LunarDrillItem::renderOverheatHud);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                stopChargeLoop();
                stopDrillLoop();
                LunarDrillPreview.setChargeProgress(0f);
                LunarDrillPreview.setTargets(Collections.emptyList());
                chargeReadyLatched = false;
                chargeMainTicks = chargeOffTicks = drillMainTicks = drillOffTicks = 0;
                return;
            }

            // decay flags
            if (chargeMainTicks > 0) chargeMainTicks--;
            if (chargeOffTicks  > 0) chargeOffTicks--;
            if (drillMainTicks  > 0) drillMainTicks--;
            if (drillOffTicks   > 0) drillOffTicks--;

            ClientPlayerEntity p = client.player;

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
                chargeReadyLatched = false;
            }

            boolean attackHeld = client.options.attackKey.isPressed();
            if (attackHeld && mainIsDrill) drillMainTicks = 2;
            if (attackHeld && offIsDrill)  drillOffTicks  = 2;

            if (attackHeld && (mainIsDrill || offIsDrill)) {
                p.handSwinging = false;
                p.handSwingTicks = 0;
            }

            // drill loop sound (stop if hard locked)
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

    @Environment(EnvType.CLIENT)
    private static void renderOverheatHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
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

        // latch + locally cool for smooth HUD without NBT writes
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

    /* ---------- charge loop control ---------- */
    @Environment(EnvType.CLIENT)
    private static void startOrUpdateChargeLoop(ClientPlayerEntity p, ItemStack stack, int remainingUseTicks) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

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
    }

    @Environment(EnvType.CLIENT)
    private static void stopChargeLoop() {
        if (chargeLoop != null) { chargeLoop.finish(); chargeLoop = null; }
    }

    /* ---------- drill loop control ---------- */
    @Environment(EnvType.CLIENT)
    private static void startOrUpdateDrillLoop(ClientPlayerEntity p, float pitch) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        if (drillLoop == null || drillLoop.isDone()) {
            drillLoop = new LoopForPlayer(ModSounds.DRILL, p);
            mc.getSoundManager().play(drillLoop);
        }
        drillLoop.setVolume(0.65f);
        drillLoop.setPitch(pitch);
    }

    @Environment(EnvType.CLIENT)
    private static void stopDrillLoop() {
        if (drillLoop != null) { drillLoop.finish(); drillLoop = null; }
    }

    /* ---------- simple moving loop sound ---------- */
    @Environment(EnvType.CLIENT)
    private static final class LoopForPlayer extends MovingSoundInstance {
        private final ClientPlayerEntity player;
        private boolean stopped = false;

        LoopForPlayer(SoundEvent event, ClientPlayerEntity player) {
            super(event, SoundCategory.PLAYERS, SoundInstance.createRandom());
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
            @Override public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null)
                    renderer = new net.seep.odd.abilities.lunar.item.client.LunarDrillRenderer();
                return renderer;
            }
        });
    }
}
