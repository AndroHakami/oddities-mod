package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.supercharge.SuperChargeNet;
import net.seep.odd.entity.supercharge.SuperThrownItemEntity;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.Map;
import java.util.UUID;

public final class SuperChargePower implements Power {

    /* ======================= config ======================= */
    private static final int   CHARGE_TIME_TICKS       = 20;   // 1s to supercharge
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

    @Override
    public Identifier iconTexture(String slot) {
        return new Identifier(Oddities.MOD_ID, "textures/gui/abilities/super_charge.png");
    }

    @Override
    public String longDescription() {
        return "Charge the held item for 1s; supercharged items glow orange and can be thrown by holding right-click like a trident, exploding on impact.";
    }

    @Override public String slotLongDescription(String slot) { return "Primary: begin/abort super-charging the held item."; }
    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/super_charge.png"); }

    /* ======================= state ======================= */
    private static final class St {
        boolean charging; int chargeTicks;
        ItemStack snapshot = ItemStack.EMPTY;
        boolean fxShown;

        boolean throwHolding; int throwTicks; Hand throwHand = Hand.MAIN_HAND;

        boolean holdLoopPlaying;
    }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof SuperChargePower;
    }

    /* =================== POWERLESS override (FireSword-style) =================== */
    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnOncePerSec(ServerPlayerEntity p, String msg) {
        long now = p.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(Text.literal(msg), true);
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        St st = DATA.get(player.getUuid());
        if (st != null) stopAll(player, st, false);
    }

    /* ======================= sound helpers ======================= */

    private static void playOnPlayer(ServerPlayerEntity p, SoundEvent sound, float volume, float pitch) {
        if (p == null || p.networkHandler == null) return;
        var entry = Registries.SOUND_EVENT.getEntry(sound);
        long seed = p.getRandom().nextLong();
        p.networkHandler.sendPacket(new PlaySoundFromEntityS2CPacket(entry, SoundCategory.PLAYERS, p, volume, pitch, seed));
    }

    private static void stopOnPlayer(ServerPlayerEntity p, SoundEvent sound) {
        if (p == null || p.networkHandler == null) return;
        Identifier id = Registries.SOUND_EVENT.getId(sound);
        if (id == null) return;
        p.networkHandler.sendPacket(new StopSoundS2CPacket(id, SoundCategory.PLAYERS));
    }

    private static void playAtBlock(World world, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, pos, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private static void syncHoldLoop(ServerPlayerEntity p, St st) {
        boolean shouldPlay = st.charging || st.throwHolding;

        if (shouldPlay && !st.holdLoopPlaying) {
            playOnPlayer(p, ModSounds.SUPERCHARGE_THROW_HOLD, 0.95f, 0.95f);
            st.holdLoopPlaying = true;
        } else if (!shouldPlay && st.holdLoopPlaying) {
            stopOnPlayer(p, ModSounds.SUPERCHARGE_THROW_HOLD);
            st.holdLoopPlaying = false;
        }
    }

    /* ======================= armor safety net ======================= */

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[] {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** If a supercharged item ever ends up equipped, immediately pop it back into inventory. */
    private static void enforceNoSuperchargedArmor(ServerPlayerEntity p) {
        boolean changed = false;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack eq = p.getEquippedStack(slot);
            if (eq.isEmpty()) continue;
            if (!isSupercharged(eq)) continue;

            // remove from equipment first (prevents dupes)
            ItemStack toMove = eq.copy();
            p.equipStack(slot, ItemStack.EMPTY);

            if (!p.getInventory().insertStack(toMove)) {
                p.dropItem(toMove, false);
            }
            changed = true;
        }

        if (changed) {
            p.currentScreenHandler.sendContentUpdates();
        }
    }

    /* ======================= bootstrap ======================= */
    public static void bootstrap() {

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(stack);

            if (isPowerless(sp)) return TypedActionResult.pass(stack);
            if (!isCurrent(sp) || !isSupercharged(stack)) return TypedActionResult.pass(stack);

            normalizeSuperchargedStack(sp, hand);

            St st = S(sp);
            st.throwHolding = true;
            st.throwTicks = 0;
            st.throwHand = hand;

            player.setCurrentHand(hand);

            syncHoldLoop(sp, st);

            return TypedActionResult.consume(sp.getStackInHand(hand));
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) return ActionResult.PASS;
            if (!isSupercharged(stack)) return ActionResult.PASS;

            if (world.isClient) {
                return ActionResult.SUCCESS;
            }

            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.CONSUME;

            normalizeSuperchargedStack(sp, hand);

            if (isCurrent(sp) && !isPowerless(sp)) {
                St st = S(sp);
                st.throwHolding = true;
                st.throwTicks = 0;
                st.throwHand = hand;

                player.setCurrentHand(hand);
                syncHoldLoop(sp, st);
            }

            sp.currentScreenHandler.sendContentUpdates();
            return ActionResult.CONSUME;
        });

        ServerTickEvents.END_SERVER_TICK.register(SuperChargePower::tickAll);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.player;
            DATA.remove(p.getUuid());
            WARN_UNTIL.removeLong(p.getUuid());
        });
    }

    /* ======================= input: primary = supercharge ======================= */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        if (isPowerless(p)) {
            stopAll(p, st, false);
            warnOncePerSec(p, "§cYou are powerless.");
            return;
        }

        if (st.charging) { stopCharge(p, st, false); return; }

        if (hasAnySupercharged(p)) {
            p.sendMessage(Text.literal("You already have a supercharged item."), true);
            return;
        }

        ItemStack held = p.getMainHandStack();


        st.charging = true;
        st.chargeTicks = 0;
        st.snapshot = held.copy();
        st.snapshot.setCount(1);

        st.fxShown = true;

        SuperChargeNet.sendHud(p, true, 0, CHARGE_TIME_TICKS);

        syncHoldLoop(p, st);
    }

    @Override public void activateSecondary(ServerPlayerEntity p) { }
    @Override public void activateThird(ServerPlayerEntity p) { }

    /* ======================= tick loop ======================= */
    private static void tickAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {

            // ✅ always enforce (even if power swapped off)
            enforceNoSuperchargedArmor(p);

            if (!isCurrent(p)) {
                St s = DATA.get(p.getUuid());
                if (s != null) {
                    if (s.fxShown) {
                        SuperChargeNet.sendHud(p, false, 0, CHARGE_TIME_TICKS);
                        s.fxShown = false;
                    }
                    if (s.holdLoopPlaying) {
                        stopOnPlayer(p, ModSounds.SUPERCHARGE_THROW_HOLD);
                        s.holdLoopPlaying = false;
                    }
                    if (s.charging || s.throwHolding) stopAll(p, s, false);
                }
                continue;
            }

            St st = S(p);

            if (!p.isAlive() || isPowerless(p)) {
                stopAll(p, st, false);
                continue;
            }

            syncHoldLoop(p, st);

            if (st.charging) {
                ItemStack cur = p.getMainHandStack();
                if (cur.isEmpty() || !ItemStack.canCombine(cur, st.snapshot) || isSupercharged(cur)) {
                    stopCharge(p, st, false);
                } else {
                    st.chargeTicks++;
                    SuperChargeNet.sendHud(p, true, st.chargeTicks, CHARGE_TIME_TICKS);

                    if (st.chargeTicks >= CHARGE_TIME_TICKS) {
                        finishChargeOneItem(p, cur);

                        playOnPlayer(p, ModSounds.SUPERCHARGE_READY, 0.95f, 1.15f);


                        stopCharge(p, st, true);
                    }
                }
            }

            if (st.throwHolding) {
                ItemStack cur = p.getStackInHand(st.throwHand);
                boolean valid = !cur.isEmpty() && isSupercharged(cur);

                if (!p.isUsingItem() || !valid || isPowerless(p) || !p.isAlive()) {
                    int held = st.throwTicks;

                    if (held > 2 && valid && p.getWorld() instanceof ServerWorld sw) {
                        float t = Math.min(1f, held / (float) THROW_MAX_HOLD_TICKS);
                        float speed = THROW_MIN_SPEED + (THROW_MAX_SPEED - THROW_MIN_SPEED) * (t * t);

                        st.throwHolding = false;
                        st.throwTicks = 0;
                        syncHoldLoop(p, st);

                        throwStack(sw, p, cur, speed);
                        if (!p.isCreative()) cur.decrement(1);

                        playAtBlock(sw, p.getBlockPos(), ModSounds.SUPERCHARGE_THROW, 1.15f, 1.0f);
                    } else {
                        st.throwHolding = false;
                        st.throwTicks = 0;
                        syncHoldLoop(p, st);
                    }
                } else {
                    st.throwTicks++;
                }
            }

            syncHoldLoop(p, st);
        }
    }

    private static void stopAll(ServerPlayerEntity p, St st, boolean complete) {
        st.throwHolding = false;
        st.throwTicks = 0;

        if (st.charging) stopCharge(p, st, complete);
        else {
            if (st.fxShown) {
                SuperChargeNet.sendHud(p, false, 0, CHARGE_TIME_TICKS);
                st.fxShown = false;
            }
            syncHoldLoop(p, st);
        }
    }

    private static void stopCharge(ServerPlayerEntity p, St st, boolean complete) {
        st.charging = false;
        st.chargeTicks = 0;
        st.snapshot = ItemStack.EMPTY;

        if (st.fxShown) {
            SuperChargeNet.sendHud(p, false, 0, CHARGE_TIME_TICKS);
            st.fxShown = false;
        }

        syncHoldLoop(p, st);

        if (!complete) {
            playOnPlayer(p, ModSounds.SUPERCHARGE_CANCEL, 0.75f, 0.90f);
        }
    }

    private static void finishChargeOneItem(ServerPlayerEntity p, ItemStack curMainHand) {
        if (curMainHand.getCount() > 1) {
            ItemStack charged = curMainHand.copy();
            charged.setCount(1);
            markSupercharged(charged);

            ItemStack remainder = curMainHand.copy();
            remainder.setCount(curMainHand.getCount() - 1);
            clearSupercharged(remainder);

            p.setStackInHand(Hand.MAIN_HAND, charged);
            if (!p.getInventory().insertStack(remainder)) p.dropItem(remainder, false);
        } else {
            markSupercharged(curMainHand);
        }
        p.currentScreenHandler.sendContentUpdates();
    }

    private static void normalizeSuperchargedStack(ServerPlayerEntity p, Hand hand) {
        ItemStack inHand = p.getStackInHand(hand);
        if (inHand.isEmpty() || !isSupercharged(inHand)) return;
        if (inHand.getCount() <= 1) return;

        ItemStack chargedOne = inHand.copy();
        chargedOne.setCount(1);
        markSupercharged(chargedOne);

        ItemStack remainder = inHand.copy();
        remainder.setCount(inHand.getCount() - 1);
        clearSupercharged(remainder);

        p.setStackInHand(hand, chargedOne);
        if (!p.getInventory().insertStack(remainder)) p.dropItem(remainder, false);

        p.currentScreenHandler.sendContentUpdates();
    }

    private static boolean hasAnySupercharged(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (isSupercharged(inv.getStack(i))) return true;
        }
        return false;
    }

    public static boolean isSupercharged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(NBT_KEY);
    }

    public static void markSupercharged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        stack.getOrCreateNbt().putBoolean(NBT_KEY, true);
    }

    public static void clearSupercharged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        var nbt = stack.getNbt();
        if (nbt == null) return;
        nbt.remove(NBT_KEY);
        if (nbt.isEmpty()) stack.setNbt(null);
    }

    private static void throwStack(ServerWorld sw, ServerPlayerEntity sp, ItemStack stack, float speed) {
        ItemStack thrown = stack.copy();
        thrown.setCount(1);

        SuperThrownItemEntity ent = new SuperThrownItemEntity(sw, sp, thrown);
        ent.setVelocity(sp, sp.getPitch(), sp.getYaw(), 0.0F, speed, 0.01F);
        sw.spawnEntity(ent);

        sp.getItemCooldownManager().set(stack.getItem(), 6);
    }

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