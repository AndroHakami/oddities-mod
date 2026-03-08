// FILE: src/main/java/net/seep/odd/abilities/power/GhostlingsPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.ghostlings.GhostPackets; // must be server-safe!
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.status.ModStatusEffects;

public final class GhostlingsPower implements Power {
    public static final String MODID = "odd";

    // Only Ghostlings power can summon
    private static final String REQUIRED_POWER_ID = "ghostlings";

    /** Call this from Oddities.onInitialize() (server/common) */
    public static void registerCommonHooks() {
        // Only server-safe things here!
        GhostPackets.registerC2S(); // MUST NOT reference any client classes internally
        // ✅ Removed "build -> auto summon" hook entirely.
        // Building pumpkin + wool is now normal for everyone, no checks, no messages.
    }

    @Override public String id() { return "ghostlings"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 8; }
    @Override public long secondaryCooldownTicks() { return 8; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(MODID, "textures/gui/abilities/ghost_manage.png");
            case "secondary" -> new Identifier(MODID, "textures/gui/abilities/ghost_dashboard.png");
            default          -> new Identifier(MODID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "GHOSTLY COMMAND";
            case "secondary" -> "WORK STATUS";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Aim at a Ghostling to open its Manage panel, or aim at a ritual to summon.";
            case "secondary" -> "Open the dashboard of all your Ghostlings.";
            case "overview"  -> "Summon spectral workers and command them via GUIs.";
            default          -> "Ghostlings power.";
        };
    }

    @Override
    public String longDescription() {
        return """
            Build the ritual (Carved Pumpkin atop two White Wool).
            Then, while using Ghostlings power, press Primary while aiming at it to summon.
            Primary also manages a Ghostling you are aiming at; Secondary opens the dashboard.
        """;
    }

    /* =================== POWERLESS override =================== */

    private static final Object2LongOpenHashMap<java.util.UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

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

    private static boolean canSummon(ServerPlayerEntity sp) {
        if (sp == null) return false;
        if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) return false;
        String id = PowerAPI.get(sp);
        return REQUIRED_POWER_ID.equals(id);
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        // Nothing to cancel; just prevents ability use while POWERLESS.
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        if (isPowerless(player)) {
            warnOncePerSec(player, "§cYou are powerless.");
            return;
        }

        // 1) Normal primary behavior: manage the ghostling you are aiming at
        GhostlingEntity g = raycastGhost(player, 7.0);
        if (g != null) {
            if (!g.isOwner(player.getUuid())) {
                player.sendMessage(Text.literal("That's not your Ghostling."), true);
                return;
            }
            GhostPackets.openManageServer(player, g);
            return;
        }

        // 2) If not aiming at a ghostling: try summoning from a built ritual you're aiming at
        if (!canSummon(player)) {
            // Shouldn't happen because this power is only active when you have it,
            // but keeps the same gate pattern as your other stuff.
            warnOncePerSec(player, "§cOnly a Ghostlings user can summon.");
            return;
        }

        if (trySummonFromLook(player, 7.0)) {
            return;
        }

        // otherwise: do nothing (quiet)
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (isPowerless(player)) {
            warnOncePerSec(player, "§cYou are powerless.");
            return;
        }
        GhostPackets.openDashboardServer(player);
    }

    /* ------------------ Summoning (Primary) ------------------ */

    private static boolean trySummonFromLook(ServerPlayerEntity player, double range) {
        var world = player.getWorld();
        Vec3d eye  = player.getCameraPosVec(1f);
        Vec3d look = player.getRotationVec(1f);
        Vec3d end  = eye.add(look.multiply(range));

        HitResult hr = world.raycast(new net.minecraft.world.RaycastContext(
                eye, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hr.getType() != HitResult.Type.BLOCK) return false;
        BlockPos hitPos = ((net.minecraft.util.hit.BlockHitResult) hr).getBlockPos();
        BlockState hitState = world.getBlockState(hitPos);

        // We allow aiming at any part of the structure. Find the "top" pumpkin position.
        BlockPos pumpkinPos = null;

        if (hitState.isOf(Blocks.CARVED_PUMPKIN)) {
            pumpkinPos = hitPos;
        } else {
            // If you hit wool, check if there's a pumpkin above
            BlockPos up1 = hitPos.up();
            BlockPos up2 = hitPos.up(2);
            if (world.getBlockState(up1).isOf(Blocks.CARVED_PUMPKIN)) pumpkinPos = up1;
            else if (world.getBlockState(up2).isOf(Blocks.CARVED_PUMPKIN)) pumpkinPos = up2;
        }

        if (pumpkinPos == null) return false;

        BlockPos woolTop = pumpkinPos.down();
        BlockPos woolBottom = pumpkinPos.down(2);

        boolean valid =
                world.getBlockState(woolTop).isOf(Blocks.WHITE_WOOL)
                        && world.getBlockState(woolBottom).isOf(Blocks.WHITE_WOOL);

        if (!valid) return false;

        // Consume structure -> spawn ghostling
        world.breakBlock(pumpkinPos, false);
        world.breakBlock(woolTop, false);
        world.breakBlock(woolBottom, false);

        GhostlingEntity g = ModEntities.GHOSTLING.create(world);
        if (g != null) {
            g.refreshPositionAndAngles(pumpkinPos.getX() + 0.5, pumpkinPos.getY(), pumpkinPos.getZ() + 0.5, 0, 0);
            g.setOwner(player.getUuid());
            g.setCustomName(Text.of("Ghostling"));
            world.spawnEntity(g);


            player.playSound(SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8f, 1.2f);
        }

        return true;
    }

    /* ------------------ Raycast ghost ------------------ */

    private static GhostlingEntity raycastGhost(ServerPlayerEntity p, double range) {
        Vec3d eye  = p.getCameraPosVec(1f);
        Vec3d look = p.getRotationVec(1f);
        Vec3d end  = eye.add(look.multiply(range));
        Box sweep  = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0);

        var hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                p, eye, end, sweep,
                e -> e instanceof GhostlingEntity && e.isAlive() && !e.isSpectator(),
                range * range
        );
        var e = hit != null ? hit.getEntity() : null;
        return (e instanceof GhostlingEntity ge) ? ge : null;
    }
}