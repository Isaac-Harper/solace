package net.solace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.solace.Preset;
import net.solace.Solace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side config, stored at {@code config/solace.json}. Plain fields (Gson).
 */
public final class SolaceConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SolaceConfig instance = new SolaceConfig();

    public String defaultPreset = "comfort";
    public boolean allowSelfService = true;
    public boolean allowMultiplePlayers = true;
    public boolean pacifist = false;
    public Teleport teleport = new Teleport();
    public BaseProtection baseProtection = new BaseProtection();
    public FeatureCaps featureCaps = new FeatureCaps();
    public String infiniteBlockTag = "#solace:infinite";
    public Home home = null;

    public static final class Teleport {
        public boolean enabled = true;
        public int cooldownSeconds = 30;
        public boolean crossDimension = true;
    }

    public static final class BaseProtection {
        // hostile_explosions | all_explosions | home_region | off
        public String mode = "hostile_explosions";
        public int homeRadius = 64;
    }

    public static final class FeatureCaps {
        public boolean flight = true;
        public boolean reachBoost = true;
        public boolean miningBoost = true;
        public boolean infiniteBasicBlocks = true;
        public boolean noDurability = true;
    }

    /** Shared "base" location used by baseProtection mode "home_region" (null until set via /solace home set). */
    public static final class Home {
        public String dimension;
        public double x;
        public double y;
        public double z;
    }

    public static SolaceConfig get() {
        return instance;
    }

    public Preset resolvedDefaultPreset() {
        return Preset.byId(defaultPreset);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(Solace.MOD_ID + ".json");
    }

    public static void load() {
        Path path = path();
        try {
            if (Files.exists(path)) {
                SolaceConfig loaded = GSON.fromJson(Files.readString(path), SolaceConfig.class);
                instance = (loaded != null) ? loaded : new SolaceConfig();
            } else {
                instance = new SolaceConfig();
                save();
            }
        } catch (IOException | RuntimeException e) {
            Solace.LOGGER.error("Failed to load {} config, using defaults: {}", Solace.MOD_ID, e.toString());
            instance = new SolaceConfig();
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException e) {
            Solace.LOGGER.error("Failed to save {} config: {}", Solace.MOD_ID, e.toString());
        }
    }
}
