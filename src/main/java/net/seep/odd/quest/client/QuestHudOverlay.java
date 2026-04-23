package net.seep.odd.quest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.entity.robo_rascal.RoboRascalEntity;
import net.seep.odd.entity.scared_rascal_fight.ScaredRascalFightEntity;
import net.seep.odd.quest.QuestDefinition;
import net.seep.odd.quest.QuestObjectiveData;

import java.util.Locale;

public final class QuestHudOverlay {
    private QuestHudOverlay() {
    }

    private static final Identifier QUEST_GUI_TEXTURE =
            new Identifier("odd", "textures/gui/quest_gui.png");

    private static final int TEX_W = 1024;
    private static final int TEX_H = 510;
    private static final int BOSS_BAR_W = 182;
    private static final int VANILLA_BOSS_BAR_Y = 12;
    private static final int BOSS_BAR_OFFSET_Y = 26;
    private static final float HEADER_SCALE = 0.078125f;

    public static void init() {
        HudRenderCallback.EVENT.register(QuestHudOverlay::render);
    }

    public static int getBossBarYOffset() {
        return BOSS_BAR_OFFSET_Y;
    }

    private static int getBossBarY() {
        return VANILLA_BOSS_BAR_Y + BOSS_BAR_OFFSET_Y;
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        QuestClientState state = QuestClientState.INSTANCE;
        if (client.player == null || client.options.hudHidden || client.options.debugEnabled || !state.hasActiveQuest()) {
            return;
        }

        QuestDefinition quest = state.activeQuestDefinition();
        renderQuestHeader(context);
        if (quest != null) {
            renderTrackerText(context, client, state, quest);
            renderCompassMarker(context, client, state, quest);
            renderScaredRascal2HealthBars(context, client, state, quest);
        }
        renderAbandonHint(context, client);
    }

    private static void renderQuestHeader(DrawContext context) {
        int screenWidth = context.getScaledWindowWidth();
        int headerW = Math.round(TEX_W * HEADER_SCALE);
        int headerH = Math.round(TEX_H * HEADER_SCALE);
        int bossBarX = (screenWidth - BOSS_BAR_W) / 2;
        int bossBarY = getBossBarY();
        int headerX = bossBarX + (BOSS_BAR_W - headerW) / 2;
        int headerY = Math.max(0, bossBarY - headerH - 8);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(headerX, headerY, 200.0F);
        matrices.scale(HEADER_SCALE, HEADER_SCALE, 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        context.drawTexture(QUEST_GUI_TEXTURE, 0, 0, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void renderTrackerText(DrawContext context, MinecraftClient client,
                                          QuestClientState state, QuestDefinition quest) {
        int screenWidth = context.getScaledWindowWidth();
        int bossBarY = getBossBarY();
        int progress = state.progressFor(quest);
        int goal = Math.max(1, quest.goal());

        String line1 = buildProgressText(quest, progress, goal);
        String line2 = state.objectiveHint().isBlank() ? buildNeededText(state, quest, progress, goal) : state.objectiveHint();

        int textY1 = bossBarY + 12;
        int textY2 = textY1 + 11;

        context.drawText(client.textRenderer, Text.literal(line1),
                (screenWidth - client.textRenderer.getWidth(line1)) / 2, textY1, 0xFFF2CF59, true);
        context.drawText(client.textRenderer, Text.literal(line2),
                (screenWidth - client.textRenderer.getWidth(line2)) / 2, textY2, 0xFFFFFFFF, true);
    }

    private static void renderCompassMarker(DrawContext context, MinecraftClient client,
                                            QuestClientState state, QuestDefinition quest) {
        if (client.player == null || quest == null) return;

        Vec3d target = resolveCompassTarget(client, state, quest);
        if (target == null) return;

        double dx = target.x - client.player.getX();
        double dz = target.z - client.player.getZ();
        if ((dx * dx + dz * dz) < 4.0D) return;

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float delta = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());
        float normalized = MathHelper.clamp(delta / 90.0F, -1.0F, 1.0F);

        int screenWidth = context.getScaledWindowWidth();
        int bossBarX = (screenWidth - BOSS_BAR_W) / 2;
        int bossBarY = getBossBarY();
        int markerX = bossBarX + (BOSS_BAR_W / 2) + Math.round(normalized * ((BOSS_BAR_W / 2) - 9));
        drawCompassMarkerOnBar(context, markerX, bossBarY + 1);
    }

    private static Vec3d resolveCompassTarget(MinecraftClient client, QuestClientState state, QuestDefinition quest) {
        Entity tracked = resolvePreferredTrackedEntity(client, state, quest);
        if (tracked != null) {
            return tracked.getPos().add(0.0D, tracked.getHeight() * 0.35D, 0.0D);
        }

        if (state.activeStage() != QuestClientState.ActiveStage.TRAVEL
                && state.activeStage() != QuestClientState.ActiveStage.RETURN) {
            return null;
        }

        BlockPos target = state.activeTarget();
        if (target == null) return null;
        return Vec3d.ofCenter(target);
    }

    private static Entity resolvePreferredTrackedEntity(MinecraftClient client, QuestClientState state, QuestDefinition quest) {
        if (client.world == null) return null;

        Entity fallback = null;
        for (int entityId : state.trackedEntityIds()) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            if (quest.objective.type == QuestObjectiveData.Type.FIND_HIM) {
                return entity;
            }

            if (quest.objective.type == QuestObjectiveData.Type.SCARED_RASCAL) {
                if (entity instanceof net.seep.odd.entity.scared_rascal.ScaredRascalEntity) {
                    return entity;
                }
                if (fallback == null) {
                    fallback = entity;
                }
                continue;
            }

            if (quest.objective.type == QuestObjectiveData.Type.SCARED_RASCAL_2) {
                if (entity instanceof ScaredRascalFightEntity) {
                    return entity;
                }
                if (fallback == null) {
                    fallback = entity;
                }
            }
        }
        return fallback;
    }

    private static void renderScaredRascal2HealthBars(DrawContext context, MinecraftClient client,
                                                      QuestClientState state, QuestDefinition quest) {
        if (quest.objective.type != QuestObjectiveData.Type.SCARED_RASCAL_2 || client.world == null) {
            return;
        }

        RoboRascalEntity robo = null;
        ScaredRascalFightEntity rascal = null;
        for (int entityId : state.trackedEntityIds()) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity instanceof RoboRascalEntity roboRascal && !roboRascal.isRemoved()) {
                robo = roboRascal;
            } else if (entity instanceof ScaredRascalFightEntity fightRascal && !fightRascal.isRemoved()) {
                rascal = fightRascal;
            }
        }

        if (robo == null && rascal == null) {
            return;
        }

        int x = context.getScaledWindowWidth() - 126;
        int y = 40;
        if (robo != null) {
            y = drawHealthPanel(context, client, x, y, "Robo Rascal", robo, 0xFFB84D4D, 0xFF6B1F1F);
        }
        if (rascal != null) {
            drawHealthPanel(context, client, x, y + 6, "Scared Rascal", rascal, 0xFFE3C26B, 0xFF6D5724);
        }
    }

    private static int drawHealthPanel(DrawContext context, MinecraftClient client, int x, int y,
                                       String label, LivingEntity entity, int fillColor, int borderColor) {
        int width = 114;
        int height = 22;
        int barX = x + 6;
        int barY = y + 12;
        int barW = width - 12;
        int barH = 6;

        float maxHealth = Math.max(1.0F, entity.getMaxHealth());
        float health = MathHelper.clamp(entity.getHealth(), 0.0F, maxHealth);
        int filled = Math.round((health / maxHealth) * barW);

        context.fill(x, y, x + width, y + height, 0x88000000);
        context.drawBorder(x, y, width, height, 0x66FFFFFF);
        context.drawText(client.textRenderer, Text.literal(label), x + 6, y + 3, 0xFFFFFF, false);

        context.fill(barX, barY, barX + barW, barY + barH, 0x55000000);
        context.fill(barX, barY, barX + filled, barY + barH, fillColor);
        context.drawBorder(barX - 1, barY - 1, barW + 2, barH + 2, borderColor);

        String hp = Math.round(health) + "/" + Math.round(maxHealth);
        context.drawText(client.textRenderer, Text.literal(hp), x + width - client.textRenderer.getWidth(hp) - 6, y + 3, 0xFFD9D9D9, false);
        return y + height;
    }

    private static void drawCompassMarkerOnBar(DrawContext context, int centerX, int y) {
        context.fill(centerX - 2, y, centerX + 3, y + 5, 0xAA4B2D00);
        context.fill(centerX - 1, y + 1, centerX + 2, y + 4, 0xFFFFD85A);
        context.fill(centerX, y, centerX + 1, y + 5, 0xFFFFFFB0);
    }

    private static void renderAbandonHint(DrawContext context, MinecraftClient client) {
        String keyName = QuestKeybinds.ABANDON_QUEST.getBoundKeyLocalizedText().getString();
        Text text = Text.literal("Abandon Quest [" + keyName + "]");
        int width = client.textRenderer.getWidth(text) + 10;
        int x = 8;
        int y = context.getScaledWindowHeight() - 24;
        context.fill(x, y - 2, x + width, y + 10, 0x88000000);
        context.drawBorder(x, y - 2, width, 12, 0x66FFFFFF);
        context.drawText(client.textRenderer, text, x + 5, y, 0xFFFFFF, false);
    }

    private static String buildProgressText(QuestDefinition quest, int progress, int goal) {
        if (quest.objective.type == QuestObjectiveData.Type.KILL) {
            String plural = pluralize(lowerName(quest.objective.entity), goal);
            return progress + "/" + goal + " " + plural + " killed";
        }
        if (quest.objective.type == QuestObjectiveData.Type.CALM_RETURN) {
            String plural = pluralize(lowerName(quest.objective.entity), goal);
            return progress + "/" + goal + " " + plural + " returned";
        }
        if (quest.objective.type == QuestObjectiveData.Type.RACE_RETURN) {
            return progress + "/" + goal + " races won";
        }
        if (quest.objective.type == QuestObjectiveData.Type.FIND_HIM) {
            return progress + "/" + goal + " mazes cleared";
        }
        if (quest.objective.type == QuestObjectiveData.Type.SCARED_RASCAL) {
            return progress + "/" + goal + " steps cleared";
        }
        if (quest.objective.type == QuestObjectiveData.Type.SCARED_RASCAL_2) {
            return progress + "/" + goal + " steps cleared";
        }
        return progress + "/" + goal + " complete";
    }

    private static String buildNeededText(QuestClientState state, QuestDefinition quest, int progress, int goal) {
        if (state.canClaim(quest) || progress >= goal) {
            return "Return to the librarian to claim your reward";
        }
        int remaining = Math.max(0, goal - progress);
        if (quest.objective.type == QuestObjectiveData.Type.KILL) {
            return "Kill " + remaining + " more " + pluralize(lowerName(quest.objective.entity), remaining);
        }
        if (quest.objective.type == QuestObjectiveData.Type.CALM_RETURN) {
            if (state.activeStage() == QuestClientState.ActiveStage.TRAVEL) return "Go to the marked location";
            if (state.activeStage() == QuestClientState.ActiveStage.RETURN) return "Bring them back to the librarian";
            return "Bring " + remaining + " more calmed " + pluralize(lowerName(quest.objective.entity), remaining) + " back";
        }
        if (quest.objective.type == QuestObjectiveData.Type.RACE_RETURN) {
            if (state.activeStage() == QuestClientState.ActiveStage.TRAVEL) return "Go to the marked location";
            return "Beat the rascal back to the librarian";
        }
        if (quest.objective.type == QuestObjectiveData.Type.FIND_HIM) {
            return "Track Him through the mazes";
        }
        if (quest.objective.type == QuestObjectiveData.Type.SCARED_RASCAL) {
            if (state.activeStage() == QuestClientState.ActiveStage.TRAVEL) return "Go to the marked location";
            if (state.activeStage() == QuestClientState.ActiveStage.RETURN) return "Escort the scared rascal back to the librarian";
            return "Help the scared rascal survive the chase";
        }
        if (quest.objective.type == QuestObjectiveData.Type.SCARED_RASCAL_2) {
            if (state.activeStage() == QuestClientState.ActiveStage.TRAVEL) return "Go to the marked location";
            if (state.activeStage() == QuestClientState.ActiveStage.RETURN) return "Escort the scared rascal back to the librarian";
            return "Defeat the Robo Rascal before it kills the scared rascal";
        }
        return remaining + " remaining";
    }

    private static String lowerName(String entityId) {
        String path = entityId;
        int colon = path.indexOf(':');
        if (colon >= 0 && colon + 1 < path.length()) path = path.substring(colon + 1);
        return path.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String pluralize(String text, int amount) {
        if (amount == 1) return text;
        if (text.endsWith("s")) return text;
        return text + "s";
    }
}
