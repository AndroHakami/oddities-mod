package net.seep.odd.shop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.minecraft.util.math.MathHelper;
import net.seep.odd.shop.catalog.ShopCatalogManager;
import net.seep.odd.shop.catalog.ShopEntry;

public final class ShopCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ShopCommands::registerImpl);
    }

    private static void registerImpl(CommandDispatcher<ServerCommandSource> d,
                                     CommandRegistryAccess access,
                                     CommandManager.RegistrationEnvironment env) {

        d.register(CommandManager.literal("oddshop")
                        .requires(src -> src.hasPermissionLevel(2))

                        /* ---------------- Core ---------------- */

                        .then(CommandManager.literal("reload")
                                .executes(ctx -> {
                                    ShopCatalogManager.reload();
                                    ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                    ctx.getSource().sendFeedback(() -> Text.literal("Reloaded dabloons shop catalog."), true);
                                    return 1;
                                }))

                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    int count = ShopCatalogManager.entries().size();
                                    ctx.getSource().sendFeedback(() -> Text.literal("Entries: " + count), false);
                                    ShopCatalogManager.entries().forEach(e ->
                                            ctx.getSource().sendFeedback(() -> Text.literal("- " + e.id + " (" + e.price + ")"), false)
                                    );
                                    return 1;
                                }))

                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            boolean ok = ShopCatalogManager.remove(id);
                                            ShopCatalogManager.save();
                                            ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                            ctx.getSource().sendFeedback(() -> Text.literal(ok ? "Removed: " + id : "No such entry: " + id), true);
                                            return ok ? 1 : 0;
                                        })))

                        .then(CommandManager.literal("setprice")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .then(CommandManager.argument("price", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    int price = IntegerArgumentType.getInteger(ctx, "price");
                                                    ShopEntry e = ShopCatalogManager.get(id);
                                                    if (e == null) {
                                                        ctx.getSource().sendError(Text.literal("No such entry: " + id));
                                                        return 0;
                                                    }
                                                    e.price = price;
                                                    ShopCatalogManager.upsert(e);
                                                    ShopCatalogManager.save();
                                                    ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Set price of " + id + " to " + price), true);
                                                    return 1;
                                                }))))


                        /* ---------------- Add / Edit entries ---------------- */

                        // /oddshop additem <id> <item> <price>
                        // Adds/updates an entry that previews the item and gives that same item.
                        .then(CommandManager.literal("additem")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(access))
                                                .then(CommandManager.argument("price", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> {
                                                            String id = StringArgumentType.getString(ctx, "id");
                                                            var stack = ItemStackArgumentType.getItemStackArgument(ctx, "item").createStack(1, false);
                                                            int price = IntegerArgumentType.getInteger(ctx, "price");

                                                            ShopEntry e = new ShopEntry();
                                                            e.id = id;
                                                            e.displayName = stack.getName().getString();
                                                            e.price = price;

                                                            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                                                            e.giveItemId = itemId;
                                                            e.giveCount = 1;

                                                            e.previewType = ShopEntry.PreviewType.ITEM;
                                                            e.previewItemId = itemId;
                                                            e.previewEntityType = "";

                                                            ShopCatalogManager.upsert(e);
                                                            ShopCatalogManager.save();
                                                            ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                                            ctx.getSource().sendFeedback(() -> Text.literal("Added/updated entry: " + id), true);
                                                            return 1;
                                                        })))))

                        // /oddshop settitle <id> <title...>
                        .then(CommandManager.literal("settitle")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .then(CommandManager.argument("title", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "id");
                                                    String title = StringArgumentType.getString(ctx, "title");

                                                    ShopEntry e = ShopCatalogManager.get(id);
                                                    if (e == null) {
                                                        ctx.getSource().sendError(Text.literal("No such entry: " + id));
                                                        return 0;
                                                    }

                                                    e.displayName = title;
                                                    ShopCatalogManager.upsert(e);
                                                    ShopCatalogManager.save();
                                                    ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Set title of " + id + " to: " + title), true);
                                                    return 1;
                                                }))))

                // /oddshop setgive <id> <itemId> [count]
                // Changes what you actually receive (independent of preview).
                .then(CommandManager.literal("setgive")
                        .then(CommandManager.argument("id", StringArgumentType.string())
                                .then(CommandManager.argument("item", IdentifierArgumentType.identifier())
                                        .executes(ctx -> {
                                            return setGive(ctx, StringArgumentType.getString(ctx, "id"),
                                                    IdentifierArgumentType.getIdentifier(ctx, "item"), 1);
                                        })
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> {
                                                    return setGive(ctx, StringArgumentType.getString(ctx, "id"),
                                                            IdentifierArgumentType.getIdentifier(ctx, "item"),
                                                            IntegerArgumentType.getInteger(ctx, "count"));
                                                })))))

                // /oddshop setpreview <id> item <itemId>
                // /oddshop setpreview <id> entity <entityId>
                .then(CommandManager.literal("setpreview")
                        .then(CommandManager.argument("id", StringArgumentType.string())

                                .then(CommandManager.literal("item")
                                        .then(CommandManager.argument("id2", IdentifierArgumentType.identifier())
                                                .executes(ctx -> {
                                                    String entryId = StringArgumentType.getString(ctx, "id");
                                                    Identifier itemId = IdentifierArgumentType.getIdentifier(ctx, "id2");

                                                    if (!Registries.ITEM.containsId(itemId)) {
                                                        ctx.getSource().sendError(Text.literal("Unknown item: " + itemId));
                                                        return 0;
                                                    }

                                                    ShopEntry e = ShopCatalogManager.get(entryId);
                                                    if (e == null) {
                                                        ctx.getSource().sendError(Text.literal("No such entry: " + entryId));
                                                        return 0;
                                                    }

                                                    e.previewType = ShopEntry.PreviewType.ITEM;
                                                    e.previewItemId = itemId.toString();
                                                    e.previewEntityType = "";

                                                    ShopCatalogManager.upsert(e);
                                                    ShopCatalogManager.save();
                                                    ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Set preview of " + entryId + " to item: " + itemId), true);
                                                    return 1;
                                                })))

                                .then(CommandManager.literal("entity")
                                        .then(CommandManager.argument("id2", IdentifierArgumentType.identifier())
                                                .executes(ctx -> {
                                                    String entryId = StringArgumentType.getString(ctx, "id");
                                                    Identifier entityId = IdentifierArgumentType.getIdentifier(ctx, "id2");

                                                    if (!Registries.ENTITY_TYPE.containsId(entityId)) {
                                                        ctx.getSource().sendError(Text.literal("Unknown entity: " + entityId));
                                                        return 0;
                                                    }

                                                    ShopEntry e = ShopCatalogManager.get(entryId);
                                                    if (e == null) {
                                                        ctx.getSource().sendError(Text.literal("No such entry: " + entryId));
                                                        return 0;
                                                    }

                                                    e.previewType = ShopEntry.PreviewType.ENTITY;
                                                    e.previewEntityType = entityId.toString();
                                                    e.previewItemId = "";

                                                    ShopCatalogManager.upsert(e);
                                                    ShopCatalogManager.save();
                                                    ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Set preview of " + entryId + " to entity: " + entityId), true);
                                                    return 1;
                                                }))))

        ));
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

        e.giveItemId = itemId.toString();
        e.giveCount = MathHelper.clamp(count, 1, 64);

        ShopCatalogManager.upsert(e);
        ShopCatalogManager.save();
        ShopCatalogManager.broadcastToOpenShops(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.literal("Set give item of " + entryId + " to " + itemId + " x" + e.giveCount), true);
        return 1;
    }

    private ShopCommands() {}
}
