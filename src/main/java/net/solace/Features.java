package net.solace;

import net.solace.config.SolaceConfig;

/**
 * Resolves the effective value of a feature for a player:
 * server cap → per-player override → preset default.
 */
public final class Features {

    private Features() {
    }

    public static boolean isAllowed(Feature feature, SolaceConfig config) {
        return switch (feature) {
            case FLIGHT -> config.featureCaps.flight;
            case REACH_BOOST -> config.featureCaps.reachBoost;
            case MINING_BOOST -> config.featureCaps.miningBoost;
            case INFINITE_BLOCKS -> config.featureCaps.infiniteBasicBlocks;
            case NO_DURABILITY -> config.featureCaps.noDurability;
            default -> true;
        };
    }

    public static boolean effective(SolaceData data, Feature feature, SolaceConfig config) {
        if (!isAllowed(feature, config)) {
            return false;
        }
        Boolean override = data.overrides().get(feature);
        if (override != null) {
            return override;
        }
        return feature.defaultFor(data.preset());
    }
}
