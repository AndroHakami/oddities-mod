package net.seep.odd.abilities.power;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.seep.odd.sound.ModSounds;

public class FireBlastPower implements Power {
    @Override public String id() { return "fire_blast"; }

    @Override
    public void activate(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        // Where the blast happens: ~3 blocks in front of the player, chest height
        Vec3d look   = player.getRotationVec(1.0F).normalize();
        Vec3d origin = player.getPos()
                .add(0, player.getStandingEyeHeight() * 0.6, 0)
                .add(look.multiply(3.0));

        // Visuals + sound (cold-themed)
        world.spawnParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SNOWFLAKE, origin.x, origin.y, origin.z, 90, 0.8, 0.6, 0.8, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 50, 0.8, 0.6, 0.8, 0.02);
        world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Play the cast animation on all clients


        // Affect entities in a small radius, biased in a cone forward
        double radius = 3.2;
        Box aabb = new Box(origin, origin).expand(radius);
        var targets = world.getEntitiesByClass(LivingEntity.class, aabb, e -> e != player && e.isAlive());

        for (LivingEntity e : targets) {
            // Only hit things roughly in front (cone check)
            Vec3d toTarget = e.getPos().add(0, e.getStandingEyeHeight() * 0.5, 0).subtract(player.getPos());
            double dist = toTarget.length();
            if (dist > radius + 1.0) continue;
            toTarget = toTarget.normalize();
            double dot = toTarget.dotProduct(look); // 1 = straight ahead
            if (dot < 0.25) continue; // ~60° cone

            // "Cold" effects
            e.extinguish();
            e.setFrozenTicks(Math.min(e.getFrozenTicks() + 140, 400)); // add ~7s
            e.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 100, 1));        // 5s, Slowness II
            e.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE, 100, 0));  // 5s, Fatigue I
            e.damage(world.getDamageSources().freeze(), 4.0f);

            world.spawnParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getBodyY(0.5), e.getZ(), 20, 0.2, 0.2, 0.2, 0.01);
        }

        // Light snow patches near origin (non-destructive)
        Random rand = world.getRandom();
        int tries = 18;
        for (int i = 0; i < tries; i++) {
            double rx = origin.x + (rand.nextDouble() - 0.5) * 2.2;
            double rz = origin.z + (rand.nextDouble() - 0.5) * 2.2;
            int x = MathHelper.floor(rx);
            int z = MathHelper.floor(rz);
            int y = MathHelper.floor(origin.y);

            BlockPos.Mutable pos = new BlockPos.Mutable(x, y, z);
            for (int j = 0; j < 4 && world.isAir(pos); j++) pos.setY(pos.getY() - 1);

            BlockPos above = pos.up();
            BlockState aboveState = world.getBlockState(above);
            if (aboveState.isAir() && world.getBlockState(pos).isSolidBlock(world, pos)) {
                if (rand.nextFloat() < 0.35f) {
                    world.setBlockState(above, Blocks.SNOW.getDefaultState(), 3);
                }
            }
        }
    }

    @Override public long cooldownTicks() { return 80; }       // 4s
    @Override public long secondaryCooldownTicks() { return 20; } // 1s

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        var hit = player.raycast(32.0, 0.0f, false);
        if (hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            player.sendMessage(net.minecraft.text.Text.literal("No valid spot in range."), true);
            return;
        }
        var bhr = (net.minecraft.util.hit.BlockHitResult) hit;

        var pos = bhr.getBlockPos();
        double tx = pos.getX() + 0.5;
        double ty = pos.getY() + 1.0;
        double tz = pos.getZ() + 0.5;

        var above = pos.up();
        boolean spaceOk = world.getBlockState(pos.up()).isAir() && world.getBlockState(pos.up(2)).isAir();
        if (!spaceOk) {
            ty += 1.0;
            spaceOk = world.getBlockState(above.up()).isAir();
            if (!spaceOk) {
                player.sendMessage(net.minecraft.text.Text.literal("No space to blink there."), true);
                return;
            }
        }

        // departure
        var from = player.getPos().add(0, player.getStandingEyeHeight() * 0.5, 0);
        world.spawnParticles(ParticleTypes.SNOWFLAKE, from.x, from.y, from.z, 60, 0.6, 0.4, 0.6, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 25, 0.5, 0.3, 0.5, 0.01);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.SNOWGRAVE_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // teleport
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        player.teleport(world, tx, ty, tz, yaw, pitch);
        player.fallDistance = 0.0f;

        // arrival
        world.spawnParticles(ParticleTypes.SNOWFLAKE, tx, ty, tz, 60, 0.6, 0.4, 0.6, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, tx, ty, tz, 25, 0.5, 0.3, 0.5, 0.01);
        world.playSound(null, tx, ty, tz, ModSounds.SNOWGRAVE_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/fire_blast.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/snow_teleport.png");
            case "third"     -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
            case "fourth"    -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
            case "overview"  -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Detonate a snowy blast a few blocks ahead, chilling enemies in a forward cone.";
            case "secondary" -> "Blink to your crosshair up to 32 blocks in a flash of cold.";
            case "overview" -> "Snow-caster brings ice-cold abilities to the fight: explosives and teleports at your fingertips.";
            default -> "Snow-caster brings ice-cold abilities to the fight: explosives and teleports at your fingertips.";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/player_icon.png");
    }

    @Override
    public String longDescription() {
        return """
           Your coldness is felt all-through, whispers of the one who can stop it all,
           use freeze abilities to freeze mobs, and forbidden magic to teleport..
           """;
    }
    @Override
    public boolean hasSlot(String slot) {
        if ("primary".equals(slot)) return true;
        if ("secondary".equals(slot)) return true; // FireBlast has blink
        return false;
    }
}
