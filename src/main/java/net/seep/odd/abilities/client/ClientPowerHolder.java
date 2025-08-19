package net.seep.odd.abilities.client;

public final class ClientPowerHolder {
    private static String powerId = "";
    public static void set(String id) { powerId = id == null ? "" : id; }
    public static String get() { return powerId; }
    public static boolean has() { return powerId != null && !powerId.isEmpty(); }
    private ClientPowerHolder() {}
}