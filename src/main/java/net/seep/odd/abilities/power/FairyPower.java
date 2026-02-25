package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.RaycastContext;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.fairy.CastLogic;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;
import net.seep.odd.status.ModStatusEffects;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

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
    public static final Identifier C2S_TOGGLE_BEAM  = new Identifier(Oddities.MOD_ID, "fairy/toggle_beam");

    public static final Identifier S2C_CAST_STATE   = new Identifier(Oddities.MOD_ID, "fairy/cast_state");
    public static final Identifier S2C_MANA_SYNC    = new Identifier(Oddities.MOD_ID, "fairy/mana_sync");
    public static final Identifier S2C_BEAM_STATE   = new Identifier(Oddities.MOD_ID, "fairy/beam_state");

    // ✅ CPM beam control (player-only)
    public static final Identifier S2C_CPM_BEAM     = new Identifier(Oddities.MOD_ID, "fairy/cpm_beam");

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

    private static void sendBeamStateTo(ServerPlayerEntity target, UUID who, boolean on) {
        var b = PacketByteBufs.create();
        b.writeUuid(who);
        b.writeBoolean(on);
        ServerPlayNetworking.send(target, S2C_BEAM_STATE, b);
    }

    private static void sendCpmBeam(ServerPlayerEntity player, boolean on) {
        var b = PacketByteBufs.create();
        b.writeBoolean(on);
        ServerPlayNetworking.send(player, S2C_CPM_BEAM, b);
    }

    private static void broadcastBeamState(ServerPlayerEntity source, boolean on) {
        UUID id = source.getUuid();
        sendBeamStateTo(source, id, on);
        for (ServerPlayerEntity other : PlayerLookup.tracking(source)) {
            sendBeamStateTo(other, id, on);
        }
    }

    /* ---------------- Mana + flight tuning ---------------- */

    public static final float MANA_MAX = 100f;

    public static float MANA_REGEN_PER_TICK = 0.030f;
    public static float FLIGHT_DRAIN_PER_TICK = 0.30f;
    public static int MANA_SYNC_INTERVAL = 5;

    public static int SPARKLES_PER_TICK = 2;
    public static float CAST_COST = 5f;

    public static float BEAM_DRAIN_PER_TICK = 0.22f;
    public static float BEAM_RECHARGE_PER_TICK = 2.0f;
    public static double BEAM_RANGE = 32.0;
    public static float BEAM_SOFT_RADIUS = 1.35f;

    public static float GAPPLE_MANA_BURST = 22.0f;
    public static float ENCHANTED_GAPPLE_MANA_BURST = 45.0f;

    public static final UUID HP_MOD_ID = UUID.fromString("7a2d89d0-3c80-4caa-82d6-6e2e1f8d66c1");

    /* ---------------- Beam extras ---------------- */

    // flower recharge sound pacing
    private static final Object2LongOpenHashMap<UUID> LAST_FLOWER_SOUND_T = new Object2LongOpenHashMap<>();
    private static final int FLOWER_SOUND_EVERY_TICKS = 8;

    /* ---------------- POWERLESS + enter/exit tracking ---------------- */

    private static final Set<UUID> FAIRY_ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> POWERLESS_HANDLED = ConcurrentHashMap.newKeySet();

    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void setBeamOnAndSync(ServerPlayerEntity p, boolean on) {
        setBeamOn(p, on);

        // ✅ CPM beam animation for THIS player only
        sendCpmBeam(p, on);

        // ✅ stop any current use immediately (prevents “start eating then toggle beam” exploit)
        if (on) p.stopUsingItem();

        // render beam for everyone tracking
        broadcastBeamState(p, on);
    }

    private static boolean blockIfPowerless(ServerPlayerEntity p) {
        if (!isPowerless(p)) return false;
        if (POWERLESS_HANDLED.add(p.getUuid())) {
            if (isBeamOn(p)) setBeamOnAndSync(p, false);
            p.sendMessage(Text.literal("§cYou are powerless."), true);
        }
        return true;
    }

    private static void ensureEnterFairy(ServerPlayerEntity p) {
        if (!FAIRY_ACTIVE.add(p.getUuid())) return;

        applyHalfHearts(p);
        applyPehkuiScale(p, 0.5f);

        float mana = getMana(p);
        if (mana <= 0f) {
            setMana(p, MANA_MAX);
            mana = MANA_MAX;
        }

        sendManaHud(p, true, mana);
    }

    private static void ensureExitFairy(ServerPlayerEntity p) {
        if (!FAIRY_ACTIVE.remove(p.getUuid())) return;

        clearHalfHearts(p);
        applyPehkuiScale(p, 1.0f);

        CastLogic.toggleCastForm(p, false);

        // stop beam and CPM anim
        setBeamOnAndSync(p, false);

        setAllowFlying(p, false);
        sendManaHud(p, false, 0f);

        UUID id = p.getUuid();
        LAST_SENT_MANA.removeFloat(id);
        LAST_SENT_TICK.removeLong(id);
        LAST_ALLOW.removeFloat(id);
        PREV_CAST.removeFloat(id);
        LAST_FLYING.removeFloat(id);

        LAST_EAT_KIND.removeLong(id);
        LAST_EAT_LEFT.removeLong(id);

        LAST_FLOWER_SOUND_T.removeLong(id);
        POWERLESS_HANDLED.remove(id);

        // hard-stop CPM beam just in case
        sendCpmBeam(p, false);
    }

    /* ---------------- Power actions ---------------- */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (blockIfPowerless(player)) return;
        if (isBeamOn(player)) return; // beam locks actions

        boolean next = !CastLogic.isCastFormEnabled(player);
        CastLogic.toggleCastForm(player, next);
        sendCastState(player, next);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (blockIfPowerless(player)) return;
        if (isBeamOn(player)) return; // beam locks actions
        net.seep.odd.abilities.fairy.FlowerMenu.openFor(player);
    }

    @Override
    public void activateThird(ServerPlayerEntity player) {
        if (blockIfPowerless(player)) return;

        boolean next = !isBeamOn(player);
        setBeamOnAndSync(player, next);

        player.sendMessage(Text.literal(next ? "Beam: ON" : "Beam: OFF"), true);
    }

    /* ---------------- Tick ---------------- */

    private static final Object2FloatOpenHashMap<UUID> LAST_SENT_MANA  = new Object2FloatOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID>  LAST_SENT_TICK  = new Object2LongOpenHashMap<>();
    private static final Object2FloatOpenHashMap<UUID> LAST_ALLOW      = new Object2FloatOpenHashMap<>();

    private static final Object2FloatOpenHashMap<UUID> PREV_CAST       = new Object2FloatOpenHashMap<>();
    private static final Object2FloatOpenHashMap<UUID> LAST_FLYING     = new Object2FloatOpenHashMap<>();

    private static final Object2LongOpenHashMap<UUID> LAST_EAT_KIND = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> LAST_EAT_LEFT = new Object2LongOpenHashMap<>();

    public static void serverTick(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        UUID id = p.getUuid();

        boolean powerless = isPowerless(p);
        if (!powerless) {
            POWERLESS_HANDLED.remove(id);
        } else {
            if (isBeamOn(p) && POWERLESS_HANDLED.add(id)) {
                setBeamOnAndSync(p, false);
            }
            if (p.getAbilities().flying || p.getAbilities().allowFlying) {
                p.getAbilities().flying = false;
                p.getAbilities().allowFlying = false;
                p.sendAbilitiesUpdate();
            }
        }

        boolean casting = CastLogic.isCastFormEnabled(p);
        if (!casting) {
            LAST_FLYING.put(id, p.getAbilities().flying ? 1f : 0f);
        }

        float mana = getMana(p);
        boolean flying = p.getAbilities().flying;

        boolean shouldAllow = !powerless && !casting && mana > 0.01f;
        setAllowFlyingIfChanged(p, shouldAllow);

        if (casting) p.fallDistance = 0f;
        PREV_CAST.put(id, casting ? 1f : 0f);

        boolean beamOn = isBeamOn(p);

        // ✅ un-buggable: if beam is on, you cannot eat/use items
        if (beamOn) p.stopUsingItem();

        boolean flightDraining = flying && !p.isOnGround() && shouldAllow;

        if (flightDraining) {
            mana = Math.max(0f, mana - FLIGHT_DRAIN_PER_TICK);
            spawnFlightSparkles(sw, p);
        }

        if (beamOn) {
            if (mana > 0.01f && !powerless) {
                mana = tickBeam(sw, p, mana);
            } else {
                setBeamOnAndSync(p, false);
                beamOn = false;
            }
        }

        if (!flightDraining) {
            mana = Math.min(MANA_MAX, mana + MANA_REGEN_PER_TICK);
        }

        // note: even if they somehow begin using (should be blocked), we still keep berry top-up logic
        if (p.isUsingItem()) {
            ItemStack a = p.getActiveItem();
            if (a.isOf(Items.SWEET_BERRIES) || a.isOf(Items.GLOW_BERRIES)) {
                mana = Math.min(MANA_MAX, mana + 0.8f);
            }
        }

        mana = applyGoldenAppleMana(sw, p, mana);

        if (mana <= 0f) {
            mana = 0f;

            if (p.getAbilities().flying) p.getAbilities().flying = false;
            setAllowFlyingIfChanged(p, false);
            p.sendAbilitiesUpdate();

            if (beamOn) setBeamOnAndSync(p, false);
        }

        setMana(p, mana);
        maybeSyncHud(sw, p, mana);
    }

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

        if (usingNow) {
            LAST_EAT_KIND.put(id, (long)kindNow | 4L);
            LAST_EAT_LEFT.put(id, p.getItemUseTimeLeft());
            return mana;
        }

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

    private static boolean USE_BLOCKERS_REGISTERED = false;

    public static void register() {
        if (!USE_BLOCKERS_REGISTERED) {
            USE_BLOCKERS_REGISTERED = true;

            // Block using items (eating, drinking, etc.)
            UseItemCallback.EVENT.register((player, world, hand) -> {
                if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
                if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(player.getStackInHand(hand));
                if (!"fairy".equals(PowerAPI.get(sp))) return TypedActionResult.pass(player.getStackInHand(hand));
                if (!isBeamOn(sp)) return TypedActionResult.pass(player.getStackInHand(hand));
                sp.stopUsingItem();
                return TypedActionResult.fail(player.getStackInHand(hand));
            });

            // Block using item on blocks (placing / interacting)
            UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
                if (world.isClient) return ActionResult.PASS;
                if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
                if (!"fairy".equals(PowerAPI.get(sp))) return ActionResult.PASS;
                if (!isBeamOn(sp)) return ActionResult.PASS;
                sp.stopUsingItem();
                return ActionResult.FAIL;
            });

            // Block using item on entities (interactions)
            UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
                if (world.isClient) return ActionResult.PASS;
                if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
                if (!"fairy".equals(PowerAPI.get(sp))) return ActionResult.PASS;
                if (!isBeamOn(sp)) return ActionResult.PASS;
                sp.stopUsingItem();
                return ActionResult.FAIL;
            });
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                boolean isFairy = "fairy".equals(PowerAPI.get(p));

                if (isFairy) {
                    ensureEnterFairy(p);
                    serverTick(p);
                } else {
                    ensureExitFairy(p);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_CAST, (server, player, handler, buf, resp) -> {
            boolean enable = buf.readBoolean();
            server.execute(() -> {
                if (!"fairy".equals(PowerAPI.get(player))) return;
                if (isPowerless(player)) return;
                if (isBeamOn(player)) return;
                CastLogic.toggleCastForm(player, enable);
                sendCastState(player, enable);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_CAST_COMMIT, (server, player, handler, buf, resp) -> {
            byte a0 = buf.readByte(), a1 = buf.readByte(), a2 = buf.readByte();
            var spell = net.seep.odd.abilities.fairy.FairySpell.fromInputs(a0, a1, a2);
            server.execute(() -> {
                if (!"fairy".equals(PowerAPI.get(player))) return;
                if (isPowerless(player)) return;
                if (isBeamOn(player)) return;
                net.seep.odd.abilities.fairy.CastLogic.tryFireRay(player, spell);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_MENU, (server, player, handler, buf, resp) ->
                server.execute(() -> {
                    if (!"fairy".equals(PowerAPI.get(player))) return;
                    if (isPowerless(player)) return;
                    if (isBeamOn(player)) return;
                    net.seep.odd.abilities.fairy.FlowerMenu.openFor(player);
                }));

        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_BEAM, (server, player, handler, buf, resp) -> {
            boolean enable = buf.readBoolean();
            server.execute(() -> {
                if (!"fairy".equals(PowerAPI.get(player))) return;
                if (isPowerless(player)) return;

                if (enable) player.stopUsingItem();
                setBeamOnAndSync(player, enable);

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

    /* ---------------- Particles + Beam behavior ---------------- */

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

    private static float tickBeam(ServerWorld sw, ServerPlayerEntity p, float mana) {
        if (mana <= BEAM_DRAIN_PER_TICK + 0.001f) {
            setBeamOnAndSync(p, false);
            return mana;
        }
        mana = Math.max(0f, mana - BEAM_DRAIN_PER_TICK);

        Vec3d dir = p.getRotationVector().normalize();
        double h = p.getDimensions(p.getPose()).height;

        Vec3d start = p.getPos()
                .add(0.0, h * 0.58, 0.0)
                .add(dir.multiply(h * 0.16));

        Vec3d end = start.add(dir.multiply(BEAM_RANGE));

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

        // ✅ landing/contact particles (always)
        sw.spawnParticles(ParticleTypes.END_ROD, hitPos.x, hitPos.y, hitPos.z, 2, 0.06, 0.06, 0.06, 0.0);
        sw.spawnParticles(ParticleTypes.ENCHANT, hitPos.x, hitPos.y, hitPos.z, 6, 0.10, 0.10, 0.10, 0.0);

        if (flower != null) {
            flower.addMana(BEAM_RECHARGE_PER_TICK);

            // extra obvious contact when it’s actually a flower
            Vec3d c = Vec3d.ofCenter(flower.getPos()).add(0, 0.25, 0);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 2, 0.10, 0.10, 0.10, 0.0);

            // ✅ recharge sound (player-only, paced)
            long now = sw.getTime();
            long last = LAST_FLOWER_SOUND_T.getOrDefault(p.getUuid(), Long.MIN_VALUE);
            if (now - last >= FLOWER_SOUND_EVERY_TICKS) {
                LAST_FLOWER_SOUND_T.put(p.getUuid(), now);
                p.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.35f, 1.85f);
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

    private static final BiConsumer<Entity, Float> PEHKUI_SCALE_SET = findPehkuiSetter();

    private static BiConsumer<Entity, Float> findPehkuiSetter() {
        try {
            Class<?> scaleTypesCl = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Object BASE = scaleTypesCl.getField("BASE").get(null);

            Class<?> scaleTypeCl = Class.forName("virtuoel.pehkui.api.ScaleType");
            Method getScaleData = scaleTypeCl.getMethod("getScaleData", Entity.class);

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