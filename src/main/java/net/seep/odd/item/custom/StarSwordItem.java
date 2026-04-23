package net.seep.odd.item.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.server.world.ServerWorld;

public class StarSwordItem extends SwordItem {
    public StarSwordItem() {
        super(ToolMaterials.DIAMOND, 3, -2.4f, new Item.Settings());
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient && attacker.getWorld() instanceof ServerWorld serverWorld) {
            StarSwordController.applyMark(serverWorld, target, attacker);
        }
        return super.postHit(stack, target, attacker);
    }
}
