package net.seep.odd.worldgen.structure;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

public final class WitchlogStructureAssembler {
    private WitchlogStructureAssembler() {}

    public static final int BAND_HEIGHT = 16;
    public static final int INTERIOR_RADIUS = 30;

    public static int placeFirstPack(ServerWorld world, BlockPos centerPos) {
        int placed = 0;

        placed += placeShellQuarter4(world, id("witchlog_shell_base_q"), centerPos.add(0, 0, 0));
        placed += place(world, id("witchlog_interior_doubleledge_band"),
                centerPos.add(-INTERIOR_RADIUS, 0, -INTERIOR_RADIUS), BlockRotation.NONE);

        placed += placeShellQuarter4(world, id("witchlog_shell_lower_plain_q"), centerPos.add(0, BAND_HEIGHT, 0));
        placed += place(world, id("witchlog_interior_empty_band"),
                centerPos.add(-INTERIOR_RADIUS, BAND_HEIGHT, -INTERIOR_RADIUS), BlockRotation.NONE);

        BlockPos band2 = centerPos.add(0, BAND_HEIGHT * 2, 0);
        placed += place(world, id("witchlog_shell_lower_plain_q"), band2, BlockRotation.NONE);
        placed += place(world, id("witchlog_shell_lower_socket_q"), band2, BlockRotation.CLOCKWISE_90);
        placed += place(world, id("witchlog_shell_lower_plain_q"), band2, BlockRotation.CLOCKWISE_180);
        placed += place(world, id("witchlog_shell_lower_plain_q"), band2, BlockRotation.COUNTERCLOCKWISE_90);
        placed += place(world, id("witchlog_interior_roomhub_band"),
                centerPos.add(-INTERIOR_RADIUS, BAND_HEIGHT * 2, -INTERIOR_RADIUS), BlockRotation.NONE);

        return placed;
    }

    /**
     * arenaCenterTop:
     * - X/Z = center of the arena platform
     * - Y   = top walking surface of the arena platform
     */
    public static int placeArenaPack(ServerWorld world, BlockPos arenaCenterTop) {
        int placed = 0;

        placed += place(world, id("witchlog_arena_platform_band"),
                arenaCenterTop.add(-30, -9, -30), BlockRotation.NONE);

        // v2 moat band includes the full underfloor basin floor.
        placed += place(world, id("witchlog_arena_poison_moat_band"),
                arenaCenterTop.add(-50, -20, -50), BlockRotation.NONE);

        placed += placeShellQuarter4(world, id("witchlog_arena_shell_lower_q"),
                arenaCenterTop.add(0, -20, 0));

        placed += placeShellQuarter4(world, id("witchlog_arena_shell_upperroof_q"),
                arenaCenterTop.add(0, 1, 0));

        return placed;
    }

    /**
     * Transition band between arena roof opening and the first tower bands.
     * Uses the same arena-center-top origin as placeArenaPack.
     */
    public static int placeTransitionPack(ServerWorld world, BlockPos arenaCenterTop) {
        int placed = 0;

        placed += placeShellQuarter4(world, id("witchlog_transition_shell_q"),
                arenaCenterTop.add(0, 31, 0));

        placed += place(world, id("witchlog_transition_interior_band"),
                arenaCenterTop.add(-30, 31, -30), BlockRotation.NONE);

        return placed;
    }

    /**
     * Full lower stack:
     * - arena
     * - transition
     * - first tower pack
     *
     * Origin is still arena center top.
     */
    public static int placeArenaStack(ServerWorld world, BlockPos arenaCenterTop) {
        int placed = 0;
        placed += placeArenaPack(world, arenaCenterTop);
        placed += placeTransitionPack(world, arenaCenterTop);

        // First tower pack starts directly above the 16-high transition band.
        BlockPos firstTowerOrigin = arenaCenterTop.add(0, 47, 0);
        placed += placeFirstPack(world, firstTowerOrigin);

        return placed;
    }

    public static int placeShellQuarter4(ServerWorld world, net.minecraft.util.Identifier templateId, BlockPos pos) {
        int placed = 0;
        placed += place(world, templateId, pos, BlockRotation.NONE);
        placed += place(world, templateId, pos, BlockRotation.CLOCKWISE_90);
        placed += place(world, templateId, pos, BlockRotation.CLOCKWISE_180);
        placed += place(world, templateId, pos, BlockRotation.COUNTERCLOCKWISE_90);
        return placed;
    }

    public static int place(ServerWorld world, net.minecraft.util.Identifier templateId, BlockPos pos, BlockRotation rotation) {
        net.minecraft.structure.StructureTemplateManager manager = world.getStructureTemplateManager();
        java.util.Optional<net.minecraft.structure.StructureTemplate> optional = manager.getTemplate(templateId);

        if (optional.isEmpty()) {
            System.out.println("[Witchlog] Missing template: " + templateId);
            return 0;
        }

        net.minecraft.structure.StructureTemplate template = optional.get();

        net.minecraft.structure.StructurePlacementData placementData = new net.minecraft.structure.StructurePlacementData()
                .setMirror(net.minecraft.util.BlockMirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(false);

        boolean success = template.place(
                world,
                pos,
                pos,
                placementData,
                world.getRandom(),
                net.minecraft.block.Block.NOTIFY_LISTENERS
        );

        if (!success) {
            System.out.println("[Witchlog] Failed placing template: " + templateId + " at " + pos + " rot=" + rotation);
            return 0;
        }

        return 1;
    }

    public static net.minecraft.util.Identifier id(String path) {
        return new net.minecraft.util.Identifier("odd", "witchlog/" + path);
    }
}
