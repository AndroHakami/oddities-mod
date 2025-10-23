package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.supercharge.SuperChargeNet;
import net.seep.odd.entity.supercharge.SuperEntities;
import net.seep.odd.entity.supercharge.SuperThrownItemEntity;

import java.util.*;

public final class SuperChargePower implements Power {
    /* ======================= config ======================= */
    private static final int   CHARGE_TIME_TICKS       = 20;   // 1s to "supercharge" the stack
    private static final int   THROW_MAX_HOLD_TICKS    = 20;   // hold up to ~1s like trident
    private static final float THROW_MIN_SPEED         = 0.80f;
    private static final float THROW_MAX_SPEED         = 1.90f;
    private static final float BASE_EXPLOSION_POWER    = 2.8f;
    private static final float TNT_EXPLOSION_POWER     = 6.0f;
    private static final boolean TNT_DESTROYS_BLOCKS   = true;
    private static final boolean DEFAULT_BREAK_BLOCKS  = false;

    public static final String NBT_KEY = "odd_supercharged";

    /* ======================= meta ======================= */
    @Override public String id() { return "super_charge"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot); }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override public Identifier iconTexture(String slot) {
        return new Identifier(Oddities.MOD_ID, "textures/gui/abilities/super_charge.png");
    }
    @Override public String longDescription() {
        return "Charge the held item for 1s; supercharged items glow orange and can be thrown by holding right-click like a trident, exploding on impact.";
    }
    @Override public String slotLongDescription(String slot) { return "Primary: begin/abort super-charging the held item."; }
    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/super_charge.png"); }

    /* ======================= state ======================= */
    private static final class St {
        boolean charging; int chargeTicks;
        ItemStack snapshot = ItemStack.EMPTY;
        boolean hudShown;

        boolean throwHolding; int throwTicks; Hand throwHand = Hand.MAIN_HAND;
    }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof SuperChargePower;
    }

    /* ======================= bootstrap ======================= */
    public static void bootstrap() {
        // Start "using" a supercharged stack on RMB (trident-like hold)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(stack);
            if (!isCurrent(sp) || !isSupercharged(stack)) return TypedActionResult.pass(stack);

            // begin holding
            St st = S(sp);
            st.throwHolding = true;
            st.throwTicks = 0;
            st.throwHand = hand;
            player.setCurrentHand(hand); // tells client to play use animation (we spoof SPEAR via mixin)
            sp.getWorld().playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.6f, 1.1f);
            return TypedActionResult.consume(stack);
        });

        // Also treat block-use as starting a hold, instead of placing
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!isCurrent(sp) || !isSupercharged(stack)) return ActionResult.PASS;

            St st = S(sp);
            st.throwHolding = true;
            st.throwTicks = 0;
            st.throwHand = hand;
            player.setCurrentHand(hand);
            sp.getWorld().playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.PLAYERS, 0.6f, 1.1f);
            return ActionResult.CONSUME;
        });

        // self-managed ticking
        ServerTickEvents.END_SERVER_TICK.register(SuperChargePower::tickAll);
    }

    /* ======================= input: primary = supercharge the stack ======================= */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        if (st.charging) { stopCharge(p, st, false); return; }

        ItemStack held = p.getMainHandStack();
        if (held.isEmpty()) { p.sendMessage(Text.literal("Hold an item to supercharge."), true); return; }

        st.charging = true; st.chargeTicks = 0; st.snapshot = held.copy(); st.hudShown = true;
        SuperChargeNet.sendHud(p, true, 0, CHARGE_TIME_TICKS);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.6f, 1.25f);
    }

    @Override public void activateSecondary(ServerPlayerEntity p) { }
    @Override public void activateThird(ServerPlayerEntity p) { }

    /* ======================= tick loop ======================= */
    private static void tickAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isCurrent(p)) {
                St s = DATA.get(p.getUuid());
                if (s != null && s.hudShown) { SuperChargeNet.sendHud(p, false, 0, CHARGE_TIME_TICKS); s.hudShown = false; }
                continue;
            }
            St st = S(p);

            // 1) stack super-charge progress
            if (st.charging) {
                ItemStack cur = p.getMainHandStack();
                if (cur.isEmpty() || !ItemStack.canCombine(cur, st.snapshot)) { stopCharge(p, st, false); }
                else {
                    st.chargeTicks++;
                    SuperChargeNet.sendHud(p, true, st.chargeTicks, CHARGE_TIME_TICKS);
                    if (st.chargeTicks == CHARGE_TIME_TICKS/2) p.playSound(SoundEvents.ITEM_TRIDENT_RIPTIDE_2, 0.5f, 1.4f);
                    if (st.chargeTicks >= CHARGE_TIME_TICKS) {
                        markSupercharged(cur);
                        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 0.9f, 1.35f);
                        p.sendMessage(Text.literal("Item supercharged! Hold right-click to throw."), true);
                        stopCharge(p, st, true);
                    }
                }
            }

            // 2) hold-to-throw (trident-style)
            if (st.throwHolding) {
                ItemStack cur = p.getStackInHand(st.throwHand);
                boolean valid = !cur.isEmpty() && isSupercharged(cur);
                if (!p.isUsingItem() || !valid) {
                    // release or invalid → throw if we held at least a moment
                    int held = st.throwTicks;
                    if (held > 2 && valid && p.getWorld() instanceof ServerWorld sw) {
                        float t = Math.min(1f, held / (float) THROW_MAX_HOLD_TICKS);
                        float speed = THROW_MIN_SPEED + (THROW_MAX_SPEED - THROW_MIN_SPEED) * (t * t); // ease
                        throwStack(sw, p, cur, speed);
                        if (!p.isCreative()) cur.decrement(1);
                        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.9f, 1.0f);
                    }
                    st.throwHolding = false; st.throwTicks = 0;
                } else {
                    st.throwTicks++;
                    if (st.throwTicks == 10) p.playSound(SoundEvents.ITEM_TRIDENT_RIPTIDE_2, 0.5f, 1.25f);
                    if (st.throwTicks >= THROW_MAX_HOLD_TICKS) p.playSound(SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 0.6f, 1.35f);
                }
            }
        }
    }

    private static void stopCharge(ServerPlayerEntity p, St st, boolean complete) {
        st.charging = false; st.chargeTicks = 0; st.snapshot = ItemStack.EMPTY;
        if (st.hudShown) { SuperChargeNet.sendHud(p, false, 0, CHARGE_TIME_TICKS); st.hudShown = false; }
        if (!complete) p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.6f, 0.8f);
    }

    /* ======================= helpers ======================= */
    public static boolean isSupercharged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var nbt = stack.getOrCreateNbt();
        return nbt.contains(NBT_KEY) && nbt.getBoolean(NBT_KEY);
    }
    public static void markSupercharged(ItemStack stack) {
        stack.getOrCreateNbt().putBoolean(NBT_KEY, true);
    }
    public static void clearSupercharged(ItemStack stack) {
        stack.getOrCreateNbt().remove(NBT_KEY);
    }

    private static void throwStack(ServerWorld sw, ServerPlayerEntity sp, ItemStack stack, float speed) {
        ItemStack thrown = stack.copy();
        thrown.setCount(1);

        SuperThrownItemEntity ent = new SuperThrownItemEntity(sw, sp, thrown);

        // Trident-like launch: (user, pitch, yaw, roll, speed, inaccuracy)
        ent.setVelocity(sp, sp.getPitch(), sp.getYaw(), 0.0F, speed, 0.01F);

        sw.spawnEntity(ent);

        // light cooldown so you can’t spam
        sp.getItemCooldownManager().set(stack.getItem(), 6);
    }

    /* explosion strength rules */
    public static float explosionPowerFor(ItemStack thrown) {
        Item i = thrown.getItem();
        if (i == Items.TNT) return TNT_EXPLOSION_POWER;
        return BASE_EXPLOSION_POWER;
    }
    public static boolean breaksBlocksFor(ItemStack thrown) {
        Item i = thrown.getItem();
        if (i == Items.TNT) return TNT_DESTROYS_BLOCKS;
        return DEFAULT_BREAK_BLOCKS;
    }
}
