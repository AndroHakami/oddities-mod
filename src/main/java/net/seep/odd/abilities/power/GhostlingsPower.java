package net.seep.odd.abilities.power;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.ghostlings.GhostPackets; // must be server-safe!
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.entity.ModEntities;

public final class GhostlingsPower implements Power {
    public static final String MODID = "odd";

    /** Call this from Oddities.onInitialize() (server/common) */
    public static void registerCommonHooks() {
        // Only server-safe things here!
        GhostPackets.registerC2S(); // MUST NOT reference any client classes internally

        // Build ritual: pumpkin atop two white wool -> spawn Ghostling
        UseBlockCallback.EVENT.register((PlayerEntity player, net.minecraft.world.World world, Hand hand, BlockHitResult hit) -> {
            ItemStack held = player.getStackInHand(hand);
            if (!held.isOf(Items.CARVED_PUMPKIN) || player.isSneaking()) return ActionResult.PASS;

            BlockPos placeAt = hit.getBlockPos().offset(hit.getSide());
            BlockPos woolTop = placeAt.down();
            BlockPos woolBottom = placeAt.down(2);

            // canReplace needs a placement context (1.20.1)
            BlockHitResult adjustedHit = new BlockHitResult(Vec3d.ofCenter(placeAt), hit.getSide(), placeAt, false);
            ItemPlacementContext ctx = new ItemPlacementContext(new ItemUsageContext(player, hand, adjustedHit));

            boolean valid = world.getBlockState(woolTop).isOf(Blocks.WHITE_WOOL)
                    && world.getBlockState(woolBottom).isOf(Blocks.WHITE_WOOL)
                    && world.getBlockState(placeAt).canReplace(ctx);

            if (!valid) return ActionResult.PASS;

            if (!world.isClient) {
                world.breakBlock(woolTop, false);
                world.breakBlock(woolBottom, false);
                if (!player.getAbilities().creativeMode) held.decrement(1);

                GhostlingEntity g = ModEntities.GHOSTLING.create(world);
                if (g != null) {
                    g.refreshPositionAndAngles(placeAt.getX() + 0.5, placeAt.getY(), placeAt.getZ() + 0.5, 0, 0);
                    g.setOwner(player.getUuid());
                    g.setCustomName(Text.of("Ghostling"));
                    world.spawnEntity(g);
                    world.addParticle(ParticleTypes.SOUL, placeAt.getX()+0.5, placeAt.getY()+0.8, placeAt.getZ()+0.5, 0, 0, 0);
                }
            }
            return ActionResult.success(world.isClient);
        });
    }

    @Override public String id() { return "ghostlings"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 8; }
    @Override public long secondaryCooldownTicks() { return 40; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(MODID, "textures/gui/abilities/ghost_manage.png");
            case "secondary" -> new Identifier(MODID, "textures/gui/abilities/ghost_dashboard.png");
            default          -> new Identifier(MODID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Aim at a Ghostling to open its Manage panel.";
            case "secondary" -> "Open the dashboard of all your Ghostlings.";
            case "overview"  -> "Summon spectral workers and command them via GUIs.";
            default          -> "Ghostlings power.";
        };
    }

    @Override
    public String longDescription() {
        return """
            Summon Ghostlings by placing a Carved Pumpkin atop two White Wool blocks.
            Assign jobs with tools; manage an individual (Primary) or view all (Secondary).
        """;
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        GhostlingEntity g = raycastGhost(player, 7.0);
        if (g == null) {
            player.sendMessage(Text.literal("Look at your Ghostling to manage it (or use Secondary for Dashboard)."), true);
            return;
        }
        if (!g.isOwner(player.getUuid())) {
            player.sendMessage(Text.literal("That's not your Ghostling."), true);
            return;
        }
        GhostPackets.openManageServer(player, g); // server -> client packet
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        GhostPackets.openDashboardServer(player); // server -> client packet
    }

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
