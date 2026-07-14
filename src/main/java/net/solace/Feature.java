package net.solace;

import java.util.EnumSet;

/**
 * The per-preset "dial" features layered on top of the always-on baseline safety.
 * Behaviour is wired up in M6 (comforts) and M7 (creative-lite); M4 only models them.
 */
public enum Feature {
    NO_HUNGER("no_hunger", EnumSet.of(Preset.COMFORT, Preset.CREATIVE_LITE)),
    NIGHT_VISION("night_vision", EnumSet.of(Preset.COMFORT, Preset.CREATIVE_LITE)),
    INFINITE_TORCHES("infinite_torches", EnumSet.of(Preset.COMFORT, Preset.CREATIVE_LITE)),
    FLIGHT("flight", EnumSet.of(Preset.CREATIVE_LITE)),
    REACH_BOOST("reach_boost", EnumSet.of(Preset.CREATIVE_LITE)),
    MINING_BOOST("mining_boost", EnumSet.of(Preset.CREATIVE_LITE)),
    INFINITE_BLOCKS("infinite_blocks", EnumSet.of(Preset.CREATIVE_LITE)),
    NO_DURABILITY("no_durability", EnumSet.of(Preset.CREATIVE_LITE));

    public final String id;
    private final EnumSet<Preset> defaultOn;

    Feature(String id, EnumSet<Preset> defaultOn) {
        this.id = id;
        this.defaultOn = defaultOn;
    }

    public boolean defaultFor(Preset preset) {
        return defaultOn.contains(preset);
    }

    public static Feature byIdOrNull(String id) {
        for (Feature f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }
}
