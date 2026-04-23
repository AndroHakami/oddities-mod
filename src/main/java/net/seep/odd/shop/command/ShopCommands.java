package net.seep.odd.shop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.shop.catalog.ShopCatalogManager;
import net.seep.odd.shop.catalog.ShopEntry;

public final class ShopCommands {

    private ShopCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ShopCommands::registerImpl);
    }

    private static void registerImpl(CommandDispatcher<ServerCommandSource> d,
                                     CommandRegistryAccess access,
                                     CommandManager.RegistrationEnvironment env) {

        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("oddshop")
                .requires(src -> src.hasPermissionLevel(2));

        root.then(CommandManager.literal("reload")
                .executes(ctx -> {
                    ShopCatalogManager.reload();
                    ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.literal("Reloaded dabloons shop catalog."), true);
                    return 1;
                }));

        root.then(CommandManager.literal("list")
                .executes(ctx -> {
                    int count = ShopCatalogManager.entries().size();
                    ctx.getSource().sendFeedback(() -> Text.literal("Entries: " + count), false);
                    ShopCatalogManager.entries().forEach(e ->
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "- " + e.id +
                                            " | category=" + e.category.name().toLowerCase() +
                                            " | price=" + e.price +
                                            " | sort=" + e.sortOrder +
                                            " | pet=" + e.pet +
                                            " | grant=" + e.grantType.name().toLowerCase() +
                                            " | preview=" + e.previewType.name().toLowerCase() +
                                            " | armour_slot=" + (e.previewArmourSlot == null ? "auto" : e.previewArmourSlot.name().toLowerCase())
                            ), false)
                    );
                    return 1;
                }));

        root.then(CommandManager.literal("remove")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            boolean ok = ShopCatalogManager.remove(id);
                            ShopCatalogManager.save();
                            ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal(ok ? "Removed: " + id : "No such entry: " + id), true);
                            return ok ? 1 : 0;
                        })));

        root.then(CommandManager.literal("setprice")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("price", IntegerArgumentType.integer(0))
                                .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                    e.price = IntegerArgumentType.getInteger(ctx, "price");
                                    return "Set price of " + e.id + " to " + e.price;
                                })))));

        root.then(CommandManager.literal("settitle")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("title", StringArgumentType.greedyString())
                                .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                    e.displayName = StringArgumentType.getString(ctx, "title");
                                    return "Set title of " + e.id + " to: " + e.displayName;
                                })))));

        root.then(CommandManager.literal("setdesc")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("description", StringArgumentType.greedyString())
                                .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                    e.description = StringArgumentType.getString(ctx, "description");
                                    return "Set description of " + e.id;
                                })))));

        root.then(CommandManager.literal("cleardesc")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                            e.description = "";
                            return "Cleared description of " + e.id;
                        }))));

        root.then(CommandManager.literal("setcategory")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("category", StringArgumentType.word())
                                .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                    e.category = parseCategory(StringArgumentType.getString(ctx, "category"));
                                    return "Set category of " + e.id + " to " + e.category.name().toLowerCase();
                                })))));

        root.then(CommandManager.literal("setsort")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("sort", IntegerArgumentType.integer())
                                .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                    e.sortOrder = IntegerArgumentType.getInteger(ctx, "sort");
                                    return "Set sort order of " + e.id + " to " + e.sortOrder;
                                })))));

        root.then(CommandManager.literal("setpet")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                    e.pet = BoolArgumentType.getBool(ctx, "value");
                                    return "Set pet flag of " + e.id + " to " + e.pet;
                                })))));

        root.then(CommandManager.literal("additem")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(access))
                                .then(CommandManager.argument("price", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            ItemStack stack = ItemStackArgumentType.getItemStackArgument(ctx, "item").createStack(1, false);
                                            int price = IntegerArgumentType.getInteger(ctx, "price");

                                            ShopEntry e = new ShopEntry();
                                            e.id = id;
                                            e.displayName = stack.getName().getString();
                                            e.price = price;
                                            e.description = "";
                                            e.category = ShopEntry.Category.MISC;
                                            e.giveItemId = Registries.ITEM.getId(stack.getItem()).toString();
                                            e.giveCount = 1;
                                            e.previewType = ShopEntry.PreviewType.ITEM;
                                            e.previewItemId = e.giveItemId;
                                            e.previewEntityType = "";
                                            e.previewArmourSlot = ShopEntry.PreviewArmourSlot.AUTO;

                                            ShopCatalogManager.upsert(e);
                                            ShopCatalogManager.save();
                                            ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                            ctx.getSource().sendFeedback(() -> Text.literal("Added/updated item entry: " + id + " (default category: misc)"), true);
                                            return 1;
                                        })))));

        root.then(CommandManager.literal("addpet")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("spawn_egg", IdentifierArgumentType.identifier())
                                .then(CommandManager.argument("preview_entity", IdentifierArgumentType.identifier())
                                        .then(CommandManager.argument("price", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    Identifier eggId = IdentifierArgumentType.getIdentifier(ctx, "spawn_egg");
                                                    Identifier previewEntityId = IdentifierArgumentType.getIdentifier(ctx, "preview_entity");

                                                    if (!Registries.ITEM.containsId(eggId)) {
                                                        ctx.getSource().sendError(Text.literal("Unknown item: " + eggId));
                                                        return 0;
                                                    }
                                                    if (!Registries.ENTITY_TYPE.containsId(previewEntityId)) {
                                                        ctx.getSource().sendError(Text.literal("Unknown entity: " + previewEntityId));
                                                        return 0;
                                                    }

                                                    ShopEntry e = new ShopEntry();
                                                    e.id = StringArgumentType.getString(ctx, "id");
                                                    e.displayName = Registries.ENTITY_TYPE.get(previewEntityId).getName().getString();
                                                    e.price = IntegerArgumentType.getInteger(ctx, "price");
                                                    e.description = "";
                                                    e.category = ShopEntry.Category.PETS;
                                                    e.pet = true;
                                                    e.giveItemId = eggId.toString();
                                                    e.giveCount = 1;
                                                    e.previewType = ShopEntry.PreviewType.ENTITY;
                                                    e.previewEntityType = previewEntityId.toString();
                                                    e.previewItemId = "";
                                                    e.previewArmourSlot = ShopEntry.PreviewArmourSlot.AUTO;

                                                    ShopCatalogManager.upsert(e);
                                                    ShopCatalogManager.save();
                                                    ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Added/updated pet entry: " + e.displayName), true);
                                                    return 1;
                                                }))))));

        root.then(CommandManager.literal("setgive")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("item", IdentifierArgumentType.identifier())
                                .executes(ctx -> setGive(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "item"), 1))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> setGive(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count")))))));

        root.then(CommandManager.literal("setgrant")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.literal("item")
                                .then(CommandManager.argument("item", IdentifierArgumentType.identifier())
                                        .executes(ctx -> setGive(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "item"), 1))
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> setGive(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(CommandManager.literal("command")
                                .then(CommandManager.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                            e.grantType = ShopEntry.GrantType.COMMAND;
                                            e.grantCommand = StringArgumentType.getString(ctx, "command");
                                            return "Set grant command for " + e.id;
                                        }))))));

        root.then(CommandManager.literal("setarmourslot")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.argument("slot", StringArgumentType.word())
                                .executes(ctx -> withEntry(ctx, StringArgumentType.getString(ctx, "id"), e -> {
                                    e.previewArmourSlot = parseArmourSlot(StringArgumentType.getString(ctx, "slot"));
                                    return "Set armour preview slot of " + e.id + " to " + e.previewArmourSlot.name().toLowerCase();
                                })))));

        root.then(CommandManager.literal("setpreview")
                .then(CommandManager.argument("id", StringArgumentType.string())
                        .then(CommandManager.literal("item")
                                .then(CommandManager.argument("preview", IdentifierArgumentType.identifier())
                                        .executes(ctx -> setItemPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview")))))
                        .then(CommandManager.literal("entity")
                                .then(CommandManager.argument("preview", IdentifierArgumentType.identifier())
                                        .executes(ctx -> setEntityPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview")))))
                        .then(CommandManager.literal("armour")
                                .then(CommandManager.argument("preview", IdentifierArgumentType.identifier())
                                        .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), null))
                                        .then(CommandManager.argument("slot", StringArgumentType.word())
                                                .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), StringArgumentType.getString(ctx, "slot"))))))
                        .then(CommandManager.literal("armours")
                                .then(CommandManager.argument("preview", IdentifierArgumentType.identifier())
                                        .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), null))
                                        .then(CommandManager.argument("slot", StringArgumentType.word())
                                                .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), StringArgumentType.getString(ctx, "slot"))))))
                        .then(CommandManager.literal("armor")
                                .then(CommandManager.argument("preview", IdentifierArgumentType.identifier())
                                        .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), null))
                                        .then(CommandManager.argument("slot", StringArgumentType.word())
                                                .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), StringArgumentType.getString(ctx, "slot"))))))
                        .then(CommandManager.literal("armors")
                                .then(CommandManager.argument("preview", IdentifierArgumentType.identifier())
                                        .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), null))
                                        .then(CommandManager.argument("slot", StringArgumentType.word())
                                                .executes(ctx -> setArmourPreview(ctx, StringArgumentType.getString(ctx, "id"), IdentifierArgumentType.getIdentifier(ctx, "preview"), StringArgumentType.getString(ctx, "slot"))))))));

        d.register(root);
    }

    private static int setGive(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                               String entryId, Identifier itemId, int count) {
        if (!Registries.ITEM.containsId(itemId)) {
            ctx.getSource().sendError(Text.literal("Unknown item: " + itemId));
            return 0;
        }

        ShopEntry e = ShopCatalogManager.get(entryId);
        if (e == null) {
            ctx.getSource().sendError(Text.literal("No such entry: " + entryId));
            return 0;
        }

        e.grantType = ShopEntry.GrantType.ITEM;
        e.giveItemId = itemId.toString();
        e.giveCount = MathHelper.clamp(count, 1, 64);

        ShopCatalogManager.upsert(e);
        ShopCatalogManager.save();
        ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.literal("Set grant item of " + entryId + " to " + itemId + " x" + e.giveCount), true);
        return 1;
    }

    private static int setItemPreview(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                      String entryId, Identifier itemId) {
        return withEntry(ctx, entryId, e -> {
            if (!Registries.ITEM.containsId(itemId)) {
                throw new IllegalArgumentException("Unknown item: " + itemId);
            }
            e.previewType = ShopEntry.PreviewType.ITEM;
            e.previewItemId = itemId.toString();
            e.previewEntityType = "";
            e.previewArmourSlot = ShopEntry.PreviewArmourSlot.AUTO;
            return "Set preview of " + e.id + " to item: " + itemId;
        });
    }

    private static int setEntityPreview(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                        String entryId, Identifier entityId) {
        return withEntry(ctx, entryId, e -> {
            if (!Registries.ENTITY_TYPE.containsId(entityId)) {
                throw new IllegalArgumentException("Unknown entity: " + entityId);
            }
            e.previewType = ShopEntry.PreviewType.ENTITY;
            e.previewEntityType = entityId.toString();
            e.previewItemId = "";
            e.previewArmourSlot = ShopEntry.PreviewArmourSlot.AUTO;
            return "Set preview of " + e.id + " to entity: " + entityId;
        });
    }

    private static int setArmourPreview(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                        String entryId, Identifier itemId, String rawSlot) {
        return withEntry(ctx, entryId, e -> {
            if (!Registries.ITEM.containsId(itemId)) {
                throw new IllegalArgumentException("Unknown item: " + itemId);
            }
            if (!(Registries.ITEM.get(itemId) instanceof ArmorItem armorItem)) {
                throw new IllegalArgumentException("Preview item must be an armor item: " + itemId);
            }

            e.previewType = ShopEntry.PreviewType.ARMOUR;
            e.previewItemId = itemId.toString();
            e.previewEntityType = "";
            e.previewArmourSlot = (rawSlot == null || rawSlot.isBlank()) ? fromArmorItem(armorItem) : parseArmourSlot(rawSlot);
            return "Set preview of " + e.id + " to armour: " + itemId + " (" + e.previewArmourSlot.name().toLowerCase() + ")";
        });
    }

    private static ShopEntry.PreviewArmourSlot fromArmorItem(ArmorItem armorItem) {
        return switch (armorItem.getSlotType()) {
            case HEAD -> ShopEntry.PreviewArmourSlot.HEAD;
            case CHEST -> ShopEntry.PreviewArmourSlot.CHEST;
            case LEGS -> ShopEntry.PreviewArmourSlot.LEGS;
            case FEET -> ShopEntry.PreviewArmourSlot.FEET;
            default -> ShopEntry.PreviewArmourSlot.AUTO;
        };
    }

    private static ShopEntry.PreviewArmourSlot parseArmourSlot(String raw) {
        return switch (raw.toLowerCase()) {
            case "auto", "default" -> ShopEntry.PreviewArmourSlot.AUTO;
            case "head", "helmet", "hat" -> ShopEntry.PreviewArmourSlot.HEAD;
            case "chest", "chestplate", "body", "torso" -> ShopEntry.PreviewArmourSlot.CHEST;
            case "legs", "leggings", "pants" -> ShopEntry.PreviewArmourSlot.LEGS;
            case "feet", "boots", "shoes" -> ShopEntry.PreviewArmourSlot.FEET;
            default -> throw new IllegalArgumentException("Unknown armour slot: " + raw + " (use auto, head, chest, legs, feet)");
        };
    }

    private interface EntryMutation {
        String mutate(ShopEntry entry) throws Exception;
    }

    private static int withEntry(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                 String id,
                                 EntryMutation mutation) {
        ShopEntry e = ShopCatalogManager.get(id);
        if (e == null) {
            ctx.getSource().sendError(Text.literal("No such entry: " + id));
            return 0;
        }

        try {
            String message = mutation.mutate(e);
            ShopCatalogManager.upsert(e);
            ShopCatalogManager.save();
            ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
            ctx.getSource().sendFeedback(() -> Text.literal(message), true);
            return 1;
        } catch (IllegalArgumentException ex) {
            ctx.getSource().sendError(Text.literal(ex.getMessage()));
            return 0;
        } catch (Exception ex) {
            ctx.getSource().sendError(Text.literal("Failed to edit shop entry: " + ex.getMessage()));
            return 0;
        }
    }

    private static ShopEntry.Category parseCategory(String raw) {
        return switch (raw.toLowerCase()) {
            case "weapon", "weapons" -> ShopEntry.Category.WEAPONS;
            case "pet", "pets" -> ShopEntry.Category.PETS;
            case "style", "styles" -> ShopEntry.Category.STYLES;
            case "misc", "other", "others" -> ShopEntry.Category.MISC;
            default -> throw new IllegalArgumentException("Unknown category: " + raw + " (use weapons, pets, styles, misc)");
        };
    }
}
