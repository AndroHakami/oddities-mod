package net.seep.odd.abilities.power;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.event.GameEvent;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.cultist.SightseerEntity;
import net.seep.odd.status.ModStatusEffects;
import org.jetbrains.annotations.Nullable;

public final class CultistPower implements Power {

    @Override public String id() { return "cultist"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot); // only Divine Touch
    }

    @Override public long cooldownTicks() { return 12; }

    @Override
    public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/cultist_divine_touch.png");
    }

    @Override
    public String slotLongDescription(String slot) {
        return "Divine Touch: Toggle Divine Protection on players, or bring a built Sightseer to life.";
    }

    @Override
    public String longDescription() {
        return "Cultist: Summon holy constructs. Divine Touch toggles Divine Protection on players and animates built monsters.";
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        // 1) Entity hit first (players)
        EntityHitResult ehr = raycastEntity(player, 6.0);
        if (ehr != null && ehr.getEntity() instanceof PlayerEntity targetPlayer) {
            toggleDivineProtection(player, targetPlayer);
            return;
        }

        // 2) Block hit (structures)
        BlockHitResult bhr = raycastBlock(player, 6.0);
        if (bhr.getType() != HitResult.Type.BLOCK) {
            // tiny “nothing” puff
            divineTouchFx(sw, player.getPos().add(0, player.getStandingEyeHeight() * 0.6, 0), 8, 4);
            player.sendMessage(Text.literal("Divine Touch: nothing to bless."), true);
            return;
        }

        BlockPos hit = bhr.getBlockPos();

        // Try to animate a Sightseer structure near where the player touched
        if (trySpawnSightseerFromBuild(sw, hit)) {
            // FX at the touched block
            divineTouchFx(sw, Vec3d.ofCenter(hit).add(0, 0.6, 0), 16, 8);

            sw.playSound(null, hit, SoundEvents.BLOCK_SOUL_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
            sw.emitGameEvent(player, GameEvent.ENTITY_PLACE, hit);
            player.sendMessage(Text.literal("Divine Touch: Sightseer awakened."), true);
            return;
        }

        // subtle fail feedback at the block
        divineTouchFx(sw, Vec3d.ofCenter(hit).add(0, 0.6, 0), 10, 5);
        player.sendMessage(Text.literal("Divine Touch: no valid construct found."), true);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // none
    }

    /* =========================================================
       Divine Protection toggle
       ========================================================= */

    private static final int FOREVER = 20 * 60 * 60 * 24 * 365; // 1 year (milk removes earlier)

    private static void toggleDivineProtection(ServerPlayerEntity caster, PlayerEntity target) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        Vec3d fxPos = target.getPos().add(0, target.getStandingEyeHeight() * 0.55, 0);

        if (target.hasStatusEffect(ModStatusEffects.DIVINE_PROTECTION)) {
            target.removeStatusEffect(ModStatusEffects.DIVINE_PROTECTION);

            // subtle FX
            divineTouchFx(sw, fxPos, 16, 8);

            sw.playSound(null, target.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8f, 1.4f);

            if (target instanceof ServerPlayerEntity st)
                st.sendMessage(Text.literal("Divine Protection removed."), true);

            caster.sendMessage(Text.literal("Divine Touch: removed protection."), true);
            return;
        }

        target.addStatusEffect(new StatusEffectInstance(
                ModStatusEffects.DIVINE_PROTECTION,
                FOREVER,
                0,
                true,
                false,
                true
        ));

        // subtle FX
        divineTouchFx(sw, fxPos, 18, 9);

        sw.playSound(null, target.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE, SoundCategory.PLAYERS, 0.9f, 1.2f);

        if (target instanceof ServerPlayerEntity st)
            st.sendMessage(Text.literal("Divine Protection granted."), true);

        caster.sendMessage(Text.literal("Divine Touch: granted protection."), true);
    }

    /* =========================================================
       Block structure -> Sightseer spawn
       Pattern:
       - Soul Sand "T": bottom(0,0,0), top(0,1,0), arms on top left/right (axis X or Z)
       - Skeleton skulls ONLY on the outer arms (no middle skull)
       ========================================================= */

    private static boolean trySpawnSightseerFromBuild(ServerWorld sw, BlockPos hit) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos base = hit.add(dx, dy, dz);

                    if (matchesSightseer(sw, base, true)) { // arms along X
                        consumeAndSpawnSightseer(sw, base, true);
                        return true;
                    }
                    if (matchesSightseer(sw, base, false)) { // arms along Z
                        consumeAndSpawnSightseer(sw, base, false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesSightseer(ServerWorld sw, BlockPos base, boolean axisX) {
        if (!isSoul(sw.getBlockState(base))) return false;
        if (!isSoul(sw.getBlockState(base.up(1)))) return false;

        BlockPos armA = axisX ? base.up(1).west() : base.up(1).north();
        BlockPos armB = axisX ? base.up(1).east() : base.up(1).south();

        if (!isSoul(sw.getBlockState(armA))) return false;
        if (!isSoul(sw.getBlockState(armB))) return false;

        BlockPos skullA = armA.up(1);
        BlockPos skullB = armB.up(1);

        if (!isSkeletonSkull(sw.getBlockState(skullA))) return false;
        if (!isSkeletonSkull(sw.getBlockState(skullB))) return false;

        // No middle skull above the top center
        if (!sw.getBlockState(base.up(2)).isAir()) return false;

        return true;
    }

    private static void consumeAndSpawnSightseer(ServerWorld sw, BlockPos base, boolean axisX) {
        BlockPos armA = axisX ? base.up(1).west() : base.up(1).north();
        BlockPos armB = axisX ? base.up(1).east() : base.up(1).south();

        BlockPos skullA = armA.up(1);
        BlockPos skullB = armB.up(1);

        // remove blocks like golems do
        setAir(sw, skullA);
        setAir(sw, skullB);
        setAir(sw, armA);
        setAir(sw, armB);
        setAir(sw, base.up(1));
        setAir(sw, base);

        // spawn entity
        SightseerEntity sightseer = ModEntities.SIGHTSEER.create(sw);
        if (sightseer == null) return;

        sightseer.setHomePos(base);
        sightseer.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 0.05, base.getZ() + 0.5, 0.0f, 0.0f);
        sightseer.setPersistent();

        sw.spawnEntity(sightseer);

        // subtle awaken FX at spawn
        divineTouchFx(sw, Vec3d.ofCenter(base).add(0, 0.9, 0), 20, 10);

        sw.playSound(null, base, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 1.6f);
    }

    private static void setAir(ServerWorld sw, BlockPos pos) {
        sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static boolean isSoul(BlockState s) {
        return s.isOf(Blocks.SOUL_SAND);
    }

    private static boolean isSkeletonSkull(BlockState s) {
        return s.isOf(Blocks.SKELETON_SKULL)
                || s.isOf(Blocks.SKELETON_WALL_SKULL)
                || s.isOf(Blocks.WITHER_SKELETON_SKULL)
                || s.isOf(Blocks.WITHER_SKELETON_WALL_SKULL);
    }

    /* =========================================================
       FX (subtle enchant letters + purple ender)
       ========================================================= */

    private static void divineTouchFx(ServerWorld sw, Vec3d pos, int enchantCount, int portalCount) {
        sw.spawnParticles(ParticleTypes.ENCHANT,
                pos.x, pos.y, pos.z,
                enchantCount,
                0.32, 0.42, 0.32,
                0.07);

        sw.spawnParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 0.20, pos.z,
                portalCount,
                0.28, 0.32, 0.28,
                0.03);
    }

    /* =========================================================
       Raycasts (server-safe)
       ========================================================= */

    @Nullable
    private static EntityHitResult raycastEntity(ServerPlayerEntity player, double range) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(range));

        return net.minecraft.entity.projectile.ProjectileUtil.raycast(
                player,
                start,
                end,
                player.getBoundingBox().stretch(look.multiply(range)).expand(1.0),
                e -> e instanceof PlayerEntity && e.isAlive() && e != player,
                range * range
        );
    }

    private static BlockHitResult raycastBlock(ServerPlayerEntity player, double range) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(range));

        return player.getWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
    }
}
