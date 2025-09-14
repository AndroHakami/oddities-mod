package net.seep.odd.abilities.artificer;

public enum EssenceType {
    LIGHT ("light", 0xFFFFFFFF),  // plants
    GAIA  ("gaia",  0xFF46C046),  // ground
    HOT   ("hot",   0xFFFF8C2E),  // hot air
    COLD  ("cold",  0xFF7FDBFF),  // cold air
    DEATH ("death", 0xFF1A001F),  // undead
    LIFE  ("life",  0xFFFFE066);  // living (not undead)

    public final String key;
    public final int argb;
    EssenceType(String key, int argb) { this.key = key; this.argb = argb; }

    public static EssenceType byKey(String k) {
        for (var e : values()) if (e.key.equals(k)) return e;
        return null;
    }
}
