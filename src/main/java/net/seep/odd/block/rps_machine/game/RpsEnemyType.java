package net.seep.odd.block.rps_machine.game;

import net.minecraft.util.math.random.Random;

public enum RpsEnemyType {
    TRAINING_BOT("Training Bot", 14, 3, 1, 1, 1),
    CAVE_KNIGHT("Cave Knight", 18, 4, 3, 1, 1),
    MOSS_WIZARD("Moss Wizard", 12, 5, 1, 3, 2),
    FROG_PRINCE("Frog Prince", 16, 4, 2, 1, 3);

    private final String displayName;
    private final int maxHp;
    private final int damageOnWin;
    private final int rockWeight;
    private final int paperWeight;
    private final int scissorsWeight;

    RpsEnemyType(String displayName, int maxHp, int damageOnWin,
                 int rockWeight, int paperWeight, int scissorsWeight) {
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.damageOnWin = damageOnWin;
        this.rockWeight = rockWeight;
        this.paperWeight = paperWeight;
        this.scissorsWeight = scissorsWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getDamageOnWin() {
        return damageOnWin;
    }

    public RpsMove pickMove(Random random) {
        int total = rockWeight + paperWeight + scissorsWeight;
        int roll = random.nextInt(total);

        if (roll < rockWeight) return RpsMove.ROCK;
        roll -= rockWeight;

        if (roll < paperWeight) return RpsMove.PAPER;
        return RpsMove.SCISSORS;
    }

    public static RpsEnemyType byId(int id) {
        if (id < 0 || id >= values().length) return TRAINING_BOT;
        return values()[id];
    }

    public static RpsEnemyType random(Random random) {
        RpsEnemyType[] values = values();
        return values[random.nextInt(values.length)];
    }
}