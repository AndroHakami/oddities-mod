package net.seep.odd.abilities.power;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.zerosuit.ZeroSuitCPM;
import net.seep.odd.abilities.zerosuit.ZeroSuitNet;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.zerosuit.ZeroBeamEntity;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.*;

/** Zero Suit: Zero-G toggle + chargeable piercing beam (Secondary). */
public final class ZeroSuitPower implements Power {

    /* ======================= config ======================= */
    private static final int ZERO_G_HIT_DEGRAV_TICKS = 20 * 6; // now driven by status effect

    private static final int   BLAST_CHARGE_MAX_T  = 20 * 8;
    private static final float BLAST_FULL_DAMAGE   = 25.0f;
    private static final int   BLAST_RANGE_BLOCKS  = 48;
    private static final int   BLAST_VIS_TICKS     = 12;   // how long the visual beam lingers

    private static final double BLAST_RADIUS_MIN    = 0.25;
    private static final double BLAST_RADIUS_MAX    = 1.40;
    private static final double BLAST_CORE_FRACTION = 0.55;

    private static final int HUD_SYNC_EVERY = 2;

    private static final float SHAKE_THRESHOLD = 0.60f;

    /* ======================= meta ======================= */
    @Override public String id() { return "zero_suit"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_gravity.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/zero_blast.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() { return "Zero-G mobility with air-swimming and a chargeable piercing beam."; }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Toggle Zero-G: float & swim in air. Enemies you hit lose gravity for 6s.";
            case "secondary" -> "Blast: Hold to charge (up to 8s). HUD circle shows %. LMB to fire a piercing beam.";
            default          -> "";
        };
    }
    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/zero_suit.png"); }

    /* ======================= per-player state ======================= */
    private static final class St {
        boolean zeroG;
        final Map<UUID, Integer> noGravUntil = new HashMap<>();
        boolean charging;
        int     chargeTicks;
        int lastHudCharge = -1;
        boolean lastHudActive = false;
    }
    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof ZeroSuitPower;
    }

    /* ======================= helpers: sound curves ======================= */
    private static float pitchForBlast(float ratio) {
        // 0% -> 1.15  … 100% -> 0.90 (stronger = lower, but not silly-low)
        return MathHelper.lerp(MathHelper.clamp(ratio, 0f, 1f), 1.15f, 0.90f);
    }
    private static float pitchForCharge(float ratio) {
        // 0% -> 1.20  … 100% -> 0.95
        return MathHelper.lerp(MathHelper.clamp(ratio, 0f, 1f), 1.20f, 0.95f);
    }
    private static float volumeForCharge(float ratio) {
        // a bit louder as it charges
        return 0.35f + 0.45f * MathHelper.clamp(ratio, 0f, 1f);
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
    @Override public void activateThird(ServerPlayerEntity p) { /* no-op */ }

    /* ======================= server tick ======================= */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        World w = p.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        St st = S(p);

        // Zero-G upkeep
        if (st.zeroG) {
            p.setNoGravity(true);
            p.fallDistance = 0f;
            if (!p.isSubmergedInWater()) p.setSwimming(true);
        } else {
            p.setNoGravity(false);
            if (p.isSwimming()) p.setSwimming(false);
        }

        // Charging upkeep + HUD sync
        if (st.charging) {
            if (st.chargeTicks < BLAST_CHARGE_MAX_T) st.chargeTicks++;

            if ((p.age % 15) == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false, false));
            }

            // === broadcast charge hum to nearby players (shooter hears client loop) ===
            if ((p.age % 10) == 0) { // ~2x/second
                float r = MathHelper.clamp(st.chargeTicks / (float) BLAST_CHARGE_MAX_T, 0f, 1f);
                float vol = volumeForCharge(r);
                float pit = pitchForCharge(r);
                sw.playSound(
                        p, // exclude shooter (they already have local loop)
                        p.getX(), p.getY(), p.getZ(),
                        ModSounds.ZERO_CHARGE, SoundCategory.PLAYERS,
                        vol, pit
                );
            }

            // === 3D ribbon-hurricane charge (kept) ===
            if ((p.age % 2) == 0) {
                float ratio = MathHelper.clamp(st.chargeTicks / (float) BLAST_CHARGE_MAX_T, 0f, 1f);
                Vec3d core = new Vec3d(p.getX(), p.getY() + 1.0, p.getZ());
                Vec3d look = p.getRotationVector().normalize();
                Vec3d worldUp = Math.abs(look.y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
                Vec3d right = look.crossProduct(worldUp).normalize();
                Vec3d up    = right.crossProduct(look).normalize();

                double Lfar0 = 1.40, Lnear = 0.28;
                double Lfar  = MathHelper.lerp(ratio, Lfar0, 0.70);
                double Rfar0 = 0.58,  Rnear = 0.06;
                double tighten = MathHelper.lerp(ratio, 1.0, 0.62);
                double base = (p.age * 0.30) + (ratio * 8.0);

                int spokes = 5;
                for (int i = 0; i < spokes; i++) {
                    double a01 = (i + (p.age & 1) * 0.33) / spokes;
                    double L   = MathHelper.lerp(a01, Lfar, Lnear);
                    double r   = MathHelper.lerp((L - Lnear) / Math.max(0.0001, (Lfar - Lnear)), Rfar0, Rnear) * tighten;
                    double ang = base + a01 * (Math.PI * 2.0);

                    Vec3d off = right.multiply(Math.cos(ang) * r).add(up.multiply(Math.sin(ang) * r));
                    Vec3d pos = core.add(look.multiply(L)).add(off);

                    double vx = (core.x - pos.x) * 0.18;
                    double vy = (core.y - pos.y) * 0.18;
                    double vz = (core.z - pos.z) * 0.18;

                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.WAX_ON,
                            pos.x, pos.y, pos.z,
                            1,
                            vx, vy, vz,
                            0.015);
                }
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
    }

    /* ======================= internals ======================= */

    private static void enterZeroG(ServerPlayerEntity p) {
        p.setNoGravity(true);
        p.setSwimming(true);
        p.sendMessage(net.minecraft.text.Text.literal("Zero-G: ON"), true);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }
    private static void exitZeroG(ServerPlayerEntity p) {
        p.setNoGravity(false);
        if (p.isSwimming()) p.setSwimming(false);
        p.sendMessage(net.minecraft.text.Text.literal("Zero-G: OFF"), true);
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

    /** Fire the beam; damages along ray; stops on blocks; spawns visual beam; visual-only pop at impact (real explosion only at 100%). */
    private static void fireBlast(ServerPlayerEntity src, St st) {
        ServerWorld sw = (ServerWorld) src.getWorld();

        float ratio  = MathHelper.clamp(st.chargeTicks / (float) BLAST_CHARGE_MAX_T, 0f, 1f);
        float growth = (float)Math.pow(ratio, 1.35);
        float dmg    = BLAST_FULL_DAMAGE * ratio;

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

        // Did we actually hit a BLOCK?
        boolean hitBlock = (bhr.getType() != BlockHitResult.Type.MISS);
        double hitDist = hitBlock ? start.distanceTo(Vec3d.ofCenter(bhr.getBlockPos())) : max;
        Vec3d impact = start.add(look.multiply(hitDist));

        // damage pass
        Set<UUID> hitOnce = new HashSet<>();
        Box sweep = new Box(start, start.add(look.multiply(hitDist))).expand(hitRadius);
        List<LivingEntity> mobs = sw.getEntitiesByClass(LivingEntity.class, sweep, e -> e.isAlive() && e != src);
        for (LivingEntity e : mobs) {
            Vec3d c = e.getBoundingBox().getCenter();
            double t = MathHelper.clamp(c.subtract(start).dotProduct(look), 0.0, hitDist);
            Vec3d closest = start.add(look.multiply(t));
            double dist   = c.distanceTo(closest);
            if (dist <= hitRadius && hitOnce.add(e.getUuid())) {
                e.damage(src.getDamageSources().playerAttack(src), dmg);
                if (dist <= coreRadius) {
                    Vec3d kb = look.multiply(0.8 + 1.4 * ratio);
                    e.addVelocity(kb.x, kb.y * 0.25, kb.z);
                    e.velocityModified = true;
                }
            }
        }

        // spawn the visual beam
        if (hitDist > 0.1) {
            ZeroBeamEntity beam = ModEntities.ZERO_BEAM.create(sw);
            if (beam != null) {
                beam.init(start, look, hitDist, radius, BLAST_VIS_TICKS);
                sw.spawnEntity(beam);
            }
        }

        // === REAL EXPLOSION ON BLOCK HIT (scaled; non-destructive unless 100%) ===
        if (ratio > 0.05f && hitBlock) {
            float strength = (float) MathHelper.lerp(ratio, BLAST_RADIUS_MIN , BLAST_RADIUS_MAX);
            Explosion.DestructionType mode =
                    (ratio >= 0.999f) ? Explosion.DestructionType.DESTROY
                            : Explosion.DestructionType.KEEP;

            Explosion ex = new Explosion(
                    sw,
                    src, null, null,
                    impact.x, impact.y, impact.z,
                    strength,
                    false,
                    mode
            );
            ex.collectBlocksAndDamageEntities();
            ex.affectWorld(true);
        }

        // impact readability
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);

        // recoil
        Vec3d selfImpulse = look.multiply(-(0.35 + 1.10 * ratio));
        src.addVelocity(selfImpulse.x, selfImpulse.y, selfImpulse.z);
        src.velocityModified = true;

        // BLAST SFX: charge-scaled pitch, audible to nearby players
        float pitchBlast = pitchForBlast(ratio);
        sw.playSound(
                null,
                src.getX(), src.getY(), src.getZ(),
                ModSounds.ZERO_BLAST, SoundCategory.PLAYERS,
                1.0f, pitchBlast
        );

        ZeroSuitCPM.playBlastFire(src);
        stopCharge(src, st, true);
    }

    public static void onClientRequestedFire(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);
        if (st.charging) fireBlast(p, st);
    }

    /* ======================= interaction hooks ======================= */
    static {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !isCurrent(sp)) return ActionResult.PASS;
            if (S(sp).zeroG && target instanceof LivingEntity le) {
                le.addStatusEffect(new StatusEffectInstance(ModStatusEffects.GRAVITY_SUSPEND, ZERO_G_HIT_DEGRAV_TICKS, 0, false, false, true), sp);
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !isCurrent(sp)) return ActionResult.PASS;
            return ActionResult.PASS;
        });
    }

    /* ======================= CLIENT HUD + SOUND + SCREEN SHAKE ======================= */
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

        // Textured HUD assets
        private static final Identifier HUD_TEX_BASE  =
                new Identifier(Oddities.MOD_ID, "textures/gui/hud/zero_ring_base.png");
        private static final Identifier HUD_TEX_FILL  =
                new Identifier(Oddities.MOD_ID, "textures/gui/hud/zero_ring_fill.png");
        private static final int TEX_W = 64, TEX_H = 64; // real texture size
        private static final int HUD_SIZE = 64;          // draw at 64×64 exactly

        private static ChargeLoopSound loop;

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

        private static boolean lastAttackDown = false;


        // ===== Screen Shake =====
        private static final class ScreenShake {
            private static float strength;
            private static int   ticks;
            private static long  seed = 1337L;

            static void kick(float s, int dur) { strength = Math.max(strength, s); ticks = Math.max(ticks, dur); seed ^= (System.nanoTime() + dur); }

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

        /** Local helper for renderers to request a shake when close to a given world pos. */
        public static void shakeIfClose(Vec3d worldPos, double radius, float strength, int durationTicks) {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            Vec3d cam = mc.gameRenderer.getCamera().getPos();
            if (cam.squaredDistanceTo(worldPos) <= (radius * radius)) {
                ScreenShake.kick(strength, durationTicks);
            }
        }

        public static void init() {
            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                if (!show || max <= 0) return;
                MinecraftClient mc = MinecraftClient.getInstance();
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                float pct = MathHelper.clamp(charge / (float)max, 0f, 1f);

                // center placement (exact 64×64)
                int size = HUD_SIZE;
                int x = sw / 2 - size / 2;
                int y = sh / 2 - size / 2;

                // 1) draw base ring (exact 64×64 pixels)
                ctx.drawTexture(HUD_TEX_BASE, x, y, 0, 0, size, size, TEX_W, TEX_H);

                // 2) draw radial fill slice from the fill texture
                drawRadialFill(ctx, x + size / 2, y + size / 2, size / 2, pct);

                // 3) numeric label (optional)
                String label = (int)(pct * 100) + "%";
                int w = mc.textRenderer.getWidth(label);
                ctx.drawText(mc.textRenderer, label, sw/2 - w/2, sh/2 - 4, 0xFFFFFFFF, true);
            });
            WorldRenderEvents.START.register(ScreenShake::applyTransform);
        }

        /** Draws a pie slice of HUD_TEX_FILL (0..pct of the circle). Assumes the ring is centered in a 64×64 texture. */
        private static void drawRadialFill(DrawContext ctx, int cx, int cy, int radius, float pct) {
            if (pct <= 0f) return;
            pct = MathHelper.clamp(pct, 0f, 1f);

            // GUI blend & depth state
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, HUD_TEX_FILL);

            // start at -90° (top) and fill clockwise
            float start = -90f * (float)(Math.PI/180.0);
            float end   = start + pct * (float)(Math.PI * 2.0);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder bb = tess.getBuffer();
            bb.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE);

            var mat = ctx.getMatrices().peek().getPositionMatrix();

            // center of the fan (UV center of texture)
            bb.vertex(mat, (float)cx, (float)cy, 0f).texture(0.5f, 0.5f).next();

            // arc points
            int segs = Math.max(4, (int)Math.ceil(90 * pct)); // smooth enough
            for (int i = 0; i <= segs; i++) {
                float a = MathHelper.lerp(i / (float)segs, start, end);
                float px = cx + (float)Math.cos(a) * radius;
                float py = cy + (float)Math.sin(a) * radius;

                // map circle to UVs (centered, 0.5 radius in texture space)
                float ux = 0.5f + 0.5f * (float)Math.cos(a);
                float uy = 0.5f + 0.5f * (float)Math.sin(a);

                bb.vertex(mat, px, py, 0f).texture(ux, uy).next();
            }
            tess.draw();

            // restore
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }

        // ======= client sound loop (unchanged) =======
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
