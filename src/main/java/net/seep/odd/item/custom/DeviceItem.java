package net.seep.odd.item.custom;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class DeviceItem extends Item {
    public DeviceItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            Client.open();
        }

        return TypedActionResult.success(stack, world.isClient());
    }

    @Environment(EnvType.CLIENT)
    private static final class Client {
        private static void open() {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.player == null) return;
            client.setScreen(new net.seep.odd.client.device.DeviceHomeScreen());
        }
    }
}