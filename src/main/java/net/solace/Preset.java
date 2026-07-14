package net.solace;

import com.mojang.serialization.Codec;

/**
 * The "dial": how close a Solace player sits to creative.
 */
public enum Preset {
    SURVIVAL_PLUS("survival+"),
    COMFORT("comfort"),
    CREATIVE_LITE("creative-lite");

    public final String id;

    Preset(String id) {
        this.id = id;
    }

    public static final Codec<Preset> CODEC = Codec.STRING.xmap(Preset::byId, p -> p.id);

    /** Lenient: unknown ids fall back to COMFORT (used by the persistence codec). */
    public static Preset byId(String id) {
        Preset p = tryById(id);
        return p != null ? p : COMFORT;
    }

    /** Strict: returns null for unknown ids (used by commands for validation). */
    public static Preset tryById(String id) {
        for (Preset p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }
}
