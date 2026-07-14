package net.solace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolaceDataTest {

    @Test
    void withOverrideSetsAndClears() {
        SolaceData data = SolaceData.DEFAULT.withOverride(Feature.FLIGHT, true);
        assertEquals(Map.of(Feature.FLIGHT, true), data.overrides());
        data = data.withOverride(Feature.FLIGHT, null);
        assertTrue(data.overrides().isEmpty());
    }

    @Test
    void withersDoNotMutate() {
        SolaceData base = SolaceData.DEFAULT;
        base.withEnabled(true);
        base.withPreset(Preset.CREATIVE_LITE);
        base.withOverride(Feature.FLIGHT, true);
        assertEquals(SolaceData.DEFAULT, base);
    }

    @Test
    void pristineOnlyForTheUntouchedDefault() {
        assertTrue(SolaceData.DEFAULT.pristine());
        assertFalse(SolaceData.DEFAULT.withEnabled(true).pristine());
        assertFalse(SolaceData.DEFAULT.withPreset(Preset.SURVIVAL_PLUS).pristine());
        assertFalse(SolaceData.DEFAULT.withOverride(Feature.FLIGHT, false).pristine());
        // Toggled off again after configuring: no longer pristine, keeps the player's setup.
        assertFalse(SolaceData.DEFAULT.withPreset(Preset.CREATIVE_LITE).withEnabled(false).pristine());
    }

    @Test
    void noOpOverrideClearKeepsPristineStatus() {
        // Clearing an override that was never set changes nothing and must not
        // permanently block the server default preset from applying later.
        SolaceData cleared = SolaceData.DEFAULT.withOverride(Feature.FLIGHT, null);
        assertTrue(cleared.pristine());
        assertEquals(SolaceData.DEFAULT, cleared);
    }

    @Test
    void explicitlyChoosingTheDefaultPresetStillCountsAsConfigured() {
        // A player who picked comfort on purpose (matching the built-in default) and toggled
        // off must not have the server's default preset re-applied on the next enable.
        SolaceData chosen = SolaceData.DEFAULT.withEnabled(true).withPreset(Preset.COMFORT).withEnabled(false);
        assertFalse(chosen.pristine());
    }

    @Test
    void idLookupsAreStrictWhereCommandsNeedThem() {
        assertEquals(Feature.FLIGHT, Feature.byIdOrNull("flight"));
        assertNull(Feature.byIdOrNull("warp_drive"));
        assertEquals(Preset.SURVIVAL_PLUS, Preset.tryById("survival+"));
        assertNull(Preset.tryById("survival"));
        assertEquals(Preset.COMFORT, Preset.byId("survival"));
    }
}
