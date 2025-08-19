package net.seep.odd;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.PowerCommands;
import net.seep.odd.abilities.astral.AstralInventory;
import net.seep.odd.abilities.client.AbilityHudOverlay;
import net.seep.odd.abilities.client.ClientCooldowns;
import net.seep.odd.abilities.net.AbilityKeyPacket;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.abilities.net.PossessionControlPacket;
import net.seep.odd.abilities.net.UmbraNet;
import net.seep.odd.abilities.possession.PossessionManager;
import net.seep.odd.abilities.power.*;

import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.grandanvil.ModScreens;
import net.seep.odd.block.grandanvil.net.GrandAnvilNet;
import net.seep.odd.block.grandanvil.recipe.ModGrandAnvilRecipes;
import net.seep.odd.enchant.ItalianStompersHandler;
import net.seep.odd.enchant.ModEnchantments;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.item.ModItemGroups;
import net.seep.odd.item.ModItems;
import net.seep.odd.item.ghost.client.GhostHandModel;
import net.seep.odd.item.ghost.client.GhostHandRenderer;
import net.seep.odd.sound.ModSounds;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// IMPORTANT: use the NETWORKING package for join events, not lifecycle:
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents; // ✅ correct one
import software.bernie.geckolib.GeckoLib;

public class Oddities implements ModInitializer {
	public static final String MOD_ID = "odd";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// ---- content ----
		ModItemGroups.registerItemGroups();
		ModBlocks.registerModBlocks();
		ModItems.registerModItems();
		ModSounds.registerSounds();
		FuelRegistry.INSTANCE.add(ModItems.COAL_BRIQUETTE, 200);

		// ---- powers (register ONCE) ----
		Powers.register(new FireBlastPower());
		Powers.register(new Blockade());
		Powers.register(new UmbraSoulPower());
		Powers.register(new ForgerPower());
		ClientCooldowns.registerTicker();

		// Umbra Soul
		UmbraNet.registerClient();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var p : server.getPlayerManager().getPlayerList()) {
				UmbraSoulPower.serverTick(p);
			}
		});


		// Forger Power Stuff
		ModScreens.register();
		ModEnchantments.register();
		GrandAnvilNet.registerServer();
		ItalianStompersHandler.register();
		ModGrandAnvilRecipes.register();
		ModEnchantments.registerTicker();
		// Gekko Lib
		GeckoLib.initialize();


// (Your block + block entity registrations should already exist in ModBlocks;
//  ensure there is only ONE registration for the block id "odd:grand_anvil".)



		// ---- commands (ONCE) ----
		PowerCommands.register();

		AbilityHudOverlay.register();
		// Umbra: server tick driving the shadow meter + movement
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var p : server.getPlayerManager().getPlayerList()) {
				net.seep.odd.abilities.power.UmbraSoulPower.serverTick(p);
			}
		});

		// Umbra_Astral
		net.seep.odd.abilities.astral.AstralGuards.register();







		// ---- networking / tickers (server-side, ONCE) ----
		AbilityKeyPacket.registerServerReceiver();               // ability keypress packets
		PossessionManager.INSTANCE.registerTicker();             // possession loop
		PossessionControlPacket.registerServer((player, state) -> {
			var ses = PossessionManager.INSTANCE.getSession(player);
			if (ses != null) ses.last = state;                   // store latest inputs for this player
		});

		// Umbra Astral
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var p : server.getPlayerManager().getPlayerList()) {
				net.seep.odd.abilities.power.UmbraSoulPower.serverTick(p);
			}
		});

// add this once:
		net.seep.odd.abilities.net.UmbraNet.registerServerAstral();
		AstralInventory.init(ModItems.GHOST_HAND);
		ModEntities.register();
		FabricDefaultAttributeRegistry.register(ModEntities.CREEPY, CreepyEntity.createAttributes());








		// Block player interactions while possessing (body is anchored)
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);

		// ---- sync current power to client on join ----
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			String id = PowerAPI.get(player);
			PowerNetworking.syncTo(player, id);
			LOGGER.info("[Oddities] synced power '{}' to {}", id, player.getEntityName());
		});

		LOGGER.info("Oddities initialized (powers + networking ready).");
	}
}
