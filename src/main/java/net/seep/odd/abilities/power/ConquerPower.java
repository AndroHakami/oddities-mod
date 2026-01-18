// src/main/java/net/seep/odd/abilities/power/ConquerPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.conquer.entity.DarkHorseEntity;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.item.ModItems;
import net.seep.odd.status.ModStatusEffects;

import java.util.List;
import java.util.UUID;

public final class ConquerPower implements Power {

    @Override public String id() { return "conquer"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 2 * 20; }
    @Override public long secondaryCooldownTicks() { return 2 * 20; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/conquer_dark_horse.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/conquer_corrupt.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Summon Milo. Press again to dismiss. Milo remembers armor/HP. If killed, 120s lock.";
            case "secondary" ->
                    "Tap: Corrupt a Villager or Iron Golem. Sneak+Tap: Capture a CORRUPTED Iron Golem into a Metalic Frost Spawn.";
            default -> "Conquer";
        };
    }

    @Override
    public String longDescription() {
        return "Conquer: Summon Milo (persistent state + death lockout). Corrupt villagers/golems via a status effect. "
                + "Capture: Sneak + Secondary on a corrupted golem to bottle it into a Metalic Frost Spawn item.";
    }

    /** Power check (NO tags): matches your TameBallItem gate. */
    public static boolean hasConquer(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "conquer".equals(current);
    }

    /* =========================
       Milo persistence + cooldown
       ========================= */

    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_HORSES = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<UUID, NbtCompound> STORED_HORSE_NBT = new Object2ObjectOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> DEATH_COOLDOWN_UNTIL = new Object2LongOpenHashMap<>();
    private static final long DEATH_COOLDOWN_TICKS = 120L * 20L; // 120s

    public static void onMiloKilled(UUID ownerUuid, ServerWorld world) {
        ACTIVE_HORSES.remove(ownerUuid);
        STORED_HORSE_NBT.remove(ownerUuid);
        DEATH_COOLDOWN_UNTIL.put(ownerUuid, world.getTime() + DEATH_COOLDOWN_TICKS);
    }

    public static void serverTick(ServerPlayerEntity player) {
        UUID horseId = ACTIVE_HORSES.get(player.getUuid());
        if (horseId == null) return;

        MinecraftServer server = player.getServer();
        if (server == null) {
            ACTIVE_HORSES.remove(player.getUuid());
            return;
        }

        DarkHorseEntity horse = findHorse(server, horseId);
        if (horse == null || !horse.isAlive()) {
            ACTIVE_HORSES.remove(player.getUuid());
            return;
        }

        if (horse.getWorld() instanceof ServerWorld sw) {
            if ((horse.age % 4) == 0) {
                sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                        horse.getX(), horse.getBodyY(0.6), horse.getZ(),
                        1, 0.30, 0.20, 0.30, 0.01);
                sw.spawnParticles(ParticleTypes.SCULK_SOUL,
                        horse.getX(), horse.getBodyY(0.6), horse.getZ(),
                        1, 0.30, 0.20, 0.30, 0.01);
            }
        }
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID playerId = player.getUuid();

        long now = player.getServerWorld().getTime();
        long until = DEATH_COOLDOWN_UNTIL.getOrDefault(playerId, 0L);
        if (now < until) {
            long remSeconds = (until - now + 19) / 20;
            player.sendMessage(Text.literal("Milo was slain. Wait " + remSeconds + "s to resummon."), true);
            return;
        }

        UUID existing = ACTIVE_HORSES.get(playerId);

        // Dismiss -> store NBT snapshot
        if (existing != null) {
            DarkHorseEntity horse = findHorse(server, existing);
            if (horse != null) {
                NbtCompound nbt = new NbtCompound();
                horse.writeNbt(nbt);

                nbt.remove("UUID");
                nbt.remove("UUIDMost");
                nbt.remove("UUIDLeast");
                nbt.remove("Pos");
                nbt.remove("Motion");
                nbt.remove("Rotation");
                nbt.remove("Dimension");

                STORED_HORSE_NBT.put(playerId, nbt);
                horse.discard();
            }

            ACTIVE_HORSES.remove(playerId);

            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.PLAYERS, 0.8f, 0.7f);
            player.sendMessage(Text.literal("Milo: DISMISSED"), true);
            return;
        }

        // Summon -> restore NBT snapshot if present
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        DarkHorseEntity horse = ModEntities.DARK_HORSE.create(sw);
        if (horse == null) return;

        NbtCompound saved = STORED_HORSE_NBT.remove(playerId);
        if (saved != null) horse.readNbt(saved);

        Vec3d forward = player.getRotationVec(1.0f).normalize().multiply(1.8);
        Vec3d spawnPos = player.getPos().add(forward);
        horse.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), 0.0f);

        horse.setCustomName(Text.literal("Milo"));
        horse.setCustomNameVisible(true);
        horse.setTame(true);
        horse.setOwnerUuid(player.getUuid());
        horse.setPersistent();
        horse.setBreedingAge(0);

        if (horse.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            horse.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40.0D);
        }
        if (saved == null) horse.setHealth(40.0F);

        if (horse.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null) {
            horse.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35D);
        }
        if (horse.getAttributeInstance(EntityAttributes.HORSE_JUMP_STRENGTH) != null) {
            horse.getAttributeInstance(EntityAttributes.HORSE_JUMP_STRENGTH).setBaseValue(1.15D);
        }

        sw.spawnEntity(horse);
        ACTIVE_HORSES.put(playerId, horse.getUuid());

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.PLAYERS, 0.9f, 0.9f);
        player.sendMessage(Text.literal("Milo: SUMMONED"), true);
    }

    /* =========================
       Ability 2 + 3: Corrupt / Capture
       ========================= */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // Sneak+Secondary = CAPTURE (Ability 3)
        if (player.isSneaking()) {
            if (tryCaptureCorruptedGolem(player)) return;
            return;
        }

        // Normal Secondary = CORRUPT (Ability 2)
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        LivingEntity target = findCorruptTarget(player, 5.0);
        if (target == null) {
            player.sendMessage(Text.literal("No Villager or Iron Golem in sight."), true);
            return;
        }

        if (target.hasStatusEffect(ModStatusEffects.CORRUPTION)) {
            player.sendMessage(Text.literal("Already corrupted."), true);
            return;
        }

        target.addStatusEffect(new StatusEffectInstance(
                ModStatusEffects.CORRUPTION,
                20 * 60 * 60 * 24 * 365,
                0,
                true,
                false,
                false
        ));

        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 3 * 20, 0, true, false, false));

        sw.spawnParticles(ParticleTypes.SCULK_SOUL,
                target.getX(), target.getBodyY(0.6), target.getZ(),
                14, 0.35, 0.35, 0.35, 0.01);
        sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getBodyY(0.6), target.getZ(),
                10, 0.35, 0.35, 0.35, 0.01);

        sw.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.PLAYERS, 0.9f, 0.7f);
        player.sendMessage(Text.literal("Corruption applied."), true);
    }

    /** Ability 3: Capture a corrupted Iron Golem into an item (stores full NBT except UUID/pos). */
    private static boolean tryCaptureCorruptedGolem(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;

        IronGolemEntity golem = findCorruptedGolemTarget(player, 6.0);
        if (golem == null) {
            player.sendMessage(Text.literal("Sneak+Secondary: look at a CORRUPTED Iron Golem to capture it."), true);
            return false;
        }

        // Write golem NBT
        NbtCompound golemNbt = new NbtCompound();
        golem.writeNbt(golemNbt);

        // strip identity/position so we can safely respawn it
        golemNbt.remove("UUID");
        golemNbt.remove("UUIDMost");
        golemNbt.remove("UUIDLeast");
        golemNbt.remove("Pos");
        golemNbt.remove("Motion");
        golemNbt.remove("Rotation");
        golemNbt.remove("Dimension");
        golemNbt.remove("PortalCooldown");

        ItemStack spawn = new ItemStack(ModItems.METALIC_FROST_SPAWN);
        spawn.getOrCreateNbt().put("Golem", golemNbt);

        // Remove golem from world
        sw.spawnParticles(ParticleTypes.SNOWFLAKE, golem.getX(), golem.getBodyY(0.6), golem.getZ(),
                60, 1.2, 1.0, 1.2, 0.02);
        sw.spawnParticles(ParticleTypes.SCULK_SOUL, golem.getX(), golem.getBodyY(0.6), golem.getZ(),
                30, 1.0, 0.8, 1.0, 0.02);
        sw.playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.PLAYERS, 0.9f, 0.6f);

        golem.discard();

        // Give item
        if (!player.getInventory().insertStack(spawn)) {
            player.dropItem(spawn, false);
        }

        player.sendMessage(Text.literal("Captured: Metalic Frost Spawn"), true);
        return true;
    }

    private static IronGolemEntity findCorruptedGolemTarget(ServerPlayerEntity player, double range) {
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1.75);

        List<Entity> candidates = player.getWorld().getOtherEntities(
                player,
                box,
                e -> (e instanceof IronGolemEntity ig)
                        && ig.isAlive()
                        && ig.hasStatusEffect(ModStatusEffects.CORRUPTION)
        );

        IronGolemEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity e : candidates) {
            double d = e.squaredDistanceTo(player);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = (IronGolemEntity) e;
            }
        }
        return best;
    }

    private static LivingEntity findCorruptTarget(ServerPlayerEntity player, double range) {
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1.25);

        List<Entity> candidates = player.getWorld().getOtherEntities(
                player,
                box,
                e -> (e instanceof VillagerEntity || e instanceof IronGolemEntity) && e.isAlive()
        );

        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity e : candidates) {
            double d = e.squaredDistanceTo(player);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    private static DarkHorseEntity findHorse(MinecraftServer server, UUID horseUuid) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(horseUuid);
            if (e instanceof DarkHorseEntity dh) return dh;
        }
        return null;
    }
}
