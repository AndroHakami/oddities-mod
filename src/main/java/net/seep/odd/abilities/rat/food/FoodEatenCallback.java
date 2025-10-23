package net.seep.odd.abilities.rat.food;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fired when a player actually finishes consuming an edible item. */
@FunctionalInterface
public interface FoodEatenCallback {
    Event<FoodEatenCallback> EVENT = EventFactory.createArrayBacked(FoodEatenCallback.class,
            listeners -> (player, stack, food, saturation) -> {
                for (var l : listeners) l.onEaten(player, stack, food, saturation);
            });

    void onEaten(ServerPlayerEntity player, ItemStack stack, int food, float saturation);
}
