package net.seep.odd.item.custom;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.seep.odd.abilities.PowerAPI;

public final class DeviceItem extends Item {
    private static final String CUSTOM_MODEL_DATA_KEY = "CustomModelData";

    public DeviceItem(Settings settings) {
        super(settings);
    }

    @Override
    public void onCraft(ItemStack stack, World world, PlayerEntity player) {
        super.onCraft(stack, world, player);

        if (world.isClient) {
            return;
        }

        applyCrafterPowerTexture(stack, player);
    }

    private static void applyCrafterPowerTexture(ItemStack stack, PlayerEntity player) {
        String powerId = PowerAPI.get((ServerPlayerEntity) player);
        int customModelData = DevicePowerModels.getCustomModelDataForPower(powerId);

        NbtCompound nbt = stack.getOrCreateNbt();
        if (customModelData != 0) {
            nbt.putInt(CUSTOM_MODEL_DATA_KEY, customModelData);
        } else {
            nbt.remove(CUSTOM_MODEL_DATA_KEY);
        }
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