package net.solace;

import net.solace.config.SolaceConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Effective feature resolution: server cap beats per-player override beats preset default.
 */
class FeaturesTest {

    private static SolaceData data(Preset preset, Map<Feature, Boolean> overrides) {
        return new SolaceData(true, preset, overrides, true);
    }

    @Test
    void presetDefaultsMatchTheDial() {
        SolaceConfig config = new SolaceConfig();
        // Survival+: baseline safety only.
        for (Feature feature : Feature.values()) {
            assertFalse(Features.effective(data(Preset.SURVIVAL_PLUS, Map.of()), feature, config),
                    feature + " should be off for survival+");
        }
        // Comfort: comforts on, creative-lite perks off.
        SolaceData comfort = data(Preset.COMFORT, Map.of());
        assertTrue(Features.effective(comfort, Feature.NO_HUNGER, config));
        assertTrue(Features.effective(comfort, Feature.NIGHT_VISION, config));
        assertTrue(Features.effective(comfort, Feature.INFINITE_TORCHES, config));
        assertFalse(Features.effective(comfort, Feature.FLIGHT, config));
        assertFalse(Features.effective(comfort, Feature.NO_DURABILITY, config));
        // Creative-lite: everything on.
        for (Feature feature : Feature.values()) {
            assertTrue(Features.effective(data(Preset.CREATIVE_LITE, Map.of()), feature, config),
                    feature + " should be on for creative-lite");
        }
    }

    @Test
    void overrideBeatsPresetDefault() {
        SolaceConfig config = new SolaceConfig();
        assertTrue(Features.effective(
                data(Preset.SURVIVAL_PLUS, Map.of(Feature.FLIGHT, true)), Feature.FLIGHT, config));
        assertFalse(Features.effective(
                data(Preset.CREATIVE_LITE, Map.of(Feature.FLIGHT, false)), Feature.FLIGHT, config));
    }

    @Test
    void serverCapBeatsOverride() {
        SolaceConfig config = new SolaceConfig();
        config.featureCaps.flight = false;
        assertFalse(Features.isAllowed(Feature.FLIGHT, config));
        assertFalse(Features.effective(
                data(Preset.CREATIVE_LITE, Map.of(Feature.FLIGHT, true)), Feature.FLIGHT, config));
    }

    @Test
    void everyCappedFeatureRespondsToItsCap() {
        SolaceConfig config = new SolaceConfig();
        config.featureCaps.flight = false;
        config.featureCaps.reachBoost = false;
        config.featureCaps.miningBoost = false;
        config.featureCaps.infiniteBasicBlocks = false;
        config.featureCaps.noDurability = false;
        assertFalse(Features.isAllowed(Feature.FLIGHT, config));
        assertFalse(Features.isAllowed(Feature.REACH_BOOST, config));
        assertFalse(Features.isAllowed(Feature.MINING_BOOST, config));
        assertFalse(Features.isAllowed(Feature.INFINITE_BLOCKS, config));
        assertFalse(Features.isAllowed(Feature.NO_DURABILITY, config));
        // Comforts have no cap and stay allowed.
        assertTrue(Features.isAllowed(Feature.NO_HUNGER, config));
        assertTrue(Features.isAllowed(Feature.NIGHT_VISION, config));
        assertTrue(Features.isAllowed(Feature.INFINITE_TORCHES, config));
    }
}
