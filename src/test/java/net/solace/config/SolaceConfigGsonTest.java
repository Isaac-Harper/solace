package net.solace.config;

import com.google.gson.Gson;
import net.solace.Preset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * config/solace.json parsing, including configs written by older versions.
 */
class SolaceConfigGsonTest {

    private static final Gson GSON = new Gson();

    @Test
    void legacyConfigWithRemovedFieldsStillLoads() {
        // infiniteBlockTag was removed; existing files still contain it.
        SolaceConfig config = GSON.fromJson("""
                {"defaultPreset": "creative-lite", "allowSelfService": false,
                 "infiniteBlockTag": "#solace:infinite",
                 "teleport": {"enabled": false, "cooldownSeconds": 5, "crossDimension": false}}""",
                SolaceConfig.class);
        assertEquals(Preset.CREATIVE_LITE, config.resolvedDefaultPreset());
        assertFalse(config.allowSelfService);
        assertFalse(config.teleport.enabled);
        assertEquals(5, config.teleport.cooldownSeconds);
    }

    @Test
    void defaultsMatchSpec() {
        SolaceConfig config = new SolaceConfig();
        assertEquals(Preset.COMFORT, config.resolvedDefaultPreset());
        assertTrue(config.allowSelfService);
        assertTrue(config.allowMultiplePlayers);
        assertFalse(config.pacifist);
        assertEquals("hostile_explosions", config.baseProtection.mode);
        assertEquals(64, config.baseProtection.homeRadius);
        assertEquals(30, config.teleport.cooldownSeconds);
        assertTrue(config.featureCaps.flight);
        assertNull(config.home);
    }

    @Test
    void baseProtectionModeResolvesLeniently() {
        SolaceConfig.BaseProtection protection = new SolaceConfig.BaseProtection();
        for (String mode : SolaceConfig.BaseProtection.MODES) {
            protection.mode = mode;
            assertEquals(mode, protection.resolvedMode());
        }
        // Hand-edited or stale values behave like the default instead of corrupting saves.
        protection.mode = "home-region";
        assertEquals(SolaceConfig.BaseProtection.HOSTILE_EXPLOSIONS, protection.resolvedMode());
        // Explicit null has always meant protection off; preserve that hand-edit behavior.
        protection.mode = null;
        assertEquals(SolaceConfig.BaseProtection.OFF, protection.resolvedMode());
    }

    @Test
    void explicitNullSectionsAreRestoredToDefaults() {
        // Gson writes an explicit JSON null over initialized fields; sanitized() must
        // restore them or the tick loop NPEs on config.featureCaps.
        SolaceConfig config = GSON.fromJson("""
                {"defaultPreset": null, "teleport": null, "baseProtection": null, "featureCaps": null}""",
                SolaceConfig.class).sanitized();
        assertEquals(Preset.COMFORT, config.resolvedDefaultPreset());
        assertTrue(config.teleport.enabled);
        assertEquals(SolaceConfig.BaseProtection.HOSTILE_EXPLOSIONS, config.baseProtection.resolvedMode());
        assertTrue(config.featureCaps.flight);
    }

    @Test
    void junkDefaultPresetFallsBackToComfort() {
        SolaceConfig config = GSON.fromJson("{\"defaultPreset\": \"anarchy\"}", SolaceConfig.class);
        assertEquals(Preset.COMFORT, config.resolvedDefaultPreset());
    }

    @Test
    void partialConfigKeepsDefaultsForMissingSections() {
        SolaceConfig config = GSON.fromJson("{\"pacifist\": true}", SolaceConfig.class);
        assertTrue(config.pacifist);
        assertTrue(config.teleport.enabled);
        assertEquals("hostile_explosions", config.baseProtection.mode);
    }
}
