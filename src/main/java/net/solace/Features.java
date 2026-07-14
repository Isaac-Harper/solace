package net.solace;

import net.minecraft.world.entity.Entity;
import net.solace.config.SolaceConfig;

/**
 * Resolves the effective value of a feature for a player:
 * server cap → per-player override → preset default.
 */
public final class Features {

    private Features() {
    }

    /** True when the entity has Solace enabled and the feature is effectively on, in one attachment lookup. */
    public static boolean active(Entity entity, Feature feature) {
        return active(SolaceState.get(entity), feature, SolaceConfig.get());
    }

    /** As {@link #active(Entity, Feature)} for callers that already hold the data. */
    public static boolean active(SolaceData data, Feature feature, SolaceConfig config) {
        return data.enabled() && effective(data, feature, config);
    }

    /**
     * Exhaustive on purpose: adding a Feature without deciding its cap is a compile
     * error instead of silently resolving to "always allowed".
     */
    public static boolean isAllowed(Feature feature, SolaceConfig config) {
        return switch (feature) {
            case FLIGHT -> config.featureCaps.flight;
            case REACH_BOOST -> config.featureCaps.reachBoost;
            case MINING_BOOST -> config.featureCaps.miningBoost;
            case INFINITE_BLOCKS -> config.featureCaps.infiniteBasicBlocks;
            case NO_DURABILITY -> config.featureCaps.noDurability;
            case NO_HUNGER, NIGHT_VISION, INFINITE_TORCHES -> true;
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
