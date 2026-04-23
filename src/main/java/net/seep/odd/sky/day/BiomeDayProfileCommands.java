package net.seep.odd.sky.day;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.Map;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BiomeDayProfileCommands {
    private BiomeDayProfileCommands() {}

    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("biomedaysky")
                        .requires(source -> source.hasPermissionLevel(2))

                        .then(literal("set")
                                .then(argument("biome", IdentifierArgumentType.identifier())
                                        .then(argument("skyHex", StringArgumentType.word())
                                                .then(argument("fogHex", StringArgumentType.word())
                                                        .then(argument("horizonHex", StringArgumentType.word())
                                                                .then(argument("cloudHex", StringArgumentType.word())
                                                                        .executes(ctx -> {
                                                                            Identifier biomeId = IdentifierArgumentType.getIdentifier(ctx, "biome");
                                                                            return setProfile(
                                                                                    ctx.getSource(),
                                                                                    biomeId,
                                                                                    StringArgumentType.getString(ctx, "skyHex"),
                                                                                    StringArgumentType.getString(ctx, "fogHex"),
                                                                                    StringArgumentType.getString(ctx, "horizonHex"),
                                                                                    StringArgumentType.getString(ctx, "cloudHex")
                                                                            );
                                                                        })))))))

                .then(literal("set_here")
                        .then(argument("skyHex", StringArgumentType.word())
                                .then(argument("fogHex", StringArgumentType.word())
                                        .then(argument("horizonHex", StringArgumentType.word())
                                                .then(argument("cloudHex", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            Identifier biomeId = getCurrentBiomeId(ctx.getSource());
                                                            if (biomeId == null) return 0;

                                                            return setProfile(
                                                                    ctx.getSource(),
                                                                    biomeId,
                                                                    StringArgumentType.getString(ctx, "skyHex"),
                                                                    StringArgumentType.getString(ctx, "fogHex"),
                                                                    StringArgumentType.getString(ctx, "horizonHex"),
                                                                    StringArgumentType.getString(ctx, "cloudHex")
                                                            );
                                                        }))))))

                .then(literal("get")
                        .then(argument("biome", IdentifierArgumentType.identifier())
                                .executes(ctx -> getProfile(ctx.getSource(), IdentifierArgumentType.getIdentifier(ctx, "biome")))))

                .then(literal("get_here")
                        .executes(ctx -> {
                            Identifier biomeId = getCurrentBiomeId(ctx.getSource());
                            if (biomeId == null) return 0;
                            return getProfile(ctx.getSource(), biomeId);
                        }))

                .then(literal("clear")
                        .then(argument("biome", IdentifierArgumentType.identifier())
                                .executes(ctx -> clearProfile(ctx.getSource(), IdentifierArgumentType.getIdentifier(ctx, "biome")))))

                .then(literal("clear_here")
                        .executes(ctx -> {
                            Identifier biomeId = getCurrentBiomeId(ctx.getSource());
                            if (biomeId == null) return 0;
                            return clearProfile(ctx.getSource(), biomeId);
                        }))

                .then(literal("list")
                        .executes(ctx -> {
                            Map<Identifier, BiomeDayProfile> profiles = BiomeDayProfileState.get(ctx.getSource().getServer()).getProfiles();
                            if (profiles.isEmpty()) {
                                ctx.getSource().sendFeedback(() -> Text.literal("No biome daytime profiles saved."), false);
                                return 1;
                            }

                            ctx.getSource().sendFeedback(() -> Text.literal("Biome daytime profiles:"), false);
                            for (Map.Entry<Identifier, BiomeDayProfile> entry : profiles.entrySet()) {
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal(" - " + entry.getKey() + " -> ").append(entry.getValue().asText()),
                                        false
                                );
                            }
                            return profiles.size();
                        }))
        );
    }

    private static int setProfile(ServerCommandSource source,
                                  Identifier biomeId,
                                  String skyHex,
                                  String fogHex,
                                  String horizonHex,
                                  String cloudHex) {
        Registry<Biome> biomeRegistry = source.getServer().getRegistryManager().get(RegistryKeys.BIOME);
        if (!biomeRegistry.getIds().contains(biomeId)) {
            source.sendError(Text.literal("Unknown biome: " + biomeId));
            return 0;
        }

        int sky;
        int fog;
        int horizon;
        int cloud;

        try {
            sky = BiomeDayProfile.parseHex(skyHex);
            fog = BiomeDayProfile.parseHex(fogHex);
            horizon = BiomeDayProfile.parseHex(horizonHex);
            cloud = BiomeDayProfile.parseHex(cloudHex);
        } catch (IllegalArgumentException ex) {
            source.sendError(Text.literal(ex.getMessage()));
            return 0;
        }

        BiomeDayProfileState state = BiomeDayProfileState.get(source.getServer());
        state.put(biomeId, new BiomeDayProfile(sky, fog, horizon, cloud));
        BiomeDayProfileNetworking.syncAll(source.getServer());

        source.sendFeedback(
                () -> Text.literal("Set daytime profile for " + biomeId + " -> ")
                        .append(new BiomeDayProfile(sky, fog, horizon, cloud).asText()),
                true
        );
        return 1;
    }

    private static int getProfile(ServerCommandSource source, Identifier biomeId) {
        BiomeDayProfile profile = BiomeDayProfileState.get(source.getServer()).get(biomeId);
        if (profile == null) {
            source.sendError(Text.literal("No profile stored for " + biomeId));
            return 0;
        }

        source.sendFeedback(
                () -> Text.literal(biomeId + " -> ").append(profile.asText()),
                false
        );
        return 1;
    }

    private static int clearProfile(ServerCommandSource source, Identifier biomeId) {
        boolean removed = BiomeDayProfileState.get(source.getServer()).remove(biomeId);
        if (!removed) {
            source.sendError(Text.literal("No profile stored for " + biomeId));
            return 0;
        }

        BiomeDayProfileNetworking.syncAll(source.getServer());
        source.sendFeedback(() -> Text.literal("Cleared daytime profile for " + biomeId), true);
        return 1;
    }

    private static Identifier getCurrentBiomeId(ServerCommandSource source) {
        try {
            PlayerEntity player = source.getPlayerOrThrow();
            RegistryEntry<Biome> entry = player.getWorld().getBiome(player.getBlockPos());
            Optional<net.minecraft.registry.RegistryKey<Biome>> key = entry.getKey();
            if (key.isEmpty()) {
                source.sendError(Text.literal("Could not resolve current biome."));
                return null;
            }
            return key.get().getValue();
        } catch (Exception e) {
            source.sendError(Text.literal("You must run this as a player in-game."));
            return null;
        }
    }
}