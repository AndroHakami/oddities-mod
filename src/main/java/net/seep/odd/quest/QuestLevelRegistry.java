
package net.seep.odd.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class QuestLevelRegistry implements SimpleSynchronousResourceReloadListener {
    public static final Identifier FABRIC_ID = new Identifier("odd", "quest_levels_loader");
    private static final Identifier LEVELS_FILE = new Identifier("odd", "quest_levels.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static QuestLevelConfig CONFIG = new QuestLevelConfig();

    @Override
    public Identifier getFabricId() {
        return FABRIC_ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        QuestLevelConfig loaded = new QuestLevelConfig();

        try {
            Optional<Resource> resource = manager.getResource(LEVELS_FILE);
            if (resource.isPresent()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8))) {
                    QuestLevelConfig parsed = GSON.fromJson(reader, QuestLevelConfig.class);
                    if (parsed != null && parsed.levels != null && !parsed.levels.isEmpty()) {
                        loaded = parsed;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Oddities] Failed to load quest level config: " + e.getMessage());
        }

        CONFIG = loaded;
    }

    public static QuestLevelConfig get() {
        return CONFIG;
    }
}
