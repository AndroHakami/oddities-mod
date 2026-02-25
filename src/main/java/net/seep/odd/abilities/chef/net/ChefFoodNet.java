// src/main/java/net/seep/odd/abilities/chef/net/ChefFoodNet.java
package net.seep.odd.abilities.chef.net;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.Oddities;
import net.seep.odd.status.ModStatusEffects;

import java.util.UUID;

public final class ChefFoodNet {
    private ChefFoodNet() {}

    public static final Identifier C2S_DRAGON_JUMP =
            new Identifier(Oddities.MOD_ID, "c2s_dragon_jump");

    private static boolean serverInited = false;
    private static boolean clientInited = false;

    private static final Object2IntOpenHashMap<UUID> EXTRA_JUMPS_USED = new Object2IntOpenHashMap<>();

    public static void resetJumps(UUID id) {
        EXTRA_JUMPS_USED.removeInt(id);
    }

    public static void initServer() {
        if (serverInited) return;
        serverInited = true;

        ServerPlayNetworking.registerGlobalReceiver(C2S_DRAGON_JUMP, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                ServerPlayerEntity sp = player; // ✅ already ServerPlayerEntity

                if (!sp.hasStatusEffect(ModStatusEffects.DRAGON_BURRITO)) return;
                if (sp.isOnGround()) return;

                int used = EXTRA_JUMPS_USED.getOrDefault(sp.getUuid(), 0);
                if (used >= 2) return; // two extra jumps

                EXTRA_JUMPS_USED.put(sp.getUuid(), used + 1);

                Vec3d v = sp.getVelocity();
                sp.setVelocity(v.x, 0.62, v.z);
                sp.velocityModified = true;

                ServerWorld sw = sp.getServerWorld();
                sw.spawnParticles(ParticleTypes.CLOUD,
                        sp.getX(), sp.getY() + 0.2, sp.getZ(),
                        12,
                        0.25, 0.05, 0.25,
                        0.02);

                sw.playSound(null, sp.getBlockPos(),
                        SoundEvents.ENTITY_BLAZE_SHOOT, // ✅ .value()
                        SoundCategory.PLAYERS,
                        0.65f,
                        1.35f);
            });
        });
    }

    /** Call from client init (ChefClient.init()). */
    public static void initClient() {
        if (clientInited) return;
        clientInited = true;

        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientTickEvents.END_CLIENT_TICK.register(new net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick() {
            private boolean prevJump = false;

            @Override
            public void onEndTick(MinecraftClient client) {
                if (client.player == null) return;

                boolean pressed = client.options.jumpKey.isPressed();

                if (pressed && !prevJump) {
                    if (client.player.hasStatusEffect(ModStatusEffects.DRAGON_BURRITO) && !client.player.isOnGround()) {
                        ClientPlayNetworking.send(C2S_DRAGON_JUMP, PacketByteBufs.empty());
                    }
                }

                prevJump = pressed;
            }
        });


        // proper implementation:
        ClientTickEvents.END_CLIENT_TICK.register(new net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick() {
            private boolean prevJump = false;

            @Override
            public void onEndTick(MinecraftClient client) {
                if (client.player == null) return;
                boolean pressed = client.options.jumpKey.isPressed();

                if (pressed && !prevJump) {
                    if (client.player.hasStatusEffect(ModStatusEffects.DRAGON_BURRITO) && !client.player.isOnGround()) {
                        ClientPlayNetworking.send(C2S_DRAGON_JUMP, PacketByteBufs.empty());
                    }
                }

                prevJump = pressed;
            }
        });
    }
}