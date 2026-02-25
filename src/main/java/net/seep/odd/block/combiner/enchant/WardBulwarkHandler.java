// src/main/java/net/seep/odd/block/combiner/enchant/WardBulwarkHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WardBulwarkHandler {
    private WardBulwarkHandler() {}

    private static boolean installed = false;

    // “high damage” threshold (health points; 2 = 1 heart)
    private static final float HIGH_DAMAGE_THRESHOLD = 8.0f; // 4 hearts

    // prevents spam if you get machine-gunned
    private static final int COOLDOWN_TICKS = 20; // 1s

    // cap absorption so it doesn't get silly
    private static final float BASE_MAX_ABS = 24.0f; // 12 hearts

    private static final Map<UUID, Float> lastEffective = new HashMap<>();
    private static final Map<UUID, Integer> cooldown = new HashMap<>();

    public static void init() {
        if (installed) return;
        installed = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p);
            }
        });
    }

    private static void tickPlayer(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        UUID id = p.getUuid();

        // cooldown tick down
        Integer cd = cooldown.get(id);
        if (cd != null) {
            if (cd <= 1) cooldown.remove(id);
            else cooldown.put(id, cd - 1);
        }

        // must have WARD on chestplate
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        int lvl = (chest == null || chest.isEmpty() || CombinerEnchantments.WARD == null)
                ? 0
                : EnchantmentHelper.getLevel(CombinerEnchantments.WARD, chest);

        float curEff = p.getHealth() + p.getAbsorptionAmount();
        Float prevEffObj = lastEffective.put(id, curEff);

        // first tick seen
        if (prevEffObj == null) return;

        float prevEff = prevEffObj;

        // ignore increases (regen, our absorption, etc.)
        if (curEff >= prevEff) return;

        // effective damage taken this tick window
        float delta = prevEff - curEff;

        // only trigger on big hits + if enchanted
        if (lvl <= 0) return;
        if (delta < HIGH_DAMAGE_THRESHOLD) return;

        // if on cooldown, do nothing
        if (cooldown.containsKey(id)) return;
        cooldown.put(id, COOLDOWN_TICKS);

        // absorption equal to damage (delta), capped
        float maxAbs = BASE_MAX_ABS + (lvl - 1) * 8.0f; // lvl2 cap a bit higher (16 hearts total cap)
        float newAbs = Math.min(maxAbs, p.getAbsorptionAmount() + delta);
        p.setAbsorptionAmount(newAbs);

        // warden-ish activation sound
        sw.playSound(
                null,
                p.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                SoundCategory.PLAYERS,
                0.9f,
                0.95f
        );
    }
}