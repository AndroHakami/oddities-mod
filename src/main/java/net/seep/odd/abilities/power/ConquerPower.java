// src/main/java/net/seep/odd/abilities/power/ConquerPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.conquer.entity.DarkHorseEntity;
import net.seep.odd.entity.ModEntities;

import java.util.UUID;

public final class ConquerPower implements Power {

    @Override public String id() { return "conquer"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot);
    }

    // Small cooldown to prevent spam-summoning
    @Override public long cooldownTicks() { return 2 * 20; }
    @Override public long secondaryCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/conquer_dark_horse.png");
            default        -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Summon Milo, a powerful Dark Horse wreathed in snowflake and sculk particles. Press again to dismiss.";
            default        -> "Conquer";
        };
    }

    @Override
    public String longDescription() {
        return "Summon Milo, a powerful Dark Horse. Milo comes pre-saddled, has superior horse stats, "
                + "and emits snowflake + sculk particles. Press again to dismiss.";
    }

    /* ===== State ===== */

    // playerUuid -> horseUuid
    private static final Object2ObjectOpenHashMap<UUID, UUID> ACTIVE_HORSES = new Object2ObjectOpenHashMap<>();

    /* ===== Passive tick (optional) =====
       Your core power system appears to call these serverTick(...) methods (see FireSwordPower).
       Here we use it to:
       - clean up stale horse references
       - keep the saddle present
       - emit subtle particles from server so everyone sees them
    */
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

        // Keep saddle present (slot 0 is saddle slot for horses)
        if (horse.getInventory().getStack(0).isEmpty()) {
            horse.getInventory().setStack(0, new ItemStack(Items.SADDLE));
        }

        // Particles (server-side broadcast). Keep it tasteful.
        if (horse.getWorld() instanceof ServerWorld sw) {
            if ((horse.age & 1) == 0) { // every 2 ticks
                sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                        horse.getX(), horse.getBodyY(0.6), horse.getZ(),
                        2, 0.35, 0.25, 0.35, 0.01);

                sw.spawnParticles(ParticleTypes.SCULK_SOUL,
                        horse.getX(), horse.getBodyY(0.6), horse.getZ(),
                        1, 0.35, 0.25, 0.35, 0.01);
            }
        }
    }

    /* ===== Primary: toggle summon/dismiss ===== */

    @Override
    public void activate(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID playerId = player.getUuid();
        UUID existing = ACTIVE_HORSES.get(playerId);

        // If exists -> dismiss
        if (existing != null) {
            DarkHorseEntity horse = findHorse(server, existing);
            if (horse != null) {
                horse.discard();
            }
            ACTIVE_HORSES.remove(playerId);

            player.getWorld().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_HORSE_AMBIENT,
                    SoundCategory.PLAYERS,
                    0.8f,
                    0.7f
            );
            player.sendMessage(Text.literal("Milo: DISMISSED"), true);
            return;
        }

        // Else -> summon
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        DarkHorseEntity horse = ModEntities.DARK_HORSE.create(sw);
        if (horse == null) return;

        Vec3d forward = player.getRotationVec(1.0f).normalize().multiply(1.8);
        Vec3d spawnPos = player.getPos().add(forward);

        horse.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), 0.0f);

        horse.setCustomName(Text.literal("Milo"));
        horse.setCustomNameVisible(true);

        horse.setTame(true);
        horse.setOwnerUuid(player.getUuid());
        horse.setPersistent();

        // Stronger “maxed” stats (also registered in entity attrs, but we enforce here too)
        if (horse.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            horse.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40.0D);
        }
        horse.setHealth(40.0F);

        if (horse.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null) {
            horse.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35D);
        }
        if (horse.getAttributeInstance(EntityAttributes.HORSE_JUMP_STRENGTH) != null) {
            horse.getAttributeInstance(EntityAttributes.HORSE_JUMP_STRENGTH).setBaseValue(1.15D);
        }

        // Saddle ready
        horse.getInventory().setStack(0, new ItemStack(Items.SADDLE));

        sw.spawnEntity(horse);
        ACTIVE_HORSES.put(playerId, horse.getUuid());

        // Tiny conjure cue
        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_HORSE_SADDLE,
                SoundCategory.PLAYERS,
                0.9f,
                0.9f
        );

        player.sendMessage(Text.literal("Milo: SUMMONED"), true);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // No secondary ability yet.
    }

    /* ===== helpers ===== */

    private static DarkHorseEntity findHorse(MinecraftServer server, UUID horseUuid) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(horseUuid);
            if (e instanceof DarkHorseEntity dh) return dh;
        }
        return null;
    }

    /** Optional cleanup hook if you have a disconnect event you can call into. */
    public static void dismissOnLogout(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        UUID id = ACTIVE_HORSES.remove(player.getUuid());
        if (server == null || id == null) return;

        DarkHorseEntity horse = findHorse(server, id);
        if (horse != null) horse.discard();
    }
}
