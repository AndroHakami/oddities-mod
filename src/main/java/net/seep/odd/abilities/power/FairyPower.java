// src/main/java/net/seep/odd/abilities/power/FairyPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.RaycastContext;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.fairy.CastLogic;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Fairy power:
 * - small (Pehkui 0.5), 6 hearts
 * - creative flight (double-space) that drains mana
 * - Primary: toggle Cast Form
 * - Secondary: open Manage Flowers menu
 * - Third: Beam of Magic (drains mana, recharges flowers)
 *
 * HUD is client-only: see FairyManaHudClient.
 */
public final class FairyPower implements Power {

    @Override public String id() { return "fairy"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/fairy_cast.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/fairy_manage.png");
            case "third"     -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/fairy_mana_beam.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Cast Form: enter 3 inputs (↑↓←→). Inputs become ↑↓←→. "
                            + "Soft-target False Flowers to imbue them.";
            case "secondary" ->
                    "Manage Flowers: view magical False Flowers; activate/deactivate, rename, set radius (power), cleanse.";
            case "third" ->
                    "Beam of Magic: channel a mana beam that recharges False Flowers you aim at. Drains mana while active.";
            default -> "Fairy";
        };
    }

    @Override
    public String longDescription() {
        return "A tiny caster with creative-style flight that consumes mana. Place False Flowers and imbue them "
                + "with spells using Cast Form combos. Channel a Beam of Magic to refill flowers.";
    }

    /* ---------------- Network IDs ---------------- */

    public static final Identifier C2S_CAST_COMMIT  = new Identifier(Oddities.MOD_ID, "fairy/cast_commit");
    public static final Identifier C2S_TOGGLE_CAST  = new Identifier(Oddities.MOD_ID, "fairy/toggle_cast");
    public static final Identifier C2S_OPEN_MENU    = new Identifier(Oddities.MOD_ID, "fairy/open_menu");

    // optional (lets any client control beam w/o needing ability-framework wiring)
    public static final Identifier C2S_TOGGLE_BEAM  = new Identifier(Oddities.MOD_ID, "fairy/toggle_beam");

    public static final Identifier S2C_CAST_STATE   = new Identifier(Oddities.MOD_ID, "fairy/cast_state");

    // mana HUD sync (client file listens to this)
    public static final Identifier S2C_MANA_SYNC    = new Identifier(Oddities.MOD_ID, "fairy/mana_sync");

    private static void sendCastState(ServerPlayerEntity player, boolean on) {
        var b = PacketByteBufs.create();
        b.writeBoolean(on);
        ServerPlayNetworking.send(player, S2C_CAST_STATE, b);
    }

    private static void sendManaHud(ServerPlayerEntity player, boolean hasFairy, float mana) {
        var b = PacketByteBufs.create();
        b.writeBoolean(hasFairy);
        b.writeFloat(mana);
        b.writeFloat(MANA_MAX);
        ServerPlayNetworking.send(player, S2C_MANA_SYNC, b);
    }

    /* ---------------- Mana + flight tuning ---------------- */

    public static final float MANA_MAX = 100f;

    /** Passive regen per tick (whenever you are NOT flight-draining). */
    public static float MANA_REGEN_PER_TICK = 0.030f;

    /** Drain per tick while flying (creative flight). */
    public static float FLIGHT_DRAIN_PER_TICK = 0.30f;

    /** How often we sync mana to the client HUD. */
    public static int MANA_SYNC_INTERVAL = 5;

    /** Sparkle tuning (flight). */
    public static int SPARKLES_PER_TICK = 2;

    /** Cast cost (CastLogic uses this). */
    public static float CAST_COST = 5f;

    /** Beam tuning. */
    public static float BEAM_DRAIN_PER_TICK = 0.22f;
    public static float BEAM_RECHARGE_PER_TICK = 2.0f; // mana into flower per tick while beaming
    public static double BEAM_RANGE = 32.0;
    public static int BEAM_SEGMENTS = 14;              // particles along beam each tick
    public static float BEAM_SOFT_RADIUS = 1.35f;      // soft-targeting radius along ray
    public static int BEAM_FLASH_EVERY_TICKS = 4;      // recharge flash cadence

    /** Golden apples: burst mana on successful eat. */
    public static float GAPPLE_MANA_BURST = 22.0f;
    public static float ENCHANTED_GAPPLE_MANA_BURST = 45.0f;

    public static final UUID HP_MOD_ID = UUID.fromString("7a2d89d0-3c80-4caa-82d6-6e2e1f8d66c1");

    /* ---------------- Power actions ---------------- */

    @Override
    public void activate(ServerPlayerEntity player) {
        boolean next = !CastLogic.isCastFormEnabled(player);
        CastLogic.toggleCastForm(player, next);
        sendCastState(player, next);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        net.seep.odd.abilities.fairy.FlowerMenu.openFor(player);
    }

    /** Third slot (named "third"). */
    @Override
    public void activateThird(ServerPlayerEntity player) {
        boolean next = !isBeamOn(player);
        setBeamOn(player, next);
        player.sendMessage(Text.literal(next ? "Beam: ON" : "Beam: OFF"), true);
    }

    /* ---------------- Lifecycle hooks ---------------- */

    public static void onPowerGained(ServerPlayerEntity p) {
        applyHalfHearts(p);
        applyPehkuiScale(p, 0.5f);

        float mana = getMana(p);
        if (mana <= 0f) {
            setMana(p, MANA_MAX);
            mana = MANA_MAX;
        }

        // allow flying immediately if mana > 0
        setAllowFlying(p, mana > 0.01f);

        // HUD on
        sendManaHud(p, true, mana);

        p.sendMessage(Text.literal("Fairy form awakened ✨"), true);
    }

    public static void onPowerLost(ServerPlayerEntity p) {
        clearHalfHearts(p);
        applyPehkuiScale(p, 1.0f);

        CastLogic.toggleCastForm(p, false);

        // stop beam
        setBeamOn(p, false);

        // remove flight
        setAllowFlying(p, false);

        // hide HUD
        sendManaHud(p, false, 0f);

        // cleanup small caches
        UUID id = p.getUuid();
        LAST_SENT_MANA.removeFloat(id);
        LAST_SENT_TICK.removeLong(id);
        LAST_ALLOW.removeFloat(id);
        PREV_CAST.removeFloat(id);
        LAST_FLYING.removeFloat(id);
        LAST_BEAM_FLASH.removeLong(id);

        LAST_EAT_KIND.removeLong(id);
        LAST_EAT_LEFT.removeLong(id);
    }

    /* ---------------- Tick ---------------- */

    private static final Object2FloatOpenHashMap<UUID> LAST_SENT_MANA  = new Object2FloatOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_SENT_TICK  = new Object2LongOpenHashMap<>();
    private static final Object2FloatOpenHashMap<UUID> LAST_ALLOW      = new Object2FloatOpenHashMap<>();

    // preserve flight mode across the moment cast form toggles on
    private static final Object2FloatOpenHashMap<UUID> PREV_CAST       = new Object2FloatOpenHashMap<>();
    private static final Object2FloatOpenHashMap<UUID> LAST_FLYING     = new Object2FloatOpenHashMap<>();

    // beam flash pacing
    private static final Object2LongOpenHashMap<UUID>  LAST_BEAM_FLASH = new Object2LongOpenHashMap<>();

    // eating tracking (only for golden apples)
    // bits: [0..1]=kind (0 none, 1 gapple, 2 egapple), bit2=wasUsing
    private static final Object2LongOpenHashMap<UUID> LAST_EAT_KIND = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> LAST_EAT_LEFT = new Object2LongOpenHashMap<>();

    public static void serverTick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        UUID id = p.getUuid();

        boolean casting = CastLogic.isCastFormEnabled(p);
        boolean prevCasting = PREV_CAST.getOrDefault(id, 0f) > 0.5f;

        // Record "was flying" only while NOT casting (so it reflects real player intent)
        if (!casting) {
            LAST_FLYING.put(id, p.getAbilities().flying ? 1f : 0f);
        }

        float mana = getMana(p);
        boolean flying = p.getAbilities().flying;

        // allow flight whenever mana > 0 (YES: even while casting)
        boolean shouldAllow = mana > 0.01f;
        setAllowFlyingIfChanged(p, shouldAllow);

        // If cast form just turned ON and you were flying right before, re-assert flying so you don't drop.
        if (casting && !prevCasting) {
            boolean wasFlying = LAST_FLYING.getOrDefault(id, 0f) > 0.5f;
            if (wasFlying && shouldAllow && !p.getAbilities().flying) {
                p.getAbilities().flying = true;
                p.sendAbilitiesUpdate();
                flying = true;
            }
        }

        PREV_CAST.put(id, casting ? 1f : 0f);

        boolean beamOn = isBeamOn(p);

        boolean flightDraining = flying && !p.isOnGround();

        // ---- flight drain + particles ----
        if (flightDraining) {
            mana = Math.max(0f, mana - FLIGHT_DRAIN_PER_TICK);
            spawnFlightSparkles(sw, p);
        }

        // ---- beam drain + beam behavior ----
        if (beamOn) {
            if (mana > 0.01f) {
                mana = tickBeam(sw, p, mana);
            } else {
                setBeamOn(p, false);
                beamOn = false;
            }
        }

        // ---- passive regen (this is the key fix) ----
        // Regen whenever you are NOT flight-draining (independent of beam).
        if (!flightDraining) {
            mana = Math.min(MANA_MAX, mana + MANA_REGEN_PER_TICK);
        }

        // ---- food mana helpers ----
        // berries top-up while eating
        if (p.isUsingItem()) {
            ItemStack a = p.getActiveItem();
            if (a.isOf(Items.SWEET_BERRIES) || a.isOf(Items.GLOW_BERRIES)) {
                mana = Math.min(MANA_MAX, mana + 0.8f);
            }
        }

        // golden apple burst on successful eat
        mana = applyGoldenAppleMana(sw, p, mana);

        // stop flight if mana depleted
        if (mana <= 0f) {
            mana = 0f;
            if (p.getAbilities().flying) p.getAbilities().flying = false;
            setAllowFlyingIfChanged(p, false);
            p.sendAbilitiesUpdate();

            if (beamOn) setBeamOn(p, false);
        }

        setMana(p, mana);
        maybeSyncHud(sw, p, mana);

        // tiny safety: avoid fall spikes when toggling cast mid-drop
        if (casting) p.fallDistance = 0f;
    }

    /** Adds a fixed mana burst when a golden apple finishes eating. */
    private static float applyGoldenAppleMana(ServerWorld sw, ServerPlayerEntity p, float mana) {
        UUID id = p.getUuid();

        boolean using = p.isUsingItem();
        ItemStack active = p.getActiveItem();

        int kindNow = 0;
        if (using) {
            if (active.isOf(Items.ENCHANTED_GOLDEN_APPLE)) kindNow = 2;
            else if (active.isOf(Items.GOLDEN_APPLE)) kindNow = 1;
        }

        long prev = LAST_EAT_KIND.getOrDefault(id, 0L);
        int prevKind = (int)(prev & 3L);
        boolean prevUsing = (prev & 4L) != 0L;

        boolean usingNow = (kindNow != 0);

        // track countdown while using
        if (usingNow) {
            LAST_EAT_KIND.put(id, (long)kindNow | 4L);
            LAST_EAT_LEFT.put(id, p.getItemUseTimeLeft());
            return mana;
        }

        // just stopped using -> only pay if they were basically finished
        if (!usingNow && prevUsing && prevKind != 0) {
            long left = LAST_EAT_LEFT.getOrDefault(id, 999L);
            LAST_EAT_KIND.removeLong(id);
            LAST_EAT_LEFT.removeLong(id);

            if (left <= 1L) {
                float add = (prevKind == 2) ? ENCHANTED_GAPPLE_MANA_BURST : GAPPLE_MANA_BURST;
                return Math.min(MANA_MAX, mana + add);
            }
        }

        return mana;
    }

    private static void maybeSyncHud(ServerWorld sw, ServerPlayerEntity p, float mana) {
        UUID id = p.getUuid();
        long now = sw.getTime();

        float last = LAST_SENT_MANA.getOrDefault(id, -9999f);
        long lastT = LAST_SENT_TICK.getOrDefault(id, -9999L);

        boolean time = (now - lastT) >= MANA_SYNC_INTERVAL;
        boolean changed = Math.abs(mana - last) >= 0.10f;

        if (time || changed) {
            LAST_SENT_MANA.put(id, mana);
            LAST_SENT_TICK.put(id, now);
            sendManaHud(p, true, mana);
        }
    }

    /* ---------------- Registration ---------------- */

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                var pow = Powers.get(PowerAPI.get(p));
                if (pow instanceof FairyPower) {
                    serverTick(p);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_CAST, (server, player, handler, buf, resp) -> {
            boolean enable = buf.readBoolean();
            server.execute(() -> {
                CastLogic.toggleCastForm(player, enable);
                sendCastState(player, enable);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_CAST_COMMIT, (server, player, handler, buf, resp) -> {
            byte a0 = buf.readByte(), a1 = buf.readByte(), a2 = buf.readByte();
            var spell = net.seep.odd.abilities.fairy.FairySpell.fromInputs(a0, a1, a2);
            server.execute(() -> net.seep.odd.abilities.fairy.CastLogic.tryFireRay(player, spell));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_MENU, (server, player, handler, buf, resp) ->
                server.execute(() -> net.seep.odd.abilities.fairy.FlowerMenu.openFor(player)));

        // optional: lets clients toggle beam directly
        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_BEAM, (server, player, handler, buf, resp) -> {
            boolean enable = buf.readBoolean();
            server.execute(() -> {
                setBeamOn(player, enable);
                player.sendMessage(Text.literal(enable ? "Beam: ON" : "Beam: OFF"), true);
            });
        });
    }

    /* ---------------- Mana persistence ---------------- */

    public static float getMana(PlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return 0f;
        return ManaState.of(sw).get(p.getUuid());
    }

    public static void setMana(PlayerEntity p, float v) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        ManaState.of(sw).put(p.getUuid(), Math.max(0f, Math.min(MANA_MAX, v)));
    }

    private static final class ManaState extends PersistentState {
        private final Object2FloatOpenHashMap<UUID> mana = new Object2FloatOpenHashMap<>();

        float get(UUID id) { return mana.getOrDefault(id, MANA_MAX); }
        void put(UUID id, float v) { mana.put(id, v); markDirty(); }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (Object2FloatMap.Entry<UUID> e : mana.object2FloatEntrySet()) {
                NbtCompound c = new NbtCompound();
                c.putUuid("id", e.getKey());
                c.putFloat("m", e.getFloatValue());
                list.add(c);
            }
            nbt.put("list", list);
            return nbt;
        }

        static ManaState fromNbt(NbtCompound nbt) {
            ManaState s = new ManaState();
            NbtList list = nbt.getList("list", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                s.mana.put(c.getUuid("id"), c.getFloat("m"));
            }
            return s;
        }

        static ManaState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(ManaState::fromNbt, ManaState::new, "odd_fairy_mana");
        }
    }

    /* ---------------- Beam persistence ---------------- */

    private static final class BeamState extends PersistentState {
        private final Set<UUID> on = new HashSet<>();

        boolean isOn(UUID id) { return on.contains(id); }
        void set(UUID id, boolean enable) { if (enable) on.add(id); else on.remove(id); markDirty(); }

        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (UUID id : on) {
                NbtCompound c = new NbtCompound();
                c.putUuid("id", id);
                list.add(c);
            }
            nbt.put("on", list);
            return nbt;
        }

        static BeamState fromNbt(NbtCompound nbt) {
            BeamState s = new BeamState();
            NbtList list = nbt.getList("on", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                s.on.add(list.getCompound(i).getUuid("id"));
            }
            return s;
        }

        static BeamState of(ServerWorld world) {
            return world.getPersistentStateManager().getOrCreate(BeamState::fromNbt, BeamState::new, "odd_fairy_beam");
        }
    }

    private static boolean isBeamOn(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return false;
        return BeamState.of(sw).isOn(p.getUuid());
    }

    private static void setBeamOn(ServerPlayerEntity p, boolean on) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        BeamState.of(sw).set(p.getUuid(), on);
    }

    /* ---------------- Flight helpers ---------------- */

    private static void setAllowFlying(ServerPlayerEntity p, boolean on) {
        p.getAbilities().allowFlying = on;
        if (!on) p.getAbilities().flying = false;
        p.sendAbilitiesUpdate();
    }

    private static void setAllowFlyingIfChanged(ServerPlayerEntity p, boolean on) {
        UUID id = p.getUuid();
        float prev = LAST_ALLOW.getOrDefault(id, -1f);
        float now = on ? 1f : 0f;
        if (prev != now) {
            LAST_ALLOW.put(id, now);
            p.getAbilities().allowFlying = on;
            if (!on) p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
        }
    }

    /* ---------------- Particles ---------------- */

    // flight glitter
    private static void spawnFlightSparkles(ServerWorld sw, ServerPlayerEntity p) {
        if (SPARKLES_PER_TICK <= 0) return;

        double px = p.getX();
        double py = p.getY() + 0.45;
        double pz = p.getZ();

        for (int i = 0; i < SPARKLES_PER_TICK; i++) {
            double ox = (sw.random.nextDouble() - 0.5) * 0.35;
            double oy = (sw.random.nextDouble() - 0.5) * 0.25;
            double oz = (sw.random.nextDouble() - 0.5) * 0.35;

            sw.spawnParticles(
                    net.seep.odd.particles.OddParticles.FAIRY_SPARKLES,
                    px + ox, py + oy, pz + oz,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
        }
    }

    // beam (drain + visuals + recharge)
    private static float tickBeam(ServerWorld sw, ServerPlayerEntity p, float mana) {
        // pay cost
        if (mana <= BEAM_DRAIN_PER_TICK + 0.001f) {
            setBeamOn(p, false);
            return mana;
        }
        mana = Math.max(0f, mana - BEAM_DRAIN_PER_TICK);

        // ✅ spawn lower
        Vec3d start = p.getCameraPosVec(1f).subtract(0.0, 0.45, 0.0);
        Vec3d dir   = p.getRotationVector().normalize();
        Vec3d end   = start.add(dir.multiply(BEAM_RANGE));

        HitResult hr = sw.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));

        Vec3d hitPos = (hr != null) ? hr.getPos() : end;
        double maxDist = Math.min(BEAM_RANGE, hitPos.distanceTo(start));

        FalseFlowerBlockEntity flower = null;

        if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
            BlockPos bp = ((BlockHitResult) hr).getBlockPos();
            BlockEntity be = sw.getBlockEntity(bp);
            if (be instanceof FalseFlowerBlockEntity f) flower = f;
        }
        if (flower == null) {
            flower = findFlowerSoft(sw, start, dir, maxDist, BEAM_SOFT_RADIUS);
        }

        // beam particles along the ray
        int segs = Math.max(4, BEAM_SEGMENTS);
        for (int i = 0; i < segs; i++) {
            double t = (i + 0.5) / (double) segs;
            Vec3d pt = start.add(dir.multiply(maxDist * t));

            double jx = (sw.random.nextDouble() - 0.5) * 0.10;
            double jy = (sw.random.nextDouble() - 0.5) * 0.10;
            double jz = (sw.random.nextDouble() - 0.5) * 0.10;

            sw.spawnParticles(
                    net.seep.odd.particles.OddParticles.FAIRY_SPARKLES,
                    pt.x + jx, pt.y + jy, pt.z + jz,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
        }

        // recharge + flash on the flower
        if (flower != null) {
            flower.addMana(BEAM_RECHARGE_PER_TICK);

            long now = sw.getTime();
            UUID id = p.getUuid();
            long lastFlash = LAST_BEAM_FLASH.getOrDefault(id, Long.MIN_VALUE);

            if (now - lastFlash >= BEAM_FLASH_EVERY_TICKS) {
                LAST_BEAM_FLASH.put(id, now);

                Vec3d c = Vec3d.ofCenter(flower.getPos()).add(0, 0.25, 0);

                // quick bright flash
                sw.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0.0);

                // sparkle burst
                sw.spawnParticles(
                        net.seep.odd.particles.OddParticles.FAIRY_SPARKLES,
                        c.x, c.y, c.z,
                        10,
                        0.18, 0.18, 0.18,
                        0.0
                );

                // subtle streaks
                sw.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 4, 0.12, 0.12, 0.12, 0.0);
            }
        }

        return mana;
    }

    private static FalseFlowerBlockEntity findFlowerSoft(ServerWorld w, Vec3d start, Vec3d dir, double maxRange, double softRadius) {
        FalseFlowerBlockEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        Vec3d d = dir.normalize();
        double step = 0.5;

        for (double t = 0; t <= maxRange; t += step) {
            Vec3d pt = start.add(d.multiply(t));
            BlockPos base = BlockPos.ofFloored(pt);

            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos bp = base.add(dx, dy, dz);
                BlockEntity be = w.getBlockEntity(bp);
                if (!(be instanceof FalseFlowerBlockEntity flower)) continue;

                Vec3d c = Vec3d.ofCenter(bp);
                double d2 = c.squaredDistanceTo(pt);

                if (d2 <= softRadius * softRadius && d2 < bestD2) {
                    best = flower;
                    bestD2 = d2;
                }
            }

            if (best != null && bestD2 < 0.15) break;
        }

        return best;
    }

    /* ---------------- HP & Pehkui helpers ---------------- */

    private static void applyHalfHearts(ServerPlayerEntity p) {
        var inst = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst == null) return;
        inst.removeModifier(HP_MOD_ID);
        double target = 12.0; // 6 hearts
        double delta = target - inst.getBaseValue();
        inst.addPersistentModifier(new EntityAttributeModifier(HP_MOD_ID, "FairyHP", delta, EntityAttributeModifier.Operation.ADDITION));
        if (p.getHealth() > 12f) p.setHealth(12f);
    }

    private static void clearHalfHearts(ServerPlayerEntity p) {
        var inst = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst != null) inst.removeModifier(HP_MOD_ID);
    }

    private static final BiConsumer<net.minecraft.entity.Entity, Float> PEHKUI_SCALE_SET = findPehkuiSetter();

    private static BiConsumer<net.minecraft.entity.Entity, Float> findPehkuiSetter() {
        try {
            Class<?> scaleTypesCl = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Object BASE = scaleTypesCl.getField("BASE").get(null);

            Class<?> scaleTypeCl = Class.forName("virtuoel.pehkui.api.ScaleType");
            Method getScaleData = scaleTypeCl.getMethod("getScaleData", net.minecraft.entity.Entity.class);

            Class<?> scaleDataCl = Class.forName("virtuoel.pehkui.api.ScaleData");
            Method setScale = scaleDataCl.getMethod("setScale", float.class);

            return (entity, scale) -> {
                try {
                    Object data = getScaleData.invoke(BASE, entity);
                    setScale.invoke(data, scale);
                } catch (Throwable ignored) {}
            };
        } catch (Throwable t) {
            return (e, s) -> {};
        }
    }

    public static void applyPehkuiScale(PlayerEntity p, float s) {
        try { PEHKUI_SCALE_SET.accept(p, s); } catch (Throwable ignored) {}
    }
}
