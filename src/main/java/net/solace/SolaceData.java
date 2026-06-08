package net.solace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player Solace state, persisted with the world via the Fabric Attachment API.
 */
public record SolaceData(boolean enabled, Preset preset, Map<Feature, Boolean> overrides) {

    public static final Codec<SolaceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(SolaceData::enabled),
            Preset.CODEC.fieldOf("preset").forGetter(SolaceData::preset),
            Codec.unboundedMap(Feature.CODEC, Codec.BOOL)
                    .optionalFieldOf("overrides", Map.of())
                    .forGetter(SolaceData::overrides)
    ).apply(instance, SolaceData::new));

    public static final SolaceData DEFAULT = new SolaceData(false, Preset.COMFORT, Map.of());

    public SolaceData withEnabled(boolean value) {
        return new SolaceData(value, preset, overrides);
    }

    public SolaceData withPreset(Preset value) {
        return new SolaceData(enabled, value, overrides);
    }

    /** Set (value true/false) or clear (value null) a per-feature override. */
    public SolaceData withOverride(Feature feature, Boolean value) {
        Map<Feature, Boolean> next = new HashMap<>(overrides);
        if (value == null) {
            next.remove(feature);
        } else {
            next.put(feature, value);
        }
        return new SolaceData(enabled, preset, Map.copyOf(next));
    }

    /** True if the player has never configured Solace (used to apply the server default preset). */
    public boolean pristine() {
        return !enabled && preset == Preset.COMFORT && overrides.isEmpty();
    }
}
