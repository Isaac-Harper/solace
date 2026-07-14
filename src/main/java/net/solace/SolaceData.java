package net.solace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player Solace state, persisted with the world via the Fabric Attachment API.
 *
 * <p>{@code configured} flips to true on any explicit change (toggle, preset, override)
 * so the server default preset is only ever applied to players who never touched Solace,
 * even when their chosen state happens to equal the defaults.
 */
public record SolaceData(boolean enabled, Preset preset, Map<Feature, Boolean> overrides, boolean configured) {

    public SolaceData {
        overrides = Map.copyOf(overrides); // value semantics survive callers passing a mutable map
    }

    /** Lenient: overrides for unknown feature ids (e.g. features removed in an update) are dropped on load. */
    private static final Codec<Map<Feature, Boolean>> OVERRIDES_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.BOOL).xmap(
                    raw -> {
                        Map<Feature, Boolean> map = new HashMap<>();
                        raw.forEach((id, value) -> {
                            Feature feature = Feature.byIdOrNull(id);
                            if (feature != null) {
                                map.put(feature, value);
                            }
                        });
                        return Map.copyOf(map);
                    },
                    map -> {
                        Map<String, Boolean> raw = new HashMap<>();
                        map.forEach((feature, value) -> raw.put(feature.id, value));
                        return raw;
                    });

    public static final Codec<SolaceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(SolaceData::enabled),
            Preset.CODEC.fieldOf("preset").forGetter(SolaceData::preset),
            OVERRIDES_CODEC.optionalFieldOf("overrides", Map.of())
                    .forGetter(SolaceData::overrides),
            Codec.BOOL.optionalFieldOf("configured", false)
                    .forGetter(SolaceData::configured)
    ).apply(instance, SolaceData::new));

    public static final SolaceData DEFAULT = new SolaceData(false, Preset.COMFORT, Map.of(), false);

    public SolaceData withEnabled(boolean value) {
        return new SolaceData(value, preset, overrides, true);
    }

    public SolaceData withPreset(Preset value) {
        return new SolaceData(enabled, value, overrides, true);
    }

    /** Set (value true/false) or clear (value null) a per-feature override. */
    public SolaceData withOverride(Feature feature, Boolean value) {
        if (value == null && !overrides.containsKey(feature)) {
            return this; // no-op clear: do not mark a never-configured player as configured
        }
        Map<Feature, Boolean> next = new HashMap<>(overrides);
        if (value == null) {
            next.remove(feature);
        } else {
            next.put(feature, value);
        }
        return new SolaceData(enabled, preset, next, true);
    }

    /**
     * True if the player has never configured Solace (used to apply the server default
     * preset). The state heuristic remains for data saved before {@code configured} existed.
     */
    public boolean pristine() {
        return !configured && !enabled && preset == Preset.COMFORT && overrides.isEmpty();
    }
}
