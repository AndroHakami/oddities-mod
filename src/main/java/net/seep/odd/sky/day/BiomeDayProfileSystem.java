package net.seep.odd.sky.day;

public final class BiomeDayProfileSystem {
    private BiomeDayProfileSystem() {}

    private static boolean inited = false;

    public static void init() {
        if (inited) return;
        inited = true;

        BiomeDayProfileCommands.init();
        BiomeDayProfileNetworking.initServer();
    }
}