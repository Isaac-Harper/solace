package net.solace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
    public Home home = null;

    public static final class Teleport {
        public boolean enabled = true;
        public int cooldownSeconds = 30;
        public boolean crossDimension = true;
    }

    public static final class BaseProtection {
        public static final String HOSTILE_EXPLOSIONS = "hostile_explosions";
        public static final String ALL_EXPLOSIONS = "all_explosions";
        public static final String HOME_REGION = "home_region";
        public static final String OFF = "off";
        public static final String[] MODES = {HOSTILE_EXPLOSIONS, ALL_EXPLOSIONS, HOME_REGION, OFF};

        public String mode = HOSTILE_EXPLOSIONS;
        public int homeRadius = 64;

        /**
         * The configured mode. A null mode means protection off (long-standing hand-edit
         * behavior); unknown strings resolve to the default, matching how the explosion
         * mixin has always treated them.
         */
        public String resolvedMode() {
            if (mode == null) {
                return OFF;
            }
            for (String known : MODES) {
                if (known.equals(mode)) {
                    return known;
                }
            }
            return HOSTILE_EXPLOSIONS;
        }
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

        public static Home of(ServerPlayer player) {
            Home home = new Home();
            home.dimension = player.level().dimension().identifier().toString();
            home.x = player.getX();
            home.y = player.getY();
            home.z = player.getZ();
            return home;
        }

        public boolean matches(Level level) {
            return dimension != null && dimension.equals(level.dimension().identifier().toString());
        }

        /** The level this home lives in, or null if the dimension no longer exists. */
        public ServerLevel resolveLevel(MinecraftServer server) {
            if (dimension == null) {
                return null;
            }
            Identifier id = Identifier.tryParse(dimension);
            return (id == null) ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
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
                instance = (loaded != null) ? loaded.sanitized() : new SolaceConfig();
            } else {
                instance = new SolaceConfig();
                save();
            }
        } catch (IOException | RuntimeException e) {
            Solace.LOGGER.error("Failed to load {} config, using defaults: {}", Solace.MOD_ID, e.toString());
            instance = new SolaceConfig();
        }
    }

    /**
     * Gson writes an explicit JSON null straight over an initialized section field;
     * every consumer dereferences these without null checks, so restore defaults here.
     */
    public SolaceConfig sanitized() {
        // defaultPreset needs no restore: Preset.byId already maps null to COMFORT.
        if (teleport == null) {
            teleport = new Teleport();
        }
        if (baseProtection == null) {
            baseProtection = new BaseProtection();
        }
        if (featureCaps == null) {
            featureCaps = new FeatureCaps();
        }
        return this;
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException e) {
            Solace.LOGGER.error("Failed to save {} config: {}", Solace.MOD_ID, e.toString());
        }
    }
}
