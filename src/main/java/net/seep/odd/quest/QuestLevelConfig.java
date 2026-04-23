
package net.seep.odd.quest;

import java.util.ArrayList;
import java.util.List;

public final class QuestLevelConfig {
    public List<Integer> levels = new ArrayList<>(List.of(0, 1, 3, 6, 10));

    public int currentLevel(int xp) {
        int level = 0;
        for (int i = 0; i < levels.size(); i++) {
            if (xp >= levels.get(i)) {
                level = i;
            }
        }
        return level;
    }

    public int currentFloorXp(int xp) {
        return levels.get(currentLevel(xp));
    }

    public int nextCeilXp(int xp) {
        int next = currentLevel(xp) + 1;
        if (next >= levels.size()) {
            return levels.get(levels.size() - 1);
        }
        return levels.get(next);
    }
}
