package net.solace;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence codec: round-trips, and stays loadable when saved data contains
 * ids from older or newer versions of the mod.
 */
class SolaceDataCodecTest {

    private static SolaceData decode(String json) {
        JsonElement element = JsonParser.parseString(json);
        return SolaceData.CODEC.parse(JsonOps.INSTANCE, element)
                .result().orElseThrow(() -> new AssertionError("decode failed: " + json));
    }

    private static JsonElement encode(SolaceData data) {
        return SolaceData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .result().orElseThrow(() -> new AssertionError("encode failed: " + data));
    }

    @Test
    void roundTripPreservesEverything() {
        SolaceData original = new SolaceData(true, Preset.CREATIVE_LITE,
                Map.of(Feature.FLIGHT, false, Feature.NO_HUNGER, true), true);
        SolaceData decoded = decode(encode(original).toString());
        assertEquals(original, decoded);
    }

    @Test
    void dataSavedBeforeConfiguredFlagStillWorks() {
        // Old saves have no "configured" field; the pristine heuristic must still hold.
        SolaceData untouched = decode("""
                {"enabled": false, "preset": "comfort", "overrides": {}}""");
        assertTrue(untouched.pristine());
        SolaceData used = decode("""
                {"enabled": true, "preset": "comfort", "overrides": {}}""");
        assertFalse(used.pristine());
    }

    @Test
    void unknownFeatureOverrideIsDroppedNotFatal() {
        // easier_taming existed in an early build; saved worlds may still carry it.
        SolaceData decoded = decode("""
                {"enabled": true, "preset": "comfort",
                 "overrides": {"easier_taming": true, "flight": true}}""");
        assertTrue(decoded.enabled());
        assertEquals(Map.of(Feature.FLIGHT, true), decoded.overrides());
    }

    @Test
    void unknownPresetFallsBackToComfort() {
        SolaceData decoded = decode("""
                {"enabled": false, "preset": "hardcore_plus", "overrides": {}}""");
        assertEquals(Preset.COMFORT, decoded.preset());
    }

    @Test
    void missingOverridesFieldDefaultsToEmpty() {
        SolaceData decoded = decode("""
                {"enabled": false, "preset": "survival+"}""");
        assertEquals(Preset.SURVIVAL_PLUS, decoded.preset());
        assertTrue(decoded.overrides().isEmpty());
    }

    @Test
    void encodeWritesStableStringIds() {
        JsonElement encoded = encode(new SolaceData(true, Preset.CREATIVE_LITE,
                Map.of(Feature.NO_DURABILITY, true), true));
        String json = encoded.toString();
        assertTrue(json.contains("\"creative-lite\""), json);
        assertTrue(json.contains("\"no_durability\""), json);
        assertFalse(json.contains("CREATIVE_LITE"), "enum names must not leak into saves: " + json);
    }
}
