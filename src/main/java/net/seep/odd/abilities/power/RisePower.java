package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.rise.entity.RisenZombieEntity;
import net.seep.odd.entity.ModEntities;

import java.util.UUID;

public final class RisePower implements Power {

    // ✅ Used ONLY for particles now (no orb renderer).
    public static final Identifier RISE_SOUL_ADD_S2C    = new Identifier(Oddities.MOD_ID, "rise_soul_add_s2c");
    public static final Identifier RISE_SOUL_REMOVE_S2C = new Identifier(Oddities.MOD_ID, "rise_soul_remove_s2c");

    // client can request a re-sync of active souls (helps if packets were missed)
    public static final Identifier RISE_SOUL_SYNC_C2S   = new Identifier(Oddities.MOD_ID, "rise_soul_sync_c2s");

    // casting packets (to this player only)
    public static final Identifier RISE_CAST_START_S2C  = new Identifier(Oddities.MOD_ID, "rise_cast_start_s2c");
    public static final Identifier RISE_CAST_CANCEL_S2C = new Identifier(Oddities.MOD_ID, "rise_cast_cancel_s2c");

    private static final int SOUL_LIFETIME_TICKS = 20 * 10;

    private static final double RAISE_RANGE = 6.0;
    private static final double RAISE_RANGE_SQ = RAISE_RANGE * RAISE_RANGE;

    private static final double MAX_ALLOWED_MAX_HP_EXCLUSIVE = 25.0;
    private static final int MAX_MINIONS = 10;

    private static final double BASE_RISEN_HP  = 20.0;
    private static final double BASE_RISEN_ATK = 4.0;

    // casting behavior
    private static final int CAST_DELAY_TICKS = 14; // 0.7 sec
    private static final int CAST_SLOW_TICKS  = 20; // 1.0 sec
    private static final int CPM_ANIM_TICKS   = 26; // 1.3 sec

    private static final Object2ObjectOpenHashMap<UUID, Int2ObjectOpenHashMap<Soul>> SOULS = new Object2ObjectOpenHashMap<>();
    private static final Object2IntOpenHashMap<UUID> NEXT_SOUL_ID = new Object2IntOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<UUID, PendingCast> PENDING = new Object2ObjectOpenHashMap<>();

    private static boolean tickHooked = false;

    private record Soul(
            int id,
            Vec3d pos,
            String typeId,
            NbtCompound renderNbt,
            long expiresAtOverworldTime,
            double addMaxHp,
            double addAttackDamage,
            boolean canFly
    ) {}

    private record PendingCast(int soulId, long executeAtOverworldTime) {}

    @Override public String id() { return "rise"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot); }
    @Override public long cooldownTicks() { return 10; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/rise_revive.png");
            default -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return "Necromancy: after you kill a weak monster, its soul lingers for 10s as green wisps (only you can see). "
                + "Activate near the corpse position to raise a Risen Zombie minion that follows and defends you. "
                + "The minion visually matches the slain monster with a dark-green overlay. (Max 10.)";
    }

    @Override
    public String longDescription() {
        return "Rise (Necromancy): kills on weak monsters create a 10-second revive window (shown as green wisps only you can see). "
                + "Activate near it to raise a loyal Risen Zombie that follows and defends you, visually mimicking the slain monster. "
                + "Max 10 active risen.";
    }

    public static boolean hasRise(net.minecraft.entity.player.PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        String current = PowerAPI.get(sp);
        return "rise".equals(current);
    }

    /** Call once in common init. */
    public static void registerNetworking() {

        // ✅ self-tick so delayed revive + soul expiry ALWAYS works
        if (!tickHooked) {
            tickHooked = true;
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
                    UUID id = sp.getUuid();
                    if (hasRise(sp) || PENDING.containsKey(id) || SOULS.containsKey(id)) {
                        serverTick(sp);
                    }
                }
            });
        }

        // ✅ client requests active souls (now used for PARTICLES)
        ServerPlayNetworking.registerGlobalReceiver(RISE_SOUL_SYNC_C2S, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> sendAllSouls(player));
        });

        // ✅ also auto-sync on join (safe)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> sendAllSouls(handler.player));
        });

        // create souls on kills
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, attacker, killed) -> {
            if (!(attacker instanceof ServerPlayerEntity player)) return;
            if (!hasRise(player)) return;

            LivingEntity living = killed;

            boolean ok =
                    (living.getType().getSpawnGroup() == SpawnGroup.MONSTER)
                            || (living instanceof net.minecraft.entity.mob.HostileEntity);

            if (!ok) return;
            if (living instanceof RisenZombieEntity) return;

            double maxHp = living.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
            if (!(maxHp < MAX_ALLOWED_MAX_HP_EXCLUSIVE)) return;

            addSoul(player, living);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            SOULS.remove(id);
            NEXT_SOUL_ID.removeInt(id);
            PENDING.remove(id);
        });
    }

    /** Server-authoritative tick. */
    public static void serverTick(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        if (!hasRise(player)) {
            cancelPending(player);
            Int2ObjectOpenHashMap<Soul> map = SOULS.remove(id);
            if (map != null && !map.isEmpty()) {
                for (Soul s : map.values()) sendSoulRemove(player, s.id);
            }
            NEXT_SOUL_ID.removeInt(id);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;
        long now = server.getOverworld().getTime();

        // expire souls
        Int2ObjectOpenHashMap<Soul> map = SOULS.get(id);
        if (map != null && !map.isEmpty()) {
            PendingCast pc = PENDING.get(id);

            var it = map.int2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                Soul s = e.getValue();
                if (s.expiresAtOverworldTime <= now) {
                    if (pc != null && pc.soulId == s.id) {
                        cancelPending(player);
                        pc = null;
                    }
                    it.remove();
                    sendSoulRemove(player, s.id);
                }
            }
            if (map.isEmpty()) SOULS.remove(id);
        }

        // process pending cast
        PendingCast pc = PENDING.get(id);
        if (pc == null) return;

        if (!player.isAlive() || player.isRemoved()) {
            cancelPending(player);
            return;
        }

        Int2ObjectOpenHashMap<Soul> souls = SOULS.get(id);
        if (souls == null) {
            cancelPending(player);
            return;
        }

        Soul s = souls.get(pc.soulId);
        if (s == null) {
            cancelPending(player);
            return;
        }

        if (player.getPos().squaredDistanceTo(s.pos) > RAISE_RANGE_SQ) {
            cancelPending(player);
            return;
        }

        if (now < pc.executeAtOverworldTime) return;

        if (countOwnedRisen(player) >= MAX_MINIONS) {
            cancelPending(player);
            player.getWorld().playSound(
                    null,
                    player.getBlockPos(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.PLAYERS,
                    0.7F,
                    0.6F
            );
            return;
        }

        // consume soul (particles stop immediately)
        souls.remove(pc.soulId);
        sendSoulRemove(player, pc.soulId);

        // spawn risen
        RisenZombieEntity risen = new RisenZombieEntity(ModEntities.RISEN_ZOMBIE, player.getWorld());
        risen.refreshPositionAndAngles(s.pos.x, s.pos.y - 0.25, s.pos.z, player.getYaw(), 0.0F);

        risen.setTamed(true);
        risen.setOwnerUuid(player.getUuid());
        risen.setRiseOwnerUuid(player.getUuid());

        risen.setCustomName(null);
        risen.setCustomNameVisible(false);

        risen.setSourceTypeId(s.typeId);
        risen.setSourceRenderNbt(s.renderNbt);

        risen.setCanFly(s.canFly);

        applyBonusAttributes(risen, s.addMaxHp, s.addAttackDamage);

        player.getWorld().spawnEntity(risen);

        player.getWorld().playSound(
                null,
                s.pos.x, s.pos.y, s.pos.z,
                SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED,
                SoundCategory.PLAYERS,
                1.0F,
                0.85F
        );

        if (souls.isEmpty()) SOULS.remove(id);
        PENDING.remove(id);
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        if (player.getWorld().isClient) return;
        if (!hasRise(player)) return;

        UUID id = player.getUuid();
        if (PENDING.containsKey(id)) return;

        Int2ObjectOpenHashMap<Soul> map = SOULS.get(id);
        if (map == null || map.isEmpty()) return;

        Vec3d p = player.getPos();
        Soul best = null;
        double bestDist = Double.MAX_VALUE;

        for (Soul s : map.values()) {
            double d = s.pos.squaredDistanceTo(p);
            if (d <= RAISE_RANGE_SQ && d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        if (best == null) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;
        long now = server.getOverworld().getTime();

        // slow 1s (no particles/icon)
        try {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, CAST_SLOW_TICKS, 1, false, false, false));
        } catch (Throwable ignored) {}

        PENDING.put(id, new PendingCast(best.id, now + CAST_DELAY_TICKS));

        // still send cast packet for CPM/third-person client logic
        int remaining = safeRemainingTicks(best.expiresAtOverworldTime, now);
        sendCastStart(player, best.id, CAST_DELAY_TICKS, CPM_ANIM_TICKS, best.pos, remaining);
    }

    @Override public void activateSecondary(ServerPlayerEntity player) {}

    private static void cancelPending(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        PendingCast pc = PENDING.remove(id);
        if (pc != null) sendCastCancel(player, pc.soulId);
    }

    private static void applyBonusAttributes(RisenZombieEntity risen, double addHp, double addAtk) {
        EntityAttributeInstance mh = risen.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (mh != null && addHp > 0.0) mh.setBaseValue(mh.getBaseValue() + addHp);

        EntityAttributeInstance ad = risen.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (ad != null && addAtk > 0.0) ad.setBaseValue(ad.getBaseValue() + addAtk);

        risen.setHealth((float) risen.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH));
    }

    private static int countOwnedRisen(ServerPlayerEntity player) {
        UUID owner = player.getUuid();
        Box box = new Box(-30000000, -4096, -30000000, 30000000, 4096, 30000000);

        return player.getWorld().getEntitiesByClass(
                RisenZombieEntity.class,
                box,
                e -> e != null && e.isAlive() && owner.equals(e.getRiseOwnerUuid())
        ).size();
    }

    private static void addSoul(ServerPlayerEntity player, LivingEntity killed) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        long now = server.getOverworld().getTime();
        long expiresAt = now + SOUL_LIFETIME_TICKS;

        UUID puid = player.getUuid();
        Int2ObjectOpenHashMap<Soul> map = SOULS.computeIfAbsent(puid, u -> new Int2ObjectOpenHashMap<>());

        int next = NEXT_SOUL_ID.getInt(puid) + 1;
        NEXT_SOUL_ID.put(puid, next);

        Vec3d pos = killed.getPos().add(0.0, killed.getHeight() * 0.5, 0.0);

        String typeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(killed.getType()).toString();
        NbtCompound renderNbt = sanitizeRenderNbt(killed);

        double mobHp  = safeAttrValue(killed, EntityAttributes.GENERIC_MAX_HEALTH);
        double mobAtk = safeAttrValue(killed, EntityAttributes.GENERIC_ATTACK_DAMAGE);

        double addHp  = Math.max(0.0, mobHp  - BASE_RISEN_HP);
        double addAtk = Math.max(0.0, mobAtk - BASE_RISEN_ATK);

        boolean canFly = killed.hasNoGravity() || isInstanceOf(killed, "net.minecraft.entity.mob.FlyingEntity");

        Soul soul = new Soul(next, pos, typeId, renderNbt, expiresAt, addHp, addAtk, canFly);
        map.put(next, soul);

        // ✅ send particles marker to this player only
        sendSoulAdd(player, soul, SOUL_LIFETIME_TICKS);
    }

    private static boolean isInstanceOf(Object obj, String className) {
        try { return Class.forName(className).isInstance(obj); }
        catch (Throwable t) { return false; }
    }

    private static double safeAttrValue(LivingEntity e, EntityAttribute attr) {
        try {
            EntityAttributeInstance inst = e.getAttributeInstance(attr);
            if (inst == null) return 0.0;
            return inst.getValue();
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static NbtCompound sanitizeRenderNbt(LivingEntity killed) {
        NbtCompound nbt = new NbtCompound();
        killed.writeNbt(nbt);

        // movement / identity
        nbt.remove("UUID");
        nbt.remove("Pos");
        nbt.remove("Motion");
        nbt.remove("Rotation");
        nbt.remove("Passengers");
        nbt.remove("Leash");
        nbt.remove("PortalCooldown");
        nbt.remove("FallDistance");
        nbt.remove("Fire");
        nbt.remove("Air");
        nbt.remove("OnGround");

        // prevent red snapshot carryover
        nbt.remove("HurtTime");
        nbt.remove("HurtByTimestamp");
        nbt.remove("DeathTime");
        nbt.remove("Health");
        nbt.remove("AbsorptionAmount");
        nbt.remove("FallFlying");

        // prevent names
        nbt.remove("CustomName");
        nbt.remove("CustomNameVisible");

        // big/unstable
        nbt.remove("Brain");
        nbt.remove("Inventory");
        nbt.remove("Items");
        nbt.remove("ArmorItems");
        nbt.remove("HandItems");
        nbt.remove("EnderItems");
        nbt.remove("Offers");

        return nbt;
    }

    // --------- SYNC (particles only) ---------

    private static void sendAllSouls(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID id = player.getUuid();
        Int2ObjectOpenHashMap<Soul> map = SOULS.get(id);
        if (map == null || map.isEmpty()) return;

        long now = server.getOverworld().getTime();
        for (Soul s : map.values()) {
            int remaining = safeRemainingTicks(s.expiresAtOverworldTime, now);
            sendSoulAdd(player, s, remaining);
        }
    }

    private static int safeRemainingTicks(long expiresAt, long now) {
        long rem = expiresAt - now;
        if (rem < 1) rem = 1;
        if (rem > 20 * 60) rem = 20 * 60;
        return (int) rem;
    }

    // --------- PACKETS ---------

    private static void sendSoulAdd(ServerPlayerEntity player, Soul s, int lifetimeTicks) {
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeVarInt(s.id);
        out.writeDouble(s.pos.x);
        out.writeDouble(s.pos.y);
        out.writeDouble(s.pos.z);
        out.writeVarInt(lifetimeTicks);
        ServerPlayNetworking.send(player, RISE_SOUL_ADD_S2C, out);
    }

    private static void sendSoulRemove(ServerPlayerEntity player, int soulId) {
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeVarInt(soulId);
        ServerPlayNetworking.send(player, RISE_SOUL_REMOVE_S2C, out);
    }

    private static void sendCastStart(ServerPlayerEntity player, int soulId, int delayTicks, int animTicks, Vec3d pos, int remainingLifetimeTicks) {
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeVarInt(soulId);
        out.writeVarInt(delayTicks);
        out.writeVarInt(animTicks);

        // keep these for client-side cast FX logic (even if no orb)
        out.writeDouble(pos.x);
        out.writeDouble(pos.y);
        out.writeDouble(pos.z);
        out.writeVarInt(remainingLifetimeTicks);

        ServerPlayNetworking.send(player, RISE_CAST_START_S2C, out);
    }

    private static void sendCastCancel(ServerPlayerEntity player, int soulId) {
        PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        out.writeVarInt(soulId);
        ServerPlayNetworking.send(player, RISE_CAST_CANCEL_S2C, out);
    }
}
