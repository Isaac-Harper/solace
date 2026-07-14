package net.solace;

import net.fabricmc.api.ModInitializer;
import net.solace.command.SolaceCommands;
import net.solace.config.SolaceConfig;
import net.solace.event.ComfortTicker;
import net.solace.event.SafetyEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solace: a per-player middleground between Survival and Creative.
 *
 * <p>Common (main) entrypoint, loaded on both client and dedicated server.
 */
public class Solace implements ModInitializer {
    public static final String MOD_ID = "solace";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SolaceConfig.load();       // M4: load/generate config/solace.json
        SolaceState.init();        // M1: register the per-player attachment
        SafetyEvents.register();    // M2: damage immunity + can't-die backstop
        ComfortTicker.register();   // M6: no phantoms / no hunger / night vision
        SolaceCommands.register();  // M1/M4: /solace on|off|status|preset|set|reload
        LOGGER.info("Solace initialized: safe co-op survival for Minecraft 26.1.");
    }
}
