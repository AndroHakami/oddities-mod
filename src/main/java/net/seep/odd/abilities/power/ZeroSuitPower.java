// src/main/java/net/seep/odd/abilities/power/ZeroSuitPower.java
package net.seep.odd.abilities.power;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.Explosion;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.zerosuit.ZeroSuitCPM;
import net.seep.odd.abilities.zerosuit.ZeroSuitNet;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.zerosuit.ZeroBeamEntity;
import net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity;
import net.seep.odd.sound.ModSounds;

import java.util.*;

public final class ZeroSuitPower implements Power {

    private static final int MISSILE_MAX_TICKS = 20 * 12;

    private static final int   BLAST_CHARGE_MAX_T  = 20 * 8;
    private static final float BLAST_MAX_DAMAGE    = 3.0f;
    private static final int   BLAST_RANGE_BLOCKS  = 48;
    private static final int   BLAST_VIS_TICKS     = 12;

    private static final double BLAST_RADIUS_MIN    = 0.25;
    private static final double BLAST_RADIUS_MAX    = 1.40;
    private static final double BLAST_CORE_FRACTION = 0.55;

    private static final double BLAST_KB_BASE       = 0.55;
    private static final double BLAST_KB_MAX_ADD    = 6.25;
    private static final double BLAST_KB_CORE_MULT  = 1.35;
    private static final double BLAST_KB_UP_BASE    = 0.06;
    private static final double BLAST_KB_UP_MAX_ADD = 0.16;

    private static final double SELF_RECOIL_BASE    = 0.18;
    private static final double SELF_RECOIL_MAX_ADD = 0.65;


    // Beam victims: prevent fall damage (long enough to land)
    private static final int BEAM_NOFALL_TICKS = 20 * 12;

    private static final int HUD_SYNC_EVERY = 2;

    // === Third ability (orbital pillar) ===
    public static final Identifier S2C_ZERO_ORBITAL_STRIKE =
            new Identifier(Oddities.MOD_ID, "zero_orbital_strike");

    // === Third ability (radiation charge) ===
    public static final Identifier S2C_ZERO_RADIATION_BEGIN =
            new Identifier(Oddities.MOD_ID, "zero_radiation_begin");
    public static final Identifier S2C_ZERO_RADIATION_CANCEL =
            new Identifier(Oddities.MOD_ID, "zero_radiation_cancel");

    // How long the pillar shader stays alive
    private static final int ORBITAL_FX_TICKS = 20 * 7;

    // Radiation charge timeline
    private static final int ORBITAL_CHARGE_TICKS = 20 * 20;   // 20 seconds
    private static final float ORBITAL_MAX_RADIUS = 45.0f;     // blocks at 20s

    private static final double ORBITAL_RAYCAST_RANGE = 20.0;
    private static final double ORBITAL_BROADCAST_RANGE = 160.0;

    // ===== NEW: Pillar damage zone (NO MIXINS) =====
    private static final float  ORBITAL_PILLAR_DAMAGE_HP = 2.0f;       // 1 heart per tick (2 HP)
    private static final double ORBITAL_PILLAR_HEIGHT    = 400.0;      // “height above it”
    private static final double ORBITAL_PILLAR_RADIUS    = ORBITAL_MAX_RADIUS;

    private static final RegistryKey<DamageType> ORBITAL_BEAM_DAMAGE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Oddities.MOD_ID, "orbital_beam"));


    private static final class ActivePillar {
        final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim;
        final Vec3d pos;
        final UUID owner;
        int ticksLeft;

        ActivePillar(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim, Vec3d pos, int ticksLeft, UUID owner) {
            this.dim = dim;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
            this.owner = owner;
        }
    }

    private static final List<ActivePillar> ACTIVE_PILLARS = new ArrayList<>();
    private static final Map<net.minecraft.registry.RegistryKey<net.minecraft.world.World>, Long> LAST_PILLAR_TICK = new HashMap<>();

    private static void startPillarDamage(ServerWorld sw, Vec3d hit, ServerPlayerEntity owner) {
        ACTIVE_PILLARS.add(new ActivePillar(sw.getRegistryKey(), hit, ORBITAL_FX_TICKS, owner.getUuid()));
    }

    /**
     * Ticked from serverTick(...) but guarded to only run once per world tick.
     * This does TRUE "per tick" drain (no mixins) by draining absorption + health directly.
     * Creative + spectators are ignored.
     */
    private static void tickPillarDamage(ServerWorld sw) {
        if (ACTIVE_PILLARS.isEmpty()) return;

        var key = sw.getRegistryKey();
        long now = sw.getTime();

        Long last = LAST_PILLAR_TICK.put(key, now);
        if (last != null && last == now) return;

        // Iterate and tick only pillars in this world
        for (Iterator<ActivePillar> it = ACTIVE_PILLARS.iterator(); it.hasNext(); ) {
            ActivePillar ap = it.next();

            if (ap.dim != key) continue;

            ap.ticksLeft--;
            if (ap.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            double r = ORBITAL_PILLAR_RADIUS;
            double r2 = r * r;

            double y0 = ap.pos.y - 2.0;
            double y1 = Math.min(ap.pos.y + ORBITAL_PILLAR_HEIGHT, sw.getTopY() + 64.0);

            Box box = new Box(
                    ap.pos.x - r, y0, ap.pos.z - r,
                    ap.pos.x + r, y1, ap.pos.z + r
            );

            List<LivingEntity> victims = sw.getEntitiesByClass(LivingEntity.class, box, e -> {
                if (!e.isAlive()) return false;
                if (e instanceof PlayerEntity pe) {
                    if (pe.isSpectator()) return false;
                    if (pe.getAbilities().creativeMode) return false;
                }
                return true;
            });

            ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ap.owner);

            for (LivingEntity e : victims) {
                double dx = e.getX() - ap.pos.x;
                double dz = e.getZ() - ap.pos.z;
                if ((dx * dx + dz * dz) > r2) continue;

                // "True per tick" without mixins: drain absorption first, then health
                float dmg = ORBITAL_PILLAR_DAMAGE_HP;

                float abs = e.getAbsorptionAmount();
                float hp  = e.getHealth();

                float total = abs + hp;

                // If this tick would kill, do a real damage hit so death attribution is sane.
                if (dmg >= total - 1e-4f) {
                    if (owner != null) {
                        e.damage(sw.getDamageSources().create(ORBITAL_BEAM_DAMAGE, owner), total + 1.0f);
                    } else {
                        e.damage(sw.getDamageSources().create(ORBITAL_BEAM_DAMAGE), total + 1.0f);
                    }
                    continue;
                }


                // Otherwise: manual drain
                if (abs > 0f) {
                    float use = Math.min(abs, dmg);
                    e.setAbsorptionAmount(abs - use);
                    dmg -= use;
                }
                if (dmg > 0f) {
                    e.setHealth(hp - dmg);
                }
            }
        }
    }

    @Override public String id() { return "zero_suit"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_gravity.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_blast.png");
            case "third"     -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_nuke.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() { return "Remote missile control + a chargeable piercing beam + annihilation."; }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Missile: Launch a controllable missile. Camera follows it. Left click detonates. (No pickup/grab.)";
            case "secondary" ->
                    "Blast: Hold to charge (up to 8s). HUD circle shows %. LMB to fire a piercing beam that primarily launches targets back.";
            case "third" ->
                    "Orbital: Place an expanding radiation symbol for 20s (cancels on damage). At max radius, call down the pillar.";
            default -> "";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/zero_suit.png");
    }

    private static final class St {
        int missileId = -1;
        int missileTicks = 0;

        float targetYaw = 0f;
        float targetPitch = 0f;
        float targetRoll = 0f;

        boolean charging;
        int     chargeTicks;
        int lastHudCharge = -1;
        boolean lastHudActive = false;

        // === Orbital charge state ===
        boolean orbitalCharging = false;
        int orbitalTicks = 0;
        Vec3d orbitalHit = null;

        // damage detection without events (tracks health+absorption)
        float lastEffectiveHealth = -1f;
    }

    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof ZeroSuitPower;
    }

    private static ZeroSuitMissileEntity findMissile(ServerWorld w, int id) {
        if (id == -1) return null;
        Entity e = w.getEntityById(id);
        return (e instanceof ZeroSuitMissileEntity zm && zm.isAlive()) ? zm : null;
    }

    private static void sendMissileBeginCompat(ServerPlayerEntity to, ZeroSuitMissileEntity missile) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(missile.getId());
        out.writeFloat(missile.getYaw());
        out.writeFloat(missile.getPitch());
        ServerPlayNetworking.send(to, ZeroSuitNet.S2C_MISSILE_BEGIN, out);
    }

    private static void spawnMissile(ServerPlayerEntity p, St st) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector().normalize();
        Vec3d spawnPos = eye.add(look.multiply(1.8));

        ZeroSuitMissileEntity missile = ModEntities.ZERO_SUIT_MISSILE.create(sw);
        if (missile == null) return;

        missile.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, p.getYaw(), p.getPitch());
        missile.setOwner(p);
        missile.initFromOwner(p);

        sw.spawnEntity(missile);

        st.missileId = missile.getId();
        st.missileTicks = 0;

        st.targetYaw = p.getYaw();
        st.targetPitch = MathHelper.clamp(p.getPitch(), -80f, 80f);
        st.targetRoll = 0f;

        missile.serverSetRotation(st.targetYaw, st.targetPitch, st.targetRoll);
        ZeroSuitNet.broadcastMissileSteer(missile, st.targetYaw, st.targetPitch, st.targetRoll);

        sendMissileBeginCompat(p, missile);

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    private static void detonateMissile(ServerPlayerEntity p, St st) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        ZeroSuitMissileEntity missile = findMissile(sw, st.missileId);
        st.missileId = -1;
        st.missileTicks = 0;

        if (missile != null) missile.detonate();
        ZeroSuitNet.sendMissileEnd(p);
    }

    public static void onClientRequestedMissileDetonate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);
        if (st.missileId == -1) return;
        detonateMissile(p, st);
    }

    public static void onClientRequestedMissileSteer(ServerPlayerEntity p, int entityId, float yaw, float pitch, float roll) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S(p);
        ZeroSuitMissileEntity missile = findMissile(sw, st.missileId);
        if (missile == null) {
            st.missileId = -1;
            st.missileTicks = 0;
            ZeroSuitNet.sendMissileEnd(p);
            return;
        }

        if (missile.getId() != entityId) return;
        if (missile.getOwner() != p) return;

        st.targetYaw = yaw;
        st.targetPitch = MathHelper.clamp(pitch, -80f, 80f);
        st.targetRoll = MathHelper.clamp(roll, -55f, 55f);

        missile.serverSetRotation(st.targetYaw, st.targetPitch, st.targetRoll);
        ZeroSuitNet.broadcastMissileSteer(missile, st.targetYaw, st.targetPitch, st.targetRoll);
    }

    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        if (st.missileId != -1) {
            detonateMissile(p, st);
            return;
        }

        spawnMissile(p, st);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        if (st.missileId != -1) return;

        if (!st.charging) {
            st.charging = true;
            st.chargeTicks = 0;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 30, 2, true, false, false));
            ZeroSuitCPM.playBlastCharge(p);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.55f, 0.75f);
        } else {
            stopCharge(p, st, false);
        }
    }

    @Override
    public void activateThird(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S(p);

        // If already charging, pressing again cancels
        if (st.orbitalCharging) {
            cancelOrbital(sw, st, st.orbitalHit);
            p.sendMessage(Text.literal("ORBITAL CANCELLED"), true);
            return;
        }

        // Raycast a block (server authoritative)
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector().normalize();
        Vec3d end = eye.add(dir.multiply(ORBITAL_RAYCAST_RANGE));

        BlockHitResult bhr = sw.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                p
        ));

        if (bhr.getType() == HitResult.Type.MISS) {
            p.sendMessage(Text.literal("No block in sight."), true);
            return;
        }

        Vec3d hit = Vec3d.ofCenter(bhr.getBlockPos()).add(0, 0.01, 0);

        // Start 20s radiation expansion
        st.orbitalCharging = true;
        st.orbitalTicks = 0;
        st.orbitalHit = hit;

        // Broadcast "radiation begin"
        broadcastNear(sw, hit, ORBITAL_BROADCAST_RANGE, S2C_ZERO_RADIATION_BEGIN, out -> {
            out.writeDouble(hit.x);
            out.writeDouble(hit.y);
            out.writeDouble(hit.z);
            out.writeVarInt(ORBITAL_CHARGE_TICKS);
            out.writeFloat(ORBITAL_MAX_RADIUS);
        });

        sw.playSound(null, hit.x, hit.y, hit.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.9f, 0.8f);
        p.sendMessage(Text.literal("ORBITAL CHARGING..."), true);
    }

    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        St st = S(p);

        if (st.missileId != -1) {
            ZeroSuitMissileEntity missile = findMissile(sw, st.missileId);
            if (missile == null) {
                st.missileId = -1;
                st.missileTicks = 0;
                ZeroSuitNet.sendMissileEnd(p);
            } else {
                st.missileTicks++;
                if (st.missileTicks >= MISSILE_MAX_TICKS) {
                    detonateMissile(p, st);
                } else {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 10, true, false, false));
                    p.setVelocity(Vec3d.ZERO);
                    p.velocityModified = true;
                    p.fallDistance = 0f;

                    missile.serverSetRotation(st.targetYaw, st.targetPitch, st.targetRoll);
                }
            }
        }

        if (st.charging) {
            if (st.chargeTicks < BLAST_CHARGE_MAX_T) st.chargeTicks++;

            if ((p.age % 15) == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false, false));
            }

            if ((p.age % 10) == 0) {
                float r = MathHelper.clamp(st.chargeTicks / (float) BLAST_CHARGE_MAX_T, 0f, 1f);
                float vol = 0.35f + 0.45f * r;
                float pit = MathHelper.lerp(r, 1.20f, 0.95f);
                sw.playSound(p, p.getX(), p.getY(), p.getZ(), ModSounds.ZERO_CHARGE, SoundCategory.PLAYERS, vol, pit);
            }

            if ((p.age % HUD_SYNC_EVERY) == 0) {
                if (st.lastHudCharge != st.chargeTicks || !st.lastHudActive) {
                    st.lastHudCharge = st.chargeTicks;
                    st.lastHudActive = true;
                    ZeroSuitNet.sendHud(p, true, st.chargeTicks, BLAST_CHARGE_MAX_T);
                }
            }
        } else if ((p.age % HUD_SYNC_EVERY) == 0 && st.lastHudActive) {
            st.lastHudActive = false;
            ZeroSuitNet.sendHud(p, false, 0, BLAST_CHARGE_MAX_T);
        }

        // === Orbital charge timeline + cancel-on-damage ===
        float effHealth = p.getHealth() + p.getAbsorptionAmount();
        if (st.lastEffectiveHealth < 0f) st.lastEffectiveHealth = effHealth;

        if (st.orbitalCharging) {
            // cancel if hit
            if (effHealth + 1e-4f < st.lastEffectiveHealth) {
                cancelOrbital(sw, st, st.orbitalHit);
                p.sendMessage(Text.literal("ORBITAL INTERRUPTED"), true);
            } else {
                st.orbitalTicks++;

                // strong slow while charging
                if ((p.age % 2) == 0) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 4, true, false, true));
                }

                if (st.orbitalTicks >= ORBITAL_CHARGE_TICKS && st.orbitalHit != null) {
                    Vec3d hit = st.orbitalHit;

                    st.orbitalCharging = false;
                    st.orbitalTicks = 0;
                    st.orbitalHit = null;

                    // Start pillar visuals
                    broadcastNear(sw, hit, ORBITAL_BROADCAST_RANGE, S2C_ZERO_ORBITAL_STRIKE, out -> {
                        out.writeDouble(hit.x);
                        out.writeDouble(hit.y);
                        out.writeDouble(hit.z);
                        out.writeVarInt(ORBITAL_FX_TICKS);
                    });

                    // ===== NEW: Start pillar damage zone (server) =====
                    startPillarDamage(sw, hit, p);

                    sw.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2.5f, 0.75f);
                    sw.playSound(null, hit.x, hit.y, hit.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2f, 1.0f);
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
                }
            }
        }

        st.lastEffectiveHealth = effHealth;

        // ===== NEW: tick damage zones once per world tick =====
        tickPillarDamage(sw);
    }

    private static void cancelOrbital(ServerWorld sw, St st, Vec3d hit) {
        if (hit == null) {
            st.orbitalCharging = false;
            st.orbitalTicks = 0;
            st.orbitalHit = null;

            return;
        }

        st.orbitalCharging = false;
        st.orbitalTicks = 0;
        st.orbitalHit = null;

        broadcastNear(sw, hit, ORBITAL_BROADCAST_RANGE, S2C_ZERO_RADIATION_CANCEL, out -> {
            out.writeDouble(hit.x);
            out.writeDouble(hit.y);
            out.writeDouble(hit.z);
        });
    }

    private static void broadcastNear(ServerWorld sw, Vec3d pos, double range, Identifier id, java.util.function.Consumer<PacketByteBuf> writer) {
        double r2 = range * range;
        for (ServerPlayerEntity sp : sw.getPlayers()) {
            double dx = sp.getX() - pos.x;
            double dy = (sp.getY() + sp.getStandingEyeHeight()) - pos.y;
            double dz = sp.getZ() - pos.z;
            if ((dx*dx + dy*dy + dz*dz) <= r2) {
                if (ServerPlayNetworking.canSend(sp, id)) {
                    PacketByteBuf out = PacketByteBufs.create();
                    writer.accept(out);
                    ServerPlayNetworking.send(sp, id, out);
                }
            }
        }
    }

    private static void stopCharge(ServerPlayerEntity p, St st, boolean fired) {
        st.charging = false;
        st.chargeTicks = 0;
        ZeroSuitCPM.stopBlastCharge(p);
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        if (!fired) {
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.8f);
        }
        ZeroSuitNet.sendHud(p, false, 0, BLAST_CHARGE_MAX_T);
    }

    private static float pitchForBlast(float ratio) {
        return MathHelper.lerp(MathHelper.clamp(ratio, 0f, 1f), 1.15f, 0.90f);
    }

    private static void fireBlast(ServerPlayerEntity src, St st) {
        ServerWorld sw = (ServerWorld) src.getWorld();

        float ratio  = MathHelper.clamp(st.chargeTicks / (float) BLAST_CHARGE_MAX_T, 0f, 1f);
        float growth = (float)Math.pow(ratio, 1.35f);

        float dmg = BLAST_MAX_DAMAGE * ratio;

        double radius     = MathHelper.lerp(growth, BLAST_RADIUS_MIN, BLAST_RADIUS_MAX);
        double hitRadius  = radius;
        double coreRadius = radius * BLAST_CORE_FRACTION;

        Vec3d eye  = src.getEyePos();
        Vec3d look = src.getRotationVector().normalize();
        Vec3d start = eye.add(look.multiply(0.35)).add(0, -0.35, 0);

        double max = BLAST_RANGE_BLOCKS;
        BlockHitResult bhr = sw.raycast(new RaycastContext(
                start, start.add(look.multiply(max)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                src));

        boolean hitBlock = (bhr.getType() != BlockHitResult.Type.MISS);
        double hitDist = hitBlock ? start.distanceTo(Vec3d.ofCenter(bhr.getBlockPos())) : max;
        Vec3d impact = start.add(look.multiply(hitDist));

        Set<UUID> hitOnce = new HashSet<>();
        Box sweep = new Box(start, start.add(look.multiply(hitDist))).expand(hitRadius);
        List<LivingEntity> mobs = sw.getEntitiesByClass(LivingEntity.class, sweep, e -> e.isAlive() && e != src);

        for (LivingEntity e : mobs) {
            Vec3d c = e.getBoundingBox().getCenter();
            double t = MathHelper.clamp(c.subtract(start).dotProduct(look), 0.0, hitDist);
            Vec3d closest = start.add(look.multiply(t));

            double dist = c.distanceTo(closest);
            if (dist > hitRadius) continue;
            if (!hitOnce.add(e.getUuid())) continue;

            if (dmg > 0.001f) {
                e.damage(src.getDamageSources().playerAttack(src), dmg);
            }

            double falloff = MathHelper.clamp(1.0 - (dist / hitRadius), 0.0, 1.0);
            double kb = BLAST_KB_BASE + (BLAST_KB_MAX_ADD * ratio);
            kb *= (0.30 + 0.70 * falloff);
            if (dist <= coreRadius) kb *= BLAST_KB_CORE_MULT;

            double up = (BLAST_KB_UP_BASE + BLAST_KB_UP_MAX_ADD * ratio) * (0.30 + 0.70 * falloff);

            Vec3d kbVec = look.multiply(kb);
            kbVec = new Vec3d(kbVec.x, kbVec.y + up, kbVec.z);

            e.addVelocity(kbVec.x, kbVec.y, kbVec.z);
            e.velocityModified = true;

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, BEAM_NOFALL_TICKS, 0, true, false, true));
        }

        if (hitDist > 0.1) {
            ZeroBeamEntity beam = ModEntities.ZERO_BEAM.create(sw);
            if (beam != null) {
                beam.init(start, look, hitDist, radius, BLAST_VIS_TICKS);
                sw.spawnEntity(beam);
            }
        }

        if (ratio > 0.05f && hitBlock) {
            float strength = (float) MathHelper.lerp(ratio, (float)BLAST_RADIUS_MIN , (float)BLAST_RADIUS_MAX);
            Explosion.DestructionType mode =
                    (ratio >= 0.999f) ? Explosion.DestructionType.DESTROY : Explosion.DestructionType.KEEP;

            Explosion ex = new Explosion(
                    sw, src, null, null,
                    impact.x, impact.y, impact.z,
                    strength, false, mode
            );
            ex.collectBlocksAndDamageEntities();
            ex.affectWorld(true);
        }

        sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);

        Vec3d selfImpulse = look.multiply(-(SELF_RECOIL_BASE + SELF_RECOIL_MAX_ADD * ratio));
        src.addVelocity(selfImpulse.x, selfImpulse.y * 0.65, selfImpulse.z);
        src.velocityModified = true;

        sw.playSound(null, src.getX(), src.getY(), src.getZ(),
                ModSounds.ZERO_BLAST, SoundCategory.PLAYERS, 1.0f, pitchForBlast(ratio));

        ZeroSuitCPM.playBlastFire(src);
        stopCharge(src, st, true);
    }

    public static void onClientRequestedFire(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);
        if (st.charging) fireBlast(p, st);
    }

    /* ======================= CLIENT HUD (unchanged) ======================= */
    @Environment(EnvType.CLIENT)
    public static final class ClientHud {
        private ClientHud() {}

        public static boolean consumeAttackEdge() {
            boolean now = MinecraftClient.getInstance().options.attackKey.isPressed();
            boolean edge = now && !lastAttackDown;
            lastAttackDown = now;
            return edge;
        }

        private static boolean show;
        private static int charge;
        private static int max;

        private static final Identifier HUD_TEX_BASE  =
                new Identifier(Oddities.MOD_ID, "textures/gui/hud/zero_ring_base.png");
        private static final Identifier HUD_TEX_FILL  =
                new Identifier(Oddities.MOD_ID, "textures/gui/hud/zero_ring_fill.png");
        private static final int TEX_W = 64, TEX_H = 64;
        private static final int HUD_SIZE = 64;

        private static ChargeLoopSound loop;
        private static boolean lastAttackDown = false;

        public static void onHud(boolean s, int c, int m) {
            show = s; charge = c; max = m;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.getSoundManager() == null) return;

            if (show && (loop == null || loop.isDone())) {
                loop = new ChargeLoopSound(mc.player);
                mc.getSoundManager().play(loop);
            } else if (!show && loop != null) {
                loop.stopNow();
            }
        }

        public static void stopLoopNow() { if (loop != null) loop.stopNow(); }
        public static boolean isCharging() { return show; }

        private static final class ScreenShake {
            private static float strength;
            private static int   ticks;
            private static long  seed = 1337L;

            static void kick(float s, int dur) {
                strength = Math.max(strength, s);
                ticks = Math.max(ticks, dur);
                seed ^= (System.nanoTime() + dur);
            }

            static boolean active() { return ticks > 0 && strength > 0f; }

            private static float noise(long n) {
                n = (n << 13) ^ n;
                return (1.0f - ((n * (n * n * 15731L + 789221L) + 1376312589L) & 0x7fffffff) / 1073741824.0f);
            }

            static void applyTransform(WorldRenderContext ctx) {
                if (!active()) return;
                float td = ctx.tickDelta();
                var mc = MinecraftClient.getInstance();
                long tBase = (mc != null && mc.player != null) ? mc.player.age : 0L;
                long t = tBase + (long) td;
                float n1 = noise(seed + t * 3L) * 0.5f;
                float n2 = noise(seed ^ (t * 5L)) * 0.5f;
                float s = strength * 0.015f;
                ctx.matrixStack().translate(n1 * s, n2 * s, 0.0f);
                ticks--;
                strength *= 0.92f;
                if (ticks <= 0 || strength < 0.02f) { ticks = 0; strength = 0f; }
            }
        }

        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                if (!show || max <= 0) return;
                MinecraftClient mc = MinecraftClient.getInstance();
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                float pct = MathHelper.clamp(charge / (float)max, 0f, 1f);

                int size = HUD_SIZE;
                int x = sw / 2 - size / 2;
                int y = sh / 2 - size / 2;

                ctx.drawTexture(HUD_TEX_BASE, x, y, 0, 0, size, size, TEX_W, TEX_H);
                drawRadialFill(ctx, x + size / 2, y + size / 2, size / 2, pct);

                String label = (int)(pct * 100) + "%";
                int w = mc.textRenderer.getWidth(label);
                ctx.drawText(mc.textRenderer, label, sw/2 - w/2, sh/2 - 4, 0xFFFFFFFF, true);
            });
            WorldRenderEvents.START.register(ScreenShake::applyTransform);
        }

        private static void drawRadialFill(DrawContext ctx, int cx, int cy, int radius, float pct) {
            if (pct <= 0f) return;
            pct = MathHelper.clamp(pct, 0f, 1f);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, HUD_TEX_FILL);

            float start = -90f * (float)(Math.PI/180.0);
            float end   = start + pct * (float)(Math.PI * 2.0);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder bb = tess.getBuffer();
            bb.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE);

            var mat = ctx.getMatrices().peek().getPositionMatrix();

            bb.vertex(mat, (float)cx, (float)cy, 0f).texture(0.5f, 0.5f).next();

            int segs = Math.max(4, (int)Math.ceil(90 * pct));
            for (int i = 0; i <= segs; i++) {
                float a = MathHelper.lerp(i / (float)segs, start, end);
                float px = cx + (float)Math.cos(a) * radius;
                float py = cy + (float)Math.sin(a) * radius;

                float ux = 0.5f + 0.5f * (float)Math.cos(a);
                float uy = 0.5f + 0.5f * (float)Math.sin(a);

                bb.vertex(mat, px, py, 0f).texture(ux, uy).next();
            }
            tess.draw();

            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }

        private static final class ChargeLoopSound extends MovingSoundInstance {
            private final net.minecraft.entity.player.PlayerEntity player;
            private boolean stopped = false;

            ChargeLoopSound(net.minecraft.entity.player.PlayerEntity player) {
                super(ModSounds.ZERO_CHARGE, SoundCategory.PLAYERS, net.minecraft.util.math.random.Random.create());
                this.player = player;
                this.repeat = true; this.repeatDelay = 0;
                this.volume = 0.15f; this.pitch  = 0.95f;
                this.x = player.getX(); this.y = player.getY(); this.z = player.getZ();
            }

            @Override public void tick() {
                if (player == null || player.isRemoved() || !ClientHud.isCharging()) { stopped = true; return; }
                this.x = player.getX(); this.y = player.getY(); this.z = player.getZ();
                float pct = (max > 0) ? MathHelper.clamp(charge / (float)max, 0f, 1f) : 0f;
                this.volume = 0.15f + 0.85f * pct;
                this.pitch  = 0.95f + 0.35f * pct;
            }

            @Override public boolean isDone() { return stopped; }
            void stopNow() { stopped = true; }
        }
    }
}
