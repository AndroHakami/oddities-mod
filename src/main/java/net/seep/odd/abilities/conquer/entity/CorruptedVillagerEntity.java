package net.seep.odd.abilities.conquer.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class CorruptedVillagerEntity extends VillagerEntity {
    public CorruptedVillagerEntity(EntityType<? extends VillagerEntity> type, World world) {
        super(type, world);
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        // Only Conquer can trade
        if (!this.getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            String current = net.seep.odd.abilities.PowerAPI.get(sp);
            if (!"conquer".equals(current)) {
                sp.sendMessage(Text.literal("The corrupted villager ignores you."), true);
                return ActionResult.FAIL;
            }

            // Always discounts (special price negative = cheaper)
            var offers = this.getOffers();
            for (var offer : offers) {
                // Safe, exists in Yarn 1.20.1: TradeOffer#increaseSpecialPrice(int) :contentReference[oaicite:0]{index=0}
                offer.increaseSpecialPrice(-3);
            }
        }

        return super.interactMob(player, hand);
    }
}
