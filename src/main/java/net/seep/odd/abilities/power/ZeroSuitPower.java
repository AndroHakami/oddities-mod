package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.zerosuit.ZeroSuitCPM;
import net.seep.odd.abilities.zerosuit.ZeroSuitNet;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.zerosuit.ZeroBeamEntity;

import java.util.*;

/** Zero Suit: Zero-G toggle, Gravity Force cone (push/pull), chargeable Blast. */
public final class ZeroSuitPower implements Power {

    /* ======================= config ======================= */
    // Primary
    private static final int ZERO_G_HIT_DEGRAV_TICKS = 20 * 6; // 6s

    // Force cone
    private static final int FORCE_AWAIT_MAX_TICKS = 20 * 4;  // wait up to 4s for LMB/RMB
    private static final double FORCE_RANGE = 5.0;            // meters
    private static final double FORCE_CONE_COS = Math.cos(Math.toRadians(25)); // tight cone ~25°
    private static final double FORCE_STRENGTH = 1.55;        // initial velocity burst
    private static final int FORCE_COOLDOWN_T = 20 * 3;       // 3s

    // Blast
    private static final int   BLAST_CHARGE_MAX_T = 20 * 10;  // 10s to full power
    private static final float BLAST_FULL_DAMAGE  = 20.0f;    // 10 hearts
    private static final int   BLAST_RANGE_BLOCKS = 48;       // stops on blocks
    private static final double BLAST_CORE_RADIUS = 0.35;     // direct-hit core (knockback)
    private static final double BLAST_WIDTH       = 1.00;     // requested 1 block wide
    private static final int   BLAST_VIS_TICKS    = 10;       // beam visual lifetime

    private static final int HUD_SYNC_EVERY = 2;

    /* ======================= power meta ======================= */
    @Override public String id() { return "zero_suit"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot) || "third".equals(slot); }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return FORCE_COOLDOWN_T; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_primary.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_force.png");
            case "third"     -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_blast.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }
    @Override public String longDescription() {
        return "Zero-G mobility with air-swimming, cone push/pull force, and a chargeable piercing beam.";
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Toggle Zero-G: float & swim in air. Enemies you hit lose gravity for 6s.";
            case "secondary" -> "Gravity Force (3s cd): hold stance up to 4s; LMB Push / RMB Pull in a tight 5m cone.";
            case "third"     -> "Blast: Hold to charge (up to 10s). HUD circle shows %. LMB to fire a piercing beam.";
            default          -> "";
        };
    }
    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/zero_suit.png"); }

    /* ======================= per-player state ======================= */
    private static final class St {
        // Primary
        boolean zeroG;

        // Who we’ve de-gravitied and until when
        final Map<UUID, Integer> noGravUntil = new HashMap<>();

        // Secondary (stance wait for LMB/RMB)
        boolean forceAwait;
        int     forceAwaitAge;

        // Third (charging)
        boolean charging;
        int     chargeTicks; // 0..BLAST_CHARGE_MAX_T

        // Client HUD dedupe
        int lastHudCharge = -1;
        boolean lastHudActive = false;
    }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof ZeroSuitPower;
    }

    /* ======================= inputs ======================= */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);
        st.zeroG = !st.zeroG;

        if (st.zeroG) enterZeroG(p);
        else          exitZeroG(p);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        st.forceAwait = true;
        st.forceAwaitAge = p.age;

        ZeroSuitCPM.playStance(p); // "force_stance"
        p.sendMessage(Text.literal("Gravity Force: LMB Push • RMB Pull (4s)"), true);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public void activateThird(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        // Toggle charge (hold-to-fire with LMB; client sends C2S when you click)
        if (!st.charging) {
            st.charging = true;
            st.chargeTicks = 0;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 30, 0, true, false, false));
            ZeroSuitCPM.playBlastCharge(p);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.55f, 0.75f);
        } else {
            stopCharge(p, st, false);
        }
    }

    /* ======================= server tick (called from ZeroSuitNet) ======================= */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        World w = p.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        St st = S(p);

        // Maintain Zero-G
        if (st.zeroG) {
            p.setNoGravity(true);
            p.fallDistance = 0f;
            if (!p.isSubmergedInWater()) {
                p.setSwimming(true);
            }
        } else {
            p.setNoGravity(false);
            if (p.isSwimming()) p.setSwimming(false);
        }

        // Expire de-gravity on victims
        if (!st.noGravUntil.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = st.noGravUntil.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> e = it.next();
                UUID id = e.getKey();
                int until = e.getValue();
                if (p.age >= until) {
                    Entity ent = sw.getEntity(id);
                    if (ent != null) ent.setNoGravity(false);
                    it.remove();
                }
            }
        }

        // Secondary awaiting decision
        if (st.forceAwait && p.age - st.forceAwaitAge > FORCE_AWAIT_MAX_TICKS) {
            st.forceAwait = false;
            ZeroSuitCPM.stopStance(p);
            p.sendMessage(Text.literal("Gravity Force: Cancelled"), true);
        }

        // Charging
        if (st.charging) {
            if (st.chargeTicks < BLAST_CHARGE_MAX_T) st.chargeTicks++;

            // Refresh slowness while charging
            if ((p.age % 15) == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false, false));
            }

            // HUD sync
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
    }

    /* ======================= internals ======================= */

    private static void enterZeroG(ServerPlayerEntity p) {
        p.setNoGravity(true);
        p.setSwimming(true);
        p.sendMessage(Text.literal("Zero-G: ON"), true);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    private static void exitZeroG(ServerPlayerEntity p) {
        p.setNoGravity(false);
        if (p.isSwimming()) p.setSwimming(false);
        p.sendMessage(Text.literal("Zero-G: OFF"), true);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.6f, 0.8f);
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

    /** Apply push or pull to entities in a tight cone in front of the player. */
    private static void doForceCone(ServerPlayerEntity src, boolean push) {
        ServerWorld sw = (ServerWorld) src.getWorld();
        Vec3d eye = src.getEyePos();
        Vec3d look = src.getRotationVector().normalize();

        double range = FORCE_RANGE;
        Box box = new Box(eye, eye).expand(range, range, range);

        List<Entity> list = sw.getOtherEntities(src, box, e -> e.isAlive() && e instanceof LivingEntity);
        for (Entity e : list) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.01) continue;

            Vec3d dir = to.normalize();
            double dot = dir.dotProduct(look);
            if (dot < FORCE_CONE_COS) continue; // outside the cone

            Vec3d impulse;
            if (push) impulse = look.multiply(FORCE_STRENGTH);
            else      impulse = eye.subtract(e.getBoundingBox().getCenter()).normalize().multiply(FORCE_STRENGTH);

            e.addVelocity(impulse.x, impulse.y, impulse.z);
            e.velocityModified = true;
        }

        src.getWorld().playSound(null, src.getBlockPos(),
                push ? SoundEvents.ENTITY_BLAZE_SHOOT : SoundEvents.ENTITY_ENDERMAN_SCREAM,
                SoundCategory.PLAYERS, 0.8f, push ? 1.2f : 0.6f);

        if (push) ZeroSuitCPM.playForcePush(src); else ZeroSuitCPM.playForcePull(src);
    }

    /** Fire a piercing beam based on current charge; damages along ray, stops on blocks. */
    private static void fireBlast(ServerPlayerEntity src, St st) {
        ServerWorld sw = (ServerWorld) src.getWorld();

        float ratio = MathHelper.clamp(st.chargeTicks / (float) BLAST_CHARGE_MAX_T, 0f, 1f);
        float dmg = BLAST_FULL_DAMAGE * ratio;

        Vec3d eye = src.getEyePos();
        Vec3d look = src.getRotationVector().normalize();

        // Block ray
        double max = BLAST_RANGE_BLOCKS;
        BlockHitResult bhr = sw.raycast(new RaycastContext(eye, eye.add(look.multiply(max)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, src));
        double hitDist = bhr.getType() == BlockHitResult.Type.MISS ? max : eye.distanceTo(Vec3d.ofCenter(bhr.getBlockPos()));

        // Pierce mobs along line, stop at block
        Set<UUID> hitOnce = new HashSet<>();
        Box sweep = new Box(eye, eye.add(look.multiply(hitDist))).expand(BLAST_WIDTH * 0.5);
        List<LivingEntity> mobs = sw.getEntitiesByClass(LivingEntity.class, sweep, e -> e.isAlive() && e != src);
        for (LivingEntity e : mobs) {
            Vec3d c = e.getBoundingBox().getCenter();
            double t = MathHelper.clamp(eye.relativize(c).dotProduct(look), 0.0, hitDist);
            Vec3d closest = eye.add(look.multiply(t));
            double dist = c.distanceTo(closest);
            if (dist <= BLAST_WIDTH * 0.5) {
                if (hitOnce.add(e.getUuid())) {
                    e.damage(src.getDamageSources().playerAttack(src), dmg);
                    if (dist <= BLAST_CORE_RADIUS) {
                        Vec3d kb = look.multiply(1.0 + 1.25 * ratio);
                        e.addVelocity(kb.x, kb.y * 0.25, kb.z);
                        e.velocityModified = true;
                    }
                }
            }
        }

        // Visual beam entity
        ZeroBeamEntity beam = ModEntities.ZERO_BEAM.create(sw);
        if (beam != null) {
            beam.init(src, eye, look, hitDist, BLAST_WIDTH, BLAST_VIS_TICKS);
            sw.spawnEntity(beam);
        }

        src.getWorld().playSound(null, src.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 1.0f);
        ZeroSuitCPM.playBlastFire(src);

        stopCharge(src, st, true);
    }

    /** Called by the server when it receives the C2S FIRE packet. */
    public static void onClientRequestedFire(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);
        if (st.charging) fireBlast(p, st);
    }

    /* ======================= interaction & inputs (server-side helpers) ======================= */
    static {
        // LMB while in Force stance: PUSH. (Beam firing is now done by C2S packet from client.)
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !isCurrent(sp)) return ActionResult.PASS;
            St st = S(sp);
            if (st.forceAwait) {
                st.forceAwait = false;
                doForceCone(sp, true);
                return ActionResult.FAIL;
            }
            // Normal attack: if Zero-G active, remove gravity from the thing you hit
            if (st.zeroG && target != null) {
                target.setNoGravity(true);
                st.noGravUntil.put(target.getUuid(), sp.age + ZERO_G_HIT_DEGRAV_TICKS);
            }
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !isCurrent(sp)) return ActionResult.PASS;
            St st = S(sp);
            if (st.forceAwait) {
                st.forceAwait = false;
                doForceCone(sp, true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // RMB while in Force stance: PULL
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !isCurrent(sp)) return ActionResult.PASS;
            St st = S(sp);
            if (st.forceAwait) {
                st.forceAwait = false;
                doForceCone(sp, false);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !isCurrent(sp)) return ActionResult.PASS;
            St st = S(sp);
            if (st.forceAwait) {
                st.forceAwait = false;
                doForceCone(sp, false);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    /* ======================= CLIENT HUD ======================= */
    @Environment(EnvType.CLIENT)
    public static final class ClientHud {
        private ClientHud() {}

        private static boolean show;
        private static int charge;
        private static int max;

        public static void onHud(boolean s, int c, int m) { show = s; charge = c; max = m; }
        public static boolean isCharging() { return show; }

        // edge detector for the C2S fire (consumed by ZeroSuitNet client tick)
        private static boolean lastAttackDown = false;
        public static boolean consumeAttackEdge() {
            boolean now = MinecraftClient.getInstance().options.attackKey.isPressed();
            boolean edge = now && !lastAttackDown;
            lastAttackDown = now;
            return edge;
        }

        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                if (!show || max <= 0) return;
                MinecraftClient mc = MinecraftClient.getInstance();
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                float pct = MathHelper.clamp(charge / (float)max, 0f, 1f);
                drawRing(ctx, sw/2, sh/2, 16, 24, pct, 64, 0xAAFFFFFF, 0x33FFFFFF);
                String label = (int)(pct * 100) + "%";
                int w = mc.textRenderer.getWidth(label);
                ctx.drawText(mc.textRenderer, label, sw/2 - w/2, sh/2 - 4, 0xFFFFFFFF, true);
            });
        }

        private static void drawRing(DrawContext ctx, int cx, int cy, int rInner, int rOuter, float pct, int steps, int colorFill, int colorBg) {
            drawArc(ctx, cx, cy, rInner, rOuter, 0f, 1f, steps, colorBg);
            drawArc(ctx, cx, cy, rInner, rOuter, 0f, pct, steps, colorFill);
        }

        private static void drawArc(DrawContext ctx, int cx, int cy, int rIn, int rOut, float from, float to, int steps, int color) {
            to = Math.max(to, from);
            int segs = Math.max(1, Math.round(steps * (to - from)));
            for (int i = 0; i < segs; i++) {
                float a0 = (from + (i    /(float)segs)) * (float)(Math.PI * 2);
                float a1 = (from + ((i+1)/(float)segs)) * (float)(Math.PI * 2);
                int x0o = cx + (int)(Math.cos(a0) * rOut), y0o = cy + (int)(Math.sin(a0) * rOut);
                int x1o = cx + (int)(Math.cos(a1) * rOut), y1o = cy + (int)(Math.sin(a1) * rOut);
                int x0i = cx + (int)(Math.cos(a0) * rIn ), y0i = cy + (int)(Math.sin(a0) * rIn );
                int x1i = cx + (int)(Math.cos(a1) * rIn ), y1i = cy + (int)(Math.sin(a1) * rIn );
                ctx.fill(Math.min(x0i,x1o), Math.min(y0i,y1o), Math.max(x0i,x1o), Math.max(y0i,y1o), color);
                ctx.fill(Math.min(x1i,x1o), Math.min(y1i,y1o), Math.max(x1i,x1o), Math.max(y1i,y1o), color);
            }
        }
    }
}
