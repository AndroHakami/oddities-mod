
package net.seep.odd.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class QuestRegistry implements SimpleSynchronousResourceReloadListener {
    public static final Identifier FABRIC_ID = new Identifier("odd", "quest_registry_loader");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, QuestDefinition> QUESTS = new LinkedHashMap<>();

    @Override
    public Identifier getFabricId() {
        return FABRIC_ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<String, QuestDefinition> loaded = new HashMap<>();

        for (Map.Entry<Identifier, Resource> entry : manager.findResources("quests", path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier fileId = entry.getKey();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8))) {
                QuestDefinition def = GSON.fromJson(reader, QuestDefinition.class);
                if (def == null) {
                    continue;
                }

                if (def.id == null || def.id.isBlank()) {
                    String path = fileId.getPath();
                    int slash = path.lastIndexOf('/');
                    int dot = path.lastIndexOf('.');
                    def.id = path.substring(slash + 1, dot);
                }

                loaded.put(def.id, def);
            } catch (Exception e) {
                System.err.println("[Oddities] Failed to load quest file " + fileId + ": " + e.getMessage());
            }
        }

        List<QuestDefinition> sorted = new ArrayList<>(loaded.values());
        sorted.sort(QuestDefinition.SORTER);

        QUESTS.clear();
        for (QuestDefinition def : sorted) {
            QUESTS.put(def.id, def);
        }
    }

    public static Collection<QuestDefinition> all() {
        return Collections.unmodifiableCollection(QUESTS.values());
    }

    public static List<QuestDefinition> sortedList() {
        return new ArrayList<>(QUESTS.values());
    }

    public static QuestDefinition get(String id) {
        return QUESTS.get(id);
    }

    public static ResourceType resourceType() {
        return ResourceType.SERVER_DATA;
    }
}
